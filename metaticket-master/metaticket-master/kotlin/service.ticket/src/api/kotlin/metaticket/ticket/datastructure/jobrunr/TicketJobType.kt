package metaticket.ticket.datastructure.jobrunr


/**
 *
 * @author dongcatchay
 */
enum class TicketJobType {
    ReleaseReservation, NotifyOrderPaying, NotifyOrderSuccessful, NotifyOrderFailed, NotifyOrderCancelled,
    CreateReservationDb, RemoveReservationRedis, RollbackRemainingCapRedis, RollbackReservedCapDb, UpdateReservationStatusDb
}
