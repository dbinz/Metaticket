package meta.news.services

import meta.news.client.WordPressClient
import meta.news.client.WpPost
import meta.news.client.WpSort
import meta.news.models.NewsCategoryItem
import meta.news.models.NewsDetail
import meta.news.models.NewsHighlightPage
import meta.news.models.NewsOffsetPage
import meta.news.models.NewsPageable
import meta.news.models.NewsPageableSort
import meta.news.models.NewsSearchPage
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class NewsService(
	private val wordPressClient: WordPressClient,
	private val newsMapper: NewsMapper,
) {

	/** Bundled CSS for headless article HTML (theme + active plugins). */
	@Cacheable(cacheNames = ["contentStyles"], key = "'bundle'")
	fun getContentStyles(): String {
		return wordPressClient.fetchContentStyles()
	}

	/** Script manifest JSON for headless article HTML (plugins + jQuery deps). */
	@Cacheable(cacheNames = ["contentScripts"], key = "'bundle'")
	fun getContentScriptsJson(): String {
		return wordPressClient.fetchContentScriptsJson()
	}

	@Cacheable(cacheNames = ["categories"])
	fun getNewsCategoryList(): List<NewsCategoryItem> {
		return wordPressClient.listCategories()
			.filter { it.slug != "uncategorized" }
			.map { newsMapper.toCategoryItem(it.id, it.slug, it.name) }
	}

	@Cacheable(
		cacheNames = ["articles"],
		key = "'list-' + #offset + '-' + #size + '-' + (#categoryCode ?: '') + '-' + (#sort ?: '') + '-' + (#excludeSlug ?: '')",
	)
	fun getNewsList(
		offset: Int,
		size: Int,
		categoryCode: String?,
		sort: String?,
		excludeSlug: String? = null,
	): NewsOffsetPage {
		val safeOffset = offset.coerceAtLeast(0)
		val safeSize = size.coerceIn(1, 50)
		val exclude = normalizeExclude(excludeSlug)
		if (isCategoryFilter(categoryCode)) {
			val category = resolveCategory(categoryCode)
				?: return emptyOffsetPage(safeOffset, safeSize)
			val sorted = ArticleSort.sortCategoryThenModified(
				wordPressClient.listAllPublished(category.id),
				category.slug,
			).filter { exclude == null || it.slug != exclude }
			return sliceOffset(sorted, safeOffset, safeSize)
		}
		val result = wordPressClient.listPostsOffset(
			offset = safeOffset,
			perPage = safeSize,
			sort = parseSort(sort),
		)
		val content = result.items
			.map(newsMapper::toNewsItem)
			.filter { exclude == null || it.slug != exclude }
		val last = safeOffset + result.items.size >= result.total
		return NewsOffsetPage(
			content = content,
			totalElements = if (exclude == null) result.total else (result.total - 1).coerceAtLeast(0),
			offset = safeOffset,
			size = safeSize,
			last = last || content.isEmpty(),
			empty = content.isEmpty(),
		)
	}

	/** Public article by slug. Side rails are separate list APIs (`related` / `all` / `featured`). */
	@Cacheable(cacheNames = ["article"], key = "#slug")
	fun getNewsBySlug(slug: String): NewsDetail {
		val post = wordPressClient.getPostBySlug(slug)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: $slug")
		val item = newsMapper.toNewsItem(post)
		return NewsDetail(
			id = item.id,
			thumbnail = item.thumbnail,
			createdAt = item.createdAt,
			displayDate = item.displayDate,
			title = item.title,
			summary = item.summary,
			slug = item.slug,
			categoryId = item.categoryId,
			categoryCode = item.categoryCode,
			categoryName = item.categoryName,
			content = newsMapper.rewriteContentUrls(post.content.rendered, post.slug),
			author = newsMapper.authorName(post),
			readTime = newsMapper.readTime(post.content.rendered),
			tags = newsMapper.tagNames(post),
			highlightNews = emptyList(),
			highlightEventIds = emptyList(),
			highlightEvents = emptyList(),
		)
	}

	/** Featured pins 1..3 only — hub hero / “Bài viết nổi bật”; empty slots are omitted. */
	@Cacheable(cacheNames = ["featured"], key = "'pins-' + (#excludeSlug ?: '')")
	fun getFeaturedNews(excludeSlug: String? = null): NewsHighlightPage {
		val exclude = normalizeExclude(excludeSlug)
		val pinned = wordPressClient.listAllPublished()
			.filter { ArticleSort.effectiveFeaturedOrder(it) != null }
			.sortedBy { ArticleSort.effectiveFeaturedOrder(it) }
			.filter { exclude == null || it.slug != exclude }
			.map(newsMapper::toNewsItem)
		return NewsHighlightPage(content = pinned, totalElements = pinned.size.toLong())
	}

	/**
	 * “Bài viết liên quan”: fill up to [size] (default 6).
	 * 1) Most shared tags, then 2) newest in the same category.
	 */
	@Cacheable(
		cacheNames = ["articles"],
		key = "'related-' + #slug + '-' + #size + '-' + (#excludeSlug ?: '')",
	)
	fun getRelatedNews(
		slug: String,
		size: Int = 6,
		excludeSlug: String? = null,
	): NewsHighlightPage {
		val post = wordPressClient.getPostBySlug(slug)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: $slug")
		val target = size.coerceIn(1, 20)
		val skip = linkedSetOf(wordPressClient.normalizeSlug(post.slug))
		normalizeExclude(excludeSlug)?.let { skip.add(it) }
		val sourceTags = tagIds(post)
		val categoryIds = post.categories.toSet()
		val others = wordPressClient.listAllPublished()
			.filter { it.slug !in skip }

		val picked = LinkedHashMap<Long, WpPost>()

		if (sourceTags.isNotEmpty()) {
			val tagged = others
				.map { candidate -> candidate to tagIds(candidate).intersect(sourceTags).size }
				.filter { it.second > 0 }
				.sortedWith(
					compareByDescending<Pair<WpPost, Int>> { it.second }
						.thenByDescending { publishStamp(it.first) },
				)
			for ((candidate, _) in tagged) {
				if (picked.size >= target) break
				picked.putIfAbsent(candidate.id, candidate)
			}
		}

		if (picked.size < target && categoryIds.isNotEmpty()) {
			val sameCategory = others
				.filter { candidate ->
					candidate.id !in picked && candidate.categories.any { it in categoryIds }
				}
				.sortedByDescending { publishStamp(it) }
			for (candidate in sameCategory) {
				if (picked.size >= target) break
				picked[candidate.id] = candidate
			}
		}

		if (picked.isEmpty()) {
			return NewsHighlightPage(content = emptyList(), totalElements = 0)
		}
		val related = picked.values.take(target).map(newsMapper::toNewsItem)
		return NewsHighlightPage(content = related, totalElements = related.size.toLong())
	}

	@Cacheable(
		cacheNames = ["articles"],
		key = "'search-' + #keyword + '-' + #page + '-' + #size + '-' + (#excludeSlug ?: '')",
	)
	fun searchNews(
		keyword: String,
		page: Int,
		size: Int,
		excludeSlug: String? = null,
	): NewsSearchPage {
		val q = keyword.trim()
		if (q.isEmpty()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "keyword is required")
		}
		val safePage = page.coerceAtLeast(0)
		val safeSize = size.coerceIn(1, 50)
		val result = wordPressClient.listPosts(
			page = safePage + 1,
			perPage = safeSize,
			search = q,
			sort = WpSort("date", "desc"),
		)
		val exclude = normalizeExclude(excludeSlug)
		val content = result.items
			.map(newsMapper::toNewsItem)
			.filter { exclude == null || it.slug != exclude }
		val total = result.total
		val totalPages = if (safeSize == 0) 0 else ((total + safeSize - 1) / safeSize).toInt()
		val sorted = content.isNotEmpty()
		return NewsSearchPage(
			content = content,
			pageable = NewsPageable(
				pageNumber = safePage,
				pageSize = safeSize,
				offset = safePage.toLong() * safeSize,
				sort = NewsPageableSort(
					empty = !sorted,
					sorted = sorted,
					unsorted = !sorted,
				),
			),
			totalPages = totalPages,
			totalElements = total,
			size = safeSize,
			number = safePage,
			first = safePage == 0,
			last = safePage + 1 >= totalPages || content.isEmpty(),
			empty = content.isEmpty(),
		)
	}

	private data class CategoryRef(val id: Long, val slug: String)

	/** Blank-safe WP slug used to drop the open article from list APIs. */
	private fun normalizeExclude(excludeSlug: String?): String? {
		val raw = excludeSlug?.trim().orEmpty()
		if (raw.isEmpty()) return null
		return wordPressClient.normalizeSlug(raw)
	}

	/** Tag ids from REST `tags` plus embedded `post_tag` terms. */
	private fun tagIds(post: WpPost): Set<Long> {
		val fromField = post.tags.filter { it > 0 }.toSet()
		val fromEmbed = post.embedded?.terms
			?.flatten()
			?.filter { it.taxonomy == "post_tag" && it.id > 0 }
			?.map { it.id }
			?.toSet()
			.orEmpty()
		return fromField + fromEmbed
	}

	/** Publish timestamp for “newest first”; GMT when WP sends it. */
	private fun publishStamp(post: WpPost): String {
		return post.dateGmt.trim().ifBlank { post.date.trim() }
	}

	/** True when the list should be ranked inside one WP category. */
	private fun isCategoryFilter(categoryCode: String?): Boolean {
		return !categoryCode.isNullOrBlank() && !categoryCode.equals("all", ignoreCase = true)
	}

	/** Resolve WP id+slug; null if the code is unknown. */
	private fun resolveCategory(categoryCode: String?): CategoryRef? {
		val code = categoryCode?.trim().orEmpty()
		if (code.isEmpty()) return null
		val cats = wordPressClient.listCategories()
		val match = if (code.all { it.isDigit() }) {
			cats.firstOrNull { it.id == code.toLong() }
		} else {
			val slug = code.removePrefix("cat-")
			cats.firstOrNull { it.slug == slug || it.slug == code }
		} ?: return null
		return CategoryRef(id = match.id, slug = match.slug)
	}

	/** Empty page for an unknown categoryCode. */
	private fun emptyOffsetPage(offset: Int, size: Int): NewsOffsetPage {
		return NewsOffsetPage(
			content = emptyList(),
			totalElements = 0,
			offset = offset,
			size = size,
			last = true,
			empty = true,
		)
	}

	/** In-memory offset page after pin ranking. */
	private fun sliceOffset(posts: List<WpPost>, offset: Int, size: Int): NewsOffsetPage {
		val total = posts.size.toLong()
		val slice = if (offset >= posts.size) emptyList() else posts.drop(offset).take(size)
		val content = slice.map(newsMapper::toNewsItem)
		return NewsOffsetPage(
			content = content,
			totalElements = total,
			offset = offset,
			size = size,
			last = offset + content.size >= total || content.isEmpty(),
			empty = content.isEmpty(),
		)
	}

	/** Parse FE sort like `createdAt,desc` into WP orderby/order. */
	private fun parseSort(sort: String?): WpSort {
		if (sort.isNullOrBlank()) return WpSort("date", "desc")
		val parts = sort.split(",")
		val field = parts.getOrNull(0)?.trim()?.lowercase().orEmpty()
		val direction = parts.getOrNull(1)?.trim()?.lowercase().orEmpty().ifBlank { "desc" }
		val order = if (direction == "asc") "asc" else "desc"
		val orderBy = when (field) {
			"createdat", "date", "publishedat" -> "date"
			"modifiedat", "modified" -> "modified"
			"title" -> "title"
			"relevance" -> "relevance"
			else -> "date"
		}
		return WpSort(orderBy, order)
	}
}
