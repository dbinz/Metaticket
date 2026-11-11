package meta.news.models

data class AdminArticleRow(
	val id: Long,
	val title: String,
	val slug: String,
	val visible: Boolean,
	val pinOrder: Int?,
	val categories: List<String>,
	val modifiedAt: String,
)

data class AdminArticleForm(
	val id: Long,
	val title: String,
	val slug: String,
	val excerpt: String,
	val contentHtml: String,
	val visible: Boolean,
	val pinOrder: Int?,
	val categoryIds: List<Long>,
)

data class AdminCategoryOption(
	val id: Long,
	val name: String,
	val slug: String = "",
)

data class SaveArticleRequest(
	val title: String,
	val slug: String,
	val excerpt: String = "",
	val contentHtml: String = "",
	val visible: Boolean = false,
	val pinOrder: Int? = null,
	val categoryIds: List<Long> = emptyList(),
)

data class SetVisibilityRequest(
	val visible: Boolean,
)

data class SetPinRequest(
	val pinOrder: Int?,
)

data class NewsPinSummary(
	val id: Long,
	val title: String,
	val slug: String,
	val status: String,
	val thumbnailUrl: String,
	val modifiedAt: String,
)

data class NewsPinSlot(
	val pinOrder: Int,
	val article: NewsPinSummary?,
)

data class NewsPinBoard(
	val slots: List<NewsPinSlot>,
	val maxSlots: Int = 3,
)

data class NewsPinSlotInput(
	val pinOrder: Int,
	val articleId: Long? = null,
)

data class ConfirmNewsPinsRequest(
	val slots: List<NewsPinSlotInput> = emptyList(),
)

data class NewsPinCandidatePage(
	val content: List<NewsPinSummary>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
)

/** Admin list pagination wrapper (portal private API). */
data class PageResponse<T>(
	val content: List<T>,
	val page: Int,
	val size: Int,
	val totalElements: Long,
	val totalPages: Int,
)
