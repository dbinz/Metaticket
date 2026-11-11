package metaticket.show.pojo

import metaticket.show.pojo.db.ShowDocument

const val BANNER_PIN_SLOT_COUNT = 10

/** Compact show row for the banner pin table / picker. */
data class BannerPinShowSummary(
    val id: String,
    val showName: String,
    val code: String,
    val eventCategory: String,
    val eventCategoryCode: String,
    val thumbnailUrl: String,
    val bannerImageUrl: String,
    val pinOrder: Int?,
)

/** One of 10 fixed banner slots; [show] is null when the slot is vacant. */
data class BannerPinSlotResponse(
    val pinOrder: Int,
    val show: BannerPinShowSummary?,
)

data class BannerPinBoardResponse(
    val slots: List<BannerPinSlotResponse>,
)

data class BannerPinCandidatePage(
    val content: List<BannerPinShowSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

/** Maps a show document onto the pin-board card. */
fun ShowDocument.toBannerPinSummary(): BannerPinShowSummary = BannerPinShowSummary(
    id = id,
    showName = showName,
    code = code,
    eventCategory = eventCategory ?: "",
    eventCategoryCode = eventCategoryCode ?: "",
    thumbnailUrl = thumbnailUrl ?: "",
    bannerImageUrl = bannerImageUrl ?: "",
    pinOrder = pinOrder,
)
