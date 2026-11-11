package metaticket.order.pojo.request

data class ReservationIdOnlyRequest constructor(
    var reservationId: String,
)

data class LoadOrderByReservationRequest constructor(
    var showId: String,
    val sessionId: String,
    var reservationIds: List<String>
)

data class OrderIdOnlyRequest constructor(
    var orderId: String,
)

data class CalculateTentativePriceRequest constructor(
    val tickets: List<TentativePriceTicketRequest>,
    val reservationId: String?,
    val showId: String,
    val sessionId: String,
    val promotionCode: String?
)

data class TentativePriceTicketRequest constructor(
    var ticketId : String,
    var quantity : Int,     // requested capacity
    var priceCat : String   // price category
)

data class CreatePromotionRequest constructor(
    var showId : String,
    var sessionId : String,
    var pluginName : String,
    var configuration : String
)

data class LoadCustomFieldRequest constructor(
    val showId: String,
    val sessionId: String
)

data class LoadOrderByShowAndSessionRequest constructor(
    val showId: String,
    val sessionId: String? = null
)
