package metaticket.ticket.pojo.request

import metaticket.ticket.datastructure.SeatType
import metaticket.ticket.datastructure.TicketReservationStatus

/**
 *
 * @author dongcatchay
 */

data class TicketDocDTO(
    val sid : String,
    val dname: String? = null,
    val seatType : SeatType,
    val totCap : Int? = null,   // total capacity
    val priceCat : String,
    val basePrice : Long,
    val capacityId: String? = null,
    val policy: String? = null
)

data class CreateTicketDocRequest(
    val showId : String,
    val sessionId : String,
    val tickets : List<TicketDocDTO>
)

data class UpdateTicketDocRequest(
    val showId : String,
    val sessionId : String,
    val sid : String,
    val dname: String? = null,
    val totCap : Int? = null,   // total capacity
    val priceCat : String,
    val capacityId: String? = null,
    val policy: String? = null
)

data class UpdateTicketSlotRequest(
    val showId: String,
    val sessionId: String,
    val ticketIds : List<String>,
    val slot: Int,
)

data class LoadAllTicketDocRequest(
    val showId: String,
    val sessionId: String
)

data class LoadTicketDocByTimeRequest(
    val showId: String,
    val sessionId: String,
    val startTime: Long, // timestamp
    val endTime: Long ?, // timestamp, null meaning current time
)

data class ShowSessionRequest(
    val showId: String,
    val sessionId: String,
)

data class LoadReservationByTicketId(
    val tkId: String,
    val reservationStatus: List<TicketReservationStatus>
)

data class ReleaseExpiredReservationRequest(
    val showId: String,
    val sessionId: String,
    val lastUpdatedTime: Long
)

data class TicketReservationDetailDTO(
    val ticketId : String,
    var capacityId : String? = null,
    val quantity : Int,     // requested capacity
    var priceCat : String? = null,   // price category
    var sid: String? = null,
    // Server luôn ghi đè lúc enrich (từ ngày của vé theo lịch), giá trị client gửi bị bỏ.
    var dayCapacityId: String? = null,
)

data class CreateTicketReservationRequest(
    val showId : String,    // id of the event
    val sessionId : String, // id of the session
    val details : List<TicketReservationDetailDTO>,
)