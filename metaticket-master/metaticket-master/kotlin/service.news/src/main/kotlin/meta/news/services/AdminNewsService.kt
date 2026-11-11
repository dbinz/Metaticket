package meta.news.services

import meta.news.client.WordPressClient
import meta.news.client.WpPostWrite
import meta.news.models.AdminArticleForm
import meta.news.models.AdminArticleRow
import meta.news.models.AdminCategoryOption
import meta.news.models.PageResponse
import meta.news.models.SaveArticleRequest
import org.springframework.cache.annotation.CacheEvict
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AdminNewsService(
	private val wordPressClient: WordPressClient,
	private val adminNewsMapper: AdminNewsMapper,
) {

	fun listArticles(
		page: Int,
		size: Int,
		search: String?,
		category: String?,
	): PageResponse<AdminArticleRow> {
		val categoryId = resolveCategoryId(category)
		val result = wordPressClient.listPostsAdmin(
			page = page + 1,
			perPage = size,
			search = search,
			categoryId = categoryId,
		)
		val sorted = ArticleSort.sort(result.items)
		return PageResponse(
			content = sorted.map(adminNewsMapper::toRow),
			page = page,
			size = size,
			totalElements = result.total,
			totalPages = result.totalPages,
		)
	}

	fun getArticle(id: Long): AdminArticleForm {
		val post = wordPressClient.getPostById(id)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: $id")
		return adminNewsMapper.toForm(post)
	}

	@CacheEvict(cacheNames = ["articles", "article", "categories", "featured"], allEntries = true)
	fun createArticle(request: SaveArticleRequest): AdminArticleForm {
		val post = wordPressClient.createPost(adminNewsMapper.toWrite(request))
		return adminNewsMapper.toForm(post)
	}

	@CacheEvict(cacheNames = ["articles", "article", "categories", "featured"], allEntries = true)
	fun updateArticle(id: Long, request: SaveArticleRequest): AdminArticleForm {
		val existing = wordPressClient.getPostById(id)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: $id")
		val post = wordPressClient.updatePost(
			id,
			adminNewsMapper.toWrite(request).copy(
				pinOrder = request.pinOrder?.takeIf { it in 1..NewsPins.FEATURED_MAX }
					?: ArticleSort.effectiveFeaturedOrder(existing),
				categoryPins = existing.categoryPins.takeIf { it.isNotEmpty() },
				featuredMedia = existing.featuredMedia.takeIf { it > 0 },
			),
		)
		return adminNewsMapper.toForm(post)
	}

	@CacheEvict(cacheNames = ["articles", "article", "categories", "featured"], allEntries = true)
	fun setVisibility(id: Long, visible: Boolean): AdminArticleRow {
		val existing = wordPressClient.getPostById(id)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: $id")
		val post = wordPressClient.updatePost(
			id,
			WpPostWrite(
				title = stripHtml(existing.title.rendered),
				content = existing.content.rendered,
				excerpt = existing.excerpt.rendered,
				status = if (visible) "publish" else "draft",
				slug = existing.slug,
				categories = existing.categories,
				// Preserve featured image — default 0 previously cleared WP _thumbnail_id.
				featuredMedia = existing.featuredMedia.takeIf { it > 0 },
				pinOrder = ArticleSort.effectiveFeaturedOrder(existing),
				categoryPins = existing.categoryPins.takeIf { it.isNotEmpty() },
			),
		)
		return adminNewsMapper.toRow(post)
	}

	@CacheEvict(cacheNames = ["articles", "article", "categories", "featured"], allEntries = true)
	fun setPin(id: Long, pinOrder: Int?): AdminArticleRow {
		val existing = wordPressClient.getPostById(id)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: $id")
		val post = wordPressClient.updatePost(
			id,
			WpPostWrite(
				title = stripHtml(existing.title.rendered),
				content = existing.content.rendered,
				excerpt = existing.excerpt.rendered,
				status = existing.status,
				slug = existing.slug,
				categories = existing.categories,
				featuredMedia = existing.featuredMedia.takeIf { it > 0 },
				pinOrder = pinOrder?.takeIf { it in 1..NewsPins.FEATURED_MAX },
				categoryPins = existing.categoryPins.takeIf { it.isNotEmpty() },
			),
		)
		return adminNewsMapper.toRow(post)
	}

	fun listCategories(): List<AdminCategoryOption> {
		return wordPressClient.listCategoriesAdmin().map {
			AdminCategoryOption(id = it.id, name = it.name, slug = it.slug)
		}
	}

	private fun resolveCategoryId(category: String?): Long? {
		if (category.isNullOrBlank()) return null
		if (category.all { it.isDigit() }) {
			return category.toLong()
		}
		return wordPressClient.listCategoriesAdmin().firstOrNull { it.slug == category }?.id
	}

	private fun stripHtml(html: String): String {
		if (html.isBlank()) return ""
		return html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
	}
}
