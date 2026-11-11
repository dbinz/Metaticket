package metaticket.ticket.pojo.response

import com.fasterxml.jackson.annotation.JsonInclude
import metaticket.common.checksum.Checksum
import metaticket.ticket.configuration.Environment
import metaticket.ticket.datastructure.SeatType
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.datastructure.TicketStatus
import metaticket.ticket.pojo.db.TicketDocument
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import metaticket.ticket.pojo.db.TicketReservationDocument
import java.util.*


/**
 *
 * @author dongcatchay
 */

data class ReservationResponse constructor(
    val reservationId : String,
)

data class TicketReservationDetailResponse constructor(
    var ticketId : String,
    var quantity : Int,     // requested capacity
    var priceCat : String,   // price category
    var sid : String,   // seat id / sone id
    var capacityId: String?
)

data class TicketReservationResponse constructor(
    var id : String,
    var showId: String,
    var sessionId: String,
    var details : List<TicketReservationDetailResponse>,
    var status : TicketReservationStatus,
    var customerUserId: String?,
    var expireInS: Long,
    var createdAt: Long,
    val updatedAt: Long,
)

fun TicketReservationDetailsDocument.toResponse(): TicketReservationDetailResponse {
    return TicketReservationDetailResponse(
        ticketId = this.ticketId,
        quantity = this.quantity,
        priceCat = this.priceCat!!,
        sid = this.sid!!,
        capacityId = this.capacityId
    )
}

fun TicketReservationDocument.toResponse(): TicketReservationResponse {
    val countDown = if (this.expireInS > -1)
        this.expireInS - (Date().time - this.createdAt.time) / 1000
    else
        360000

    return TicketReservationResponse(
        id = Checksum.encode(this.id, Environment.secretKey),
        showId = this.showId,
        sessionId = this.sessionId,
        details = this.details.map { it.toResponse() },
        status = this.status,
        customerUserId = this.customerUserId,
        expireInS = countDown,
        createdAt = createdAt.time,
        updatedAt = lastModified.time
    )
}

data class TicketResponse constructor(
    var id: String,
    var sid : String,           // id of the seat inside the seat plan
    var showId: String,
    var sessionId: String,
    var seatType : SeatType,
    var status : TicketStatus,
    var resCap : Int? = null,       // capacity reserveds
    var totCap : Int? = null,       // capacity total
    var remCap : Int? = null,       // capacity remaining
    var priceCat : String,      // price category
    var ticketSlots: Int? = null,

    // base price is the price assigned to the ticket but not the final price charged to user
    // the actual charged price depends on promotional activities or other factors
    var basePrice : Long,
    var dname: String? = null,
    var capacityId: String? = null,

    // Ticket policy description, this field used to note about ticket, example using in homepage for ticket policy configuration
    var policy: String? = null,

    /**
     * Visit date "yyyy-MM-dd" of a calendar ticket: tickets of different dates share the same sid, so admin screens
     * need it to tell them apart. Omitted from JSON when null so normal tickets keep their old shape.
     */
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    var calendarDate: String? = null,

    var version: Int,
    val createdAt: Long,
    val lastModified: Long,
)

fun TicketDocument.toResponse(): TicketResponse {
    return TicketResponse(
        id = this.id,
        sid = this.sid,
        showId = this.showId,
        sessionId = this.sessionId,
        seatType = this.seatType,
        status = this.status,
        resCap = this.resCap,
        totCap = this.totCap,
        remCap = this.remCap,
        priceCat = this.priceCat,
        basePrice = this.basePrice,
        ticketSlots = this.ticketSlots,
        dname = this.dname,
        capacityId = this.capacityId,
        policy = this.policy,
        calendarDate = this.calendarDate,
        version = this.version,
        createdAt = this.createdAt.time,
        lastModified = this.lastModified.time,
    )
}

data class CountTicketOfShowResponse(
    val sessionId: String,
    val noTicket: Long,
)