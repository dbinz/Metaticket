package meta.news.services

import meta.news.client.WpPost

const val FEATURED_PIN_MAX = 6
const val CATEGORY_PIN_MAX = 3

object NewsPins {
	const val FEATURED_MAX = FEATURED_PIN_MAX
	const val CATEGORY_MAX = CATEGORY_PIN_MAX
}

object ArticleSort {
	/** Featured hub/homepage rank 1..3; values outside the board are ignored. */
	fun effectiveFeaturedOrder(post: WpPost): Int? {
		val value = post.pinOrder ?: return null
		return value.takeIf { it in 1..FEATURED_PIN_MAX }
	}

	/** Rank 1..3 inside [categorySlug]; other categories on the same post are ignored. */
	fun categoryOrder(post: WpPost, categorySlug: String): Int? {
		if (categorySlug.isBlank()) return null
		val value = post.categoryPins[categorySlug] ?: return null
		return value.takeIf { it in 1..CATEGORY_PIN_MAX }
	}

	@Deprecated("Use effectiveFeaturedOrder", ReplaceWith("effectiveFeaturedOrder(post)"))
	fun effectivePinOrder(post: WpPost): Int? = effectiveFeaturedOrder(post)

	fun compareFeaturedThenModified(): Comparator<WpPost> {
		return compareBy<WpPost> { effectiveFeaturedOrder(it) ?: Int.MAX_VALUE }
			.thenByDescending { it.modified }
	}

	fun compareCategoryThenModified(categorySlug: String): Comparator<WpPost> {
		return compareBy<WpPost> { categoryOrder(it, categorySlug) ?: Int.MAX_VALUE }
			.thenByDescending { it.modified }
	}

	fun sortFeaturedThenModified(posts: List<WpPost>): List<WpPost> =
		posts.sortedWith(compareFeaturedThenModified())

	fun sortCategoryThenModified(posts: List<WpPost>, categorySlug: String): List<WpPost> =
		posts.sortedWith(compareCategoryThenModified(categorySlug))

	fun sort(posts: List<WpPost>): List<WpPost> = sortFeaturedThenModified(posts)
}
