package metaticket.order.datastructure.response

import metaticket.order.datastructure.OrderStatus
import metaticket.ticket.datastructure.SeatType


/**
 *
 * @author dongcatchay
 */

data class OrderResponse constructor(
    var id: String,
    var reservationId: String,
    var paymentId: String? = null,
    var showId: String,
    val sessionId: String,
    var code: String,
    var status: OrderStatus,
    var customerName: String,
    var customerPhone: String,
    var customerEmail: String,
    var delivery: DeliveryInfoResponse? = null,
    var customFields: List<CustomFieldDataResponse>? = null,
    val promotionDetails: List<PromotionResponse> = emptyList(),
    var tickets: List<TicketInfoResponse> = emptyList(),
    var total: Long,
    var createdAt: Long,
    /** Ngày tham quan của đơn vé theo lịch; bỏ khỏi JSON khi null để client cũ không gặp field lạ. */
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val visitDate: String? = null,
)

data class PromotionResponse constructor(val name: String, val priceDiscount: Long)

data class DeliveryInfoResponse constructor(
    var deliveryName: String,
    var deliveryPhone: String,
    var provinceName: String,
    var districtName: String,
    var wardName: String,
    var address: String,
)

data class TicketInfoResponse constructor(
    var id: String,
    var dName: String?,
    var sid: String,
    var seatType: SeatType,
    var priceCat : String,      // price category
    var quantity : Int,     // requested capacity
    var basePrice : Long,
)

data class CustomFieldDataResponse constructor(
    val id: String,
    val name: String,
    val value: String?,
)

data class InvoiceStatisticsByShow constructor(
    val showId: String,
    var totalTicket: Int,
    var totalPrice: Long,
    var discount: Double,
    var revenue: Double,
    val bySession: List<InvoiceStatisticsBySession>
)

data class InvoiceStatisticsBySession constructor(
    val sessionId: String,
    var totalTicket: Int,
    var totalPrice: Long,
    var discount: Long,
    var commission: Float,
    var revenue: Double,
    val byCategory: List<InvoiceStatisticsByCategory>
)

data class InvoiceStatisticsByCategory constructor(
    val cat: String,
    var basePrice: Long,
    var totalTicket: Int,
    var totalPrice: Long,
)