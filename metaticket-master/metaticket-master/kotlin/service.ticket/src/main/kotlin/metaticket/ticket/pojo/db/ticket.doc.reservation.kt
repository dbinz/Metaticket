package metaticket.ticket.pojo.db

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import metaticket.ticket.datastructure.TicketReservationStatus
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*


/**
 * @author dongcatchay
 */

// Reservations are also stored as JSON (Redis, JobRunr job payloads). Ignoring unknown fields lets a
// rolled-back build read documents written by a newer one.
@JsonIgnoreProperties(ignoreUnknown = true)
data class TicketReservationDetailsDocument constructor(
    @BsonRepresentation(BsonType.OBJECT_ID)
    val ticketId : String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    var capacityId : String? = null,
    val quantity : Int,     // requested capacity
    var priceCat : String? = null,   // price category
    var sid: String? = null,
    /** Sức chứa chung cả ngày của vé theo lịch, trừ song song với bộ đếm của chính vé. */
    @BsonRepresentation(BsonType.OBJECT_ID)
    var dayCapacityId: String? = null,
)

/**
 * Tách mỗi dòng thành các bộ đếm phải trừ/hoàn: bộ đếm của vé (capacityId ?: ticketId) và, với vé theo lịch có sức
 * chứa chung cả ngày, thêm một dòng trỏ vào capacity của ngày. Các hàm trừ/hoàn chỉ hiểu một bộ đếm mỗi dòng, nên
 * mọi chỗ đụng tới tồn kho đi qua hàm này. Gọi lại trên kết quả không đổi gì (dayCapacityId đã bị xóa).
 */
fun List<TicketReservationDetailsDocument>.toCounterUnits(): List<TicketReservationDetailsDocument> = flatMap { detail ->
    val dayCapacityId = detail.dayCapacityId ?: return@flatMap listOf(detail)
    listOf(
        detail.copy(dayCapacityId = null),
        detail.copy(capacityId = dayCapacityId, dayCapacityId = null),
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TicketReservationDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id : String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId : String,    // id of the event

    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId : String, // id of the session

    val details : List<TicketReservationDetailsDocument>,

    var status : TicketReservationStatus = TicketReservationStatus.Created,

    val customerUserId: String? = null,

    val expireInS: Long,

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)

data class BoundedTicketConfigDocument constructor(
    val total: Int? = null,
    val perCat: Map<String, Int>? = null,
    val perZone: Map<String, Int>? = null,
)

data class ConfigPerReservationDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id : String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId : String,    // id of the event

    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId : String? = null, // id of the session

    val minTicket: BoundedTicketConfigDocument? = null,
    val maxTicket: BoundedTicketConfigDocument? = null,

    val expireInS: Long? = null,

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)
