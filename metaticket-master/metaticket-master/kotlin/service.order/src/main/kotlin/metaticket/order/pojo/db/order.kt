package metaticket.order.pojo.db

import metaticket.order.datastructure.OrderStatus
import metaticket.ticket.datastructure.SeatType
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

data class OrderDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    var reservationId: String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    var paymentId: String? = null,

    @BsonRepresentation(BsonType.OBJECT_ID)
    var showId: String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,

    var code: String,

    var status: OrderStatus = OrderStatus.Created,

    var tickets: List<TicketInfo> = emptyList(),

    val promotionDetails: List<OrderPromotionDetail> = emptyList(),

    var delivery: DeliveryInfo? = null,

    var customerUserId: String? = null,

    var customFields: List<CustomFieldData>? = emptyList(),

    var customerName: String,
    var customerPhone: String,
    var customerEmail: String,

    var total: Long = 0,
    var promotionPluginConfigId: String?,

    /** Ngày tham quan "yyyy-MM-dd" (giờ VN) của đơn vé theo lịch; null với đơn suất thường. */
    val visitDate: String? = null,

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)

data class OrderPromotionDetail constructor(
    val name: String,
    val priceDiscount: Long,
    val promotionCodeQuantityId: String?,
    val promotionComboQuantityId: String?,
    val promotionComboQuantity: Int?
)

data class TicketInfo constructor(
    @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String,

    var sid: String,                // seat id
    var seatType: SeatType,         // seat type
    var priceCat : String,          // price category
    var quantity : Int,             // requested capacity
    var basePrice : Long,           // base price per ticket slot
    var ticketSlots: Int? = null,   // number of slots per ticket, default is 1 if not configured
    var dname: String? = null,      // display name
)
