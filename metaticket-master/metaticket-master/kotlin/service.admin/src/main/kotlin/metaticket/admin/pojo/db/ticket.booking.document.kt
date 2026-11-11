package metaticket.admin.pojo.db

import metaticket.order.pojo.db.CustomFieldData
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

data class TicketBookingDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),
    val showId : String,
    val sessionId : String,

    // ticket info
    val ticketId : String,
    val capacityId : String? = null,
    val priceCat : String? = null,   // price category
    val sid: String,
    val quantity : Int,     // requested capacity

    // customer info
    val customerUserId : String? = null,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    // delivery ìnfo
    val deliveryName: String? = null,
    val deliveryPhone: String? = null,
    val provinceName: String? = null,
    val districtName: String? = null,
    val wardName: String? = null,
    val address: String? = null,
    // custom fields
    val customFields: List<CustomFieldData>? = null,

    // order info
    val promotionCode: String? = null,
    var reservationId: String? = null,
    var orderId: String? = null,
    var paymentId: String? = null,

    val version: Int = 1,
    val lastUpdated: Date = Date(),
    val createdAt: Date = Date(),
)