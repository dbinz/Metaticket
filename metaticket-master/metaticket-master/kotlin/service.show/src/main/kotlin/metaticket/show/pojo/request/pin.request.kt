package metaticket.show.pojo.request

/** One banner slot after confirm: [pinOrder] is 1..10; blank [showId] clears the slot. */
data class BannerPinSlotInput(
    val pinOrder: Int,
    val showId: String? = null,
)

/** Apply the 10-row pin board from the admin page. */
data class ConfirmBannerPinsRequest(
    val slots: List<BannerPinSlotInput>,
)
