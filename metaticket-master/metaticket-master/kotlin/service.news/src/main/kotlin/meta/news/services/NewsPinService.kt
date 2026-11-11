package meta.news.services

import meta.news.client.WordPressClient
import meta.news.client.WpPost
import meta.news.models.ConfirmNewsPinsRequest
import meta.news.models.NewsPinBoard
import meta.news.models.NewsPinCandidatePage
import meta.news.models.NewsPinSlot
import meta.news.models.NewsPinSlotInput
import org.springframework.cache.annotation.CacheEvict
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class NewsPinService(
	private val wordPressClient: WordPressClient,
	private val newsMapper: NewsMapper,
) {

	/** Occupied featured ranks 1..3 (vacant slots omitted). */
	fun getFeaturedBoard(): NewsPinBoard {
		val slots = wordPressClient.listAllAdmin()
			.mapNotNull { post ->
				val order = ArticleSort.effectiveFeaturedOrder(post) ?: return@mapNotNull null
				NewsPinSlot(pinOrder = order, article = newsMapper.toPinSummary(post))
			}
			.sortedBy { it.pinOrder }
		return NewsPinBoard(slots = slots, maxSlots = NewsPins.FEATURED_MAX)
	}

	/** Occupied ranks 1..3 for one category slug. */
	fun getCategoryBoard(categoryCode: String): NewsPinBoard {
		val slug = resolveCategorySlug(categoryCode)
		val slots = wordPressClient.listAllAdmin()
			.mapNotNull { post ->
				val order = ArticleSort.categoryOrder(post, slug) ?: return@mapNotNull null
				NewsPinSlot(pinOrder = order, article = newsMapper.toPinSummary(post))
			}
			.sortedBy { it.pinOrder }
		return NewsPinBoard(slots = slots, maxSlots = NewsPins.CATEGORY_MAX)
	}

	@CacheEvict(cacheNames = ["articles", "article", "featured"], allEntries = true)
	fun refreshFeaturedBoard(): NewsPinBoard {
		val newest = wordPressClient.listAllPublished().take(NewsPins.FEATURED_MAX)
		return applyFeaturedPins(newest.map { it.id })
	}

	@CacheEvict(cacheNames = ["articles", "article", "featured"], allEntries = true)
	fun refreshCategoryBoard(categoryCode: String): NewsPinBoard {
		val slug = resolveCategorySlug(categoryCode)
		val categoryId = resolveCategoryId(slug)
		val newest = wordPressClient.listAllPublished(categoryId).take(NewsPins.CATEGORY_MAX)
		return applyCategoryPins(slug, newest.map { it.id })
	}

	@CacheEvict(cacheNames = ["articles", "article", "featured"], allEntries = true)
	fun confirmFeaturedBoard(request: ConfirmNewsPinsRequest): NewsPinBoard {
		return applyFeaturedPins(orderedArticleIds(request.slots, NewsPins.FEATURED_MAX))
	}

	@CacheEvict(cacheNames = ["articles", "article", "featured"], allEntries = true)
	fun confirmCategoryBoard(categoryCode: String, request: ConfirmNewsPinsRequest): NewsPinBoard {
		val slug = resolveCategorySlug(categoryCode)
		return applyCategoryPins(slug, orderedArticleIds(request.slots, NewsPins.CATEGORY_MAX))
	}

	/** Paginated picker; [page] is 1-based to match accessory pin UI. */
	fun searchCandidates(page: Int, size: Int, keyword: String?, categoryCode: String?): NewsPinCandidatePage {
		val safePage = page.coerceAtLeast(1)
		val safeSize = size.coerceIn(1, 50)
		val categoryId = categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { resolveCategoryId(it) }
		val result = wordPressClient.listPostsAdmin(
			page = safePage,
			perPage = safeSize,
			search = keyword,
			categoryId = categoryId,
		)
		return NewsPinCandidatePage(
			content = result.items.map(newsMapper::toPinSummary),
			page = safePage,
			size = safeSize,
			totalElements = result.total,
			totalPages = result.totalPages,
		)
	}

	/** Persist compacted featured ranks; unsets `mt_pin_order` on posts that left the board. */
	private fun applyFeaturedPins(orderedIds: List<Long>): NewsPinBoard {
		val previous = wordPressClient.listAllAdmin().filter { ArticleSort.effectiveFeaturedOrder(it) != null }
		for (post in previous) {
			if (post.id !in orderedIds) {
				wordPressClient.updateFeaturedPin(post.id, null)
			}
		}
		orderedIds.forEachIndexed { index, id ->
			requirePost(id)
			wordPressClient.updateFeaturedPin(id, index + 1)
		}
		return getFeaturedBoard()
	}

	/** Merge only [slug] on each post; other category ranks stay. */
	private fun applyCategoryPins(slug: String, orderedIds: List<Long>): NewsPinBoard {
		val previous = wordPressClient.listAllAdmin().filter { ArticleSort.categoryOrder(it, slug) != null }
		for (post in previous) {
			if (post.id !in orderedIds) {
				val next = post.categoryPins.toMutableMap()
				next.remove(slug)
				wordPressClient.updateCategoryPins(post.id, next)
			}
		}
		orderedIds.forEachIndexed { index, id ->
			val post = requirePost(id)
			val next = post.categoryPins.toMutableMap()
			next[slug] = index + 1
			wordPressClient.updateCategoryPins(id, next)
		}
		return getCategoryBoard(slug)
	}

	private fun orderedArticleIds(slots: List<NewsPinSlotInput>, max: Int): List<Long> {
		if (slots.size > max) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At most $max pinned articles")
		}
		val ids = mutableListOf<Long>()
		val seen = mutableSetOf<Long>()
		for (slot in slots.sortedBy { it.pinOrder }) {
			val id = slot.articleId ?: continue
			if (id <= 0) {
				throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed articleId")
			}
			if (!seen.add(id)) {
				throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate article on the pin board")
			}
			ids += id
		}
		if (ids.size > max) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At most $max pinned articles")
		}
		return ids
	}

	private fun requirePost(id: Long): WpPost {
		return wordPressClient.getPostById(id)
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Article not found: $id")
	}

	private fun resolveCategorySlug(categoryCode: String): String {
		val code = categoryCode.trim()
		if (code.isEmpty()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryCode is required")
		}
		val cats = wordPressClient.listCategoriesAdmin().filter { it.slug != "uncategorized" }
		val match = if (code.all { it.isDigit() }) {
			cats.firstOrNull { it.id == code.toLong() }
		} else {
			cats.firstOrNull { it.slug == code || it.slug == code.removePrefix("cat-") }
		} ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category: $code")
		return match.slug
	}

	private fun resolveCategoryId(categoryCode: String): Long {
		val code = categoryCode.trim()
		val cats = wordPressClient.listCategoriesAdmin()
		if (code.all { it.isDigit() }) {
			return code.toLong()
		}
		return cats.firstOrNull { it.slug == code || it.slug == code.removePrefix("cat-") }?.id
			?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category: $code")
	}
}
