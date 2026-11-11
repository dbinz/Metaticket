package metaticket.ticket.pojo.db

import metaticket.ticket.datastructure.SeatType
import metaticket.ticket.datastructure.TicketStatus
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

/**
 * @author dongcatchay
 */

data class TicketCapacityDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id : String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    var showId : String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    var sessionId : String? = null, // define capacity on each session

    var priceCat: String? = null, // define capacity on each price category

    val description: String? = null,

    var resCap : Int = 0,       // capacity reserved
    var totCap : Int = 0,       // capacity total
    var remCap : Int = 0,       // capacity remaining

    /** Sức chứa chung cả ngày của vé theo lịch ("yyyy-MM-dd"); null với capacity thường. */
    var calendarDate: String? = null,

    var version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)

data class TicketDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    var showId : String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    var sessionId : String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    var capacityId : String? = null,

    var sid : String,           // id of the seat inside the seat plan
    var seatType : SeatType,
    var status : TicketStatus,
    var dname: String? = null,
    var resCap : Int? = null,       // capacity reserved
    var totCap : Int? = null,       // capacity total
    var remCap : Int? = null,       // capacity remaining
    var priceCat : String,      // price category
    // base price is the price assigned to the ticket but not the final price charged to user
    // the actual charged price depends on promotional activities or other factors
    var basePrice : Long,

    var policy: String? = null,

    var ticketSlots: Int? = 1,

    /** Ngày tham quan "yyyy-MM-dd" (giờ VN) của vé theo lịch; null với vé suất thường. */
    var calendarDate: String? = null,

    var version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)

data class CountTicketOfShowDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,
    val noTicket: Long,
)