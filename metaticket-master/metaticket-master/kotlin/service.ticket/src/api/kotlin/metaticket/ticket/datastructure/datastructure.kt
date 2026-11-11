package metaticket.ticket.datastructure


/**
 *
 * @author dongcatchay
 */

enum class TicketReservationStatus {
    Created, Reserving, Reserved, Locked, Unlocked, Removing, Removed, OrderPaying, OrderSuccessful, OrderCancelled, OrderFailed
}

enum class SeatType {
    SeatZone, Seat
}

enum class TicketStatus {
    Created, Opened, Blocked, Closed
}