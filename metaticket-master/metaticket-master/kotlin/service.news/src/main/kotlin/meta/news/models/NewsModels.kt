package meta.news.models

/**
 * Public customer-facing DTOs — aligned with MetaTicket-Home News_API_Datatypes_Samples.
 */

data class NewsCategoryItem(
	val id: String,
	val name: String,
	val code: String,
	val slug: String,
)

data class NewsItem(
	val id: String,
	val thumbnail: String,
	val createdAt: String,
	val displayDate: String,
	val title: String,
	val summary: String,
	val slug: String,
	val categoryId: String,
	val categoryCode: String,
	val categoryName: String,
	val author: String = "",
)

data class NewsDetail(
	val id: String,
	val thumbnail: String,
	val createdAt: String,
	val displayDate: String,
	val title: String,
	val summary: String,
	val slug: String,
	val categoryId: String = "",
	val categoryCode: String = "",
	val categoryName: String = "",
	val content: String,
	val author: String,
	val readTime: String,
	val tags: List<String> = emptyList(),
	val highlightNews: List<NewsItem> = emptyList(),
	val highlightEventIds: List<String> = emptyList(),
	val highlightEvents: List<Any> = emptyList(),
)

/** Offset-based list page used by getNewsList. */
data class NewsOffsetPage(
	val content: List<NewsItem>,
	val totalElements: Long,
	val offset: Int,
	val size: Int,
	val last: Boolean,
	val empty: Boolean,
)

/** Compact page used by featured / related list APIs. */
data class NewsHighlightPage(
	val content: List<NewsItem>,
	val totalElements: Long,
)

data class NewsPageableSort(
	val empty: Boolean,
	val sorted: Boolean,
	val unsorted: Boolean,
)

data class NewsPageable(
	val pageNumber: Int,
	val pageSize: Int,
	val offset: Long,
	val paged: Boolean = true,
	val unpaged: Boolean = false,
	val sort: NewsPageableSort,
)

/** Spring-style page used by searchNews. */
data class NewsSearchPage(
	val content: List<NewsItem>,
	val pageable: NewsPageable,
	val totalPages: Int,
	val totalElements: Long,
	val size: Int,
	val number: Int,
	val first: Boolean,
	val last: Boolean,
	val empty: Boolean,
)
