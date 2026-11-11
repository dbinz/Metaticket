package metaticket.admin.pojo.request

import metaticket.order.pojo.db.CustomFieldData
import metaticket.order.pojo.db.DeliveryInfo

data class BookingTicketDTO constructor(
    // ticket info
    val sid: String,
    val quantity : Int,
    val promotionCode: String? = null,

    // customer info
    val name: String,
    val phone: String,
    val email: String,
    val delivery: DeliveryInfo? = null,
    val customFields: List<CustomFieldData>? = null,
)

data class BookingTicketRequest constructor(
    val showId: String,
    val sessionId: String,
    val bookingTickets: List<BookingTicketDTO> = emptyList(),
)