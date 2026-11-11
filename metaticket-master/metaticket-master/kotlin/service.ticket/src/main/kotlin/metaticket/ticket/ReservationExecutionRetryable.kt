package metaticket.ticket

import kotlinx.coroutines.joinAll
import metaticket.common.coroutine.launch
import metaticket.common.coroutine.mapLaunch
import metaticket.common.logger.Logging
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.database.service.TicketReservationDBService
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.datastructure.TicketReservationStatus.*
import metaticket.ticket.gateway.JobrunrGateway
import metaticket.ticket.gateway.ShowGateway
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import metaticket.ticket.pojo.db.TicketReservationDocument
import metaticket.ticket.pojo.db.toCounterUnits
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.redis.UpdateRedisFailedConstants


/**
 *
 * @author dongcatchay
 */
class ReservationExecutionRetryable(
    private val redisClient: RedisAsyncClient,
    private val ticketDocDB: TicketDocDBService,
    private val ticketCapacityDB: TicketCapacityDBService,
    private val ticketResDB: TicketReservationDBService,
    private val jobrunrGateway: JobrunrGateway,
    private val showGateway: ShowGateway,
): Logging {

    suspend fun releaseReservation(resId: String) {
        val reservation = redisClient.getReservation(resId) ?: run {
            logger.info("reservation {} could not release, not found", resId)
            return
        }

        if (reservation.status == Created) {
            launch { removeReservationOnRedis(resId) }
            return
        }

        if (reservation.status == Reserving) {
            logger.info("reservation {} is reserving, cannot release", reservation.id)
            return
        }

        if (reservation.status == Removing) {
            logger.info("reservation {} is in progress removing", reservation.id)
            return
        }

        if (reservation.status == Removed) {
            logger.info("reservation {} released already", reservation.id)
            return
        }

        if (reservation.status !in listOf(Reserved)) {
            logger.error("reservation {} could not release when status is {}", reservation.id, reservation.status)
            return
        }

        logger.info("reservation {} is releasing", reservation.id)

        val updateRedis = redisClient.updateReservationStatus(resId, reservation.status, Removing)

        if (updateRedis == UpdateRedisFailedConstants.NOT_EXIST) {
            launch { updateReservationStatusDb(resId, reservation.status, Removed) }
            return
        }

        if (updateRedis == UpdateRedisFailedConstants.FAILED) {
            logger.error("reservation {} could not remove from status {}", reservation.id, reservation.status)
            jobrunrGateway.scheduleReleaseReservation(resId)
            return
        }

        launch { removeReservationOnRedis(resId) }

        launch { updateReservationStatusDb(resId, Removing, Removed) }

        launch { releaseReservedCapInDb(resId, reservation.details) }

        launch { releaseRemainingCapOnRedis(resId, reservation.details) }

        logger.info("reservation {} is released", reservation.id)
    }

    suspend fun releaseRemainingCapOnRedis(resId: String, details: List<TicketReservationDetailsDocument>) {
        // Hoàn cả sức chứa chung cả ngày của vé theo lịch; danh sách đã tách rồi thì tách lại không đổi gì.
        val tickets = details.toCounterUnits()
        val failed = ArrayList<TicketReservationDetailsDocument>()
        tickets.mapLaunch {
            val r = try {
                redisClient.addTicketRemCap(it.capacityId ?: it.ticketId, it.quantity)
            } catch (t: Throwable) {
                logger.error("reservation {} exception revert the remaining for ticket {} on redis... ", resId, it.ticketId, t)
                UpdateRedisFailedConstants.FAILED
            }
            if (r == UpdateRedisFailedConstants.NOT_EXIST) {
                logger.info("reservation {} revert the remaining for not exist ticket {} on redis", resId, it.ticketId)
            } else if (r < 0) {
                logger.error("reservation {} fail to revert the remaining for ticket {} on redis", resId, it.ticketId)
                failed.add(it)
            } else {
                logger.info("reservation {} success revert the remaining for ticket {} on redis", resId, it.ticketId)
            }
        }.joinAll()

        if (failed.isNotEmpty()) {
            logger.error("reservation {} scheduling to revert the remaining for ticket {} on redis", resId, failed)
            jobrunrGateway.scheduleRollbackRemainingCapRedis(resId, failed)
            return
        }

        logger.info("reservation {} success reverts all the remaining on redis", resId)
    }

    suspend fun releaseReservedCapInDb(resId: String, details: List<TicketReservationDetailsDocument>) {
        val tickets = details.toCounterUnits()
        val updatesTicket = tickets.filter { it.capacityId == null }.map { it.ticketId to -it.quantity }.toList()
        val updatesTicketCapacity = tickets.filter { it.capacityId != null }.map { it.capacityId!! to -it.quantity }.toList()

        if (updatesTicket.isNotEmpty()) {
            val updatedTicket = try {
                ticketDocDB.addReservedCap(updatesTicket)
            } catch (t: Throwable) {
                logger.error("reservation {} exception rollback reserved capacity {} in ticket db... ", resId, updatesTicket, t)
                false
            }

            if (!updatedTicket) {
                logger.error("reservation {} failed to rollback reserved capacity {} in ticket db", resId, updatesTicket)
                jobrunrGateway.scheduleRollbackReservedCapDb(resId, tickets.filter { it.capacityId == null })
            }
        }

        if (updatesTicketCapacity.isNotEmpty()) {
            val updatedTicketCapacity = try {
                ticketCapacityDB.addReservedCap(updatesTicketCapacity)
            } catch (t: Throwable) {
                logger.error("reservation {} exception rollback reserved capacity {} in ticket capacity db... ", resId, updatesTicket, t)
                false
            }

            if (!updatedTicketCapacity) {
                logger.error("reservation {} failed to rollback reserved capacity {} in ticket capacity db", resId, updatesTicket)
                jobrunrGateway.scheduleRollbackReservedCapDb(resId, tickets.filter { it.capacityId != null })
                return
            }
        }

        logger.info("reservation {} success rollback reserved capacity {} in db", resId, tickets)
    }

    suspend fun removeReservationOnRedis(resId: String) {
        val removed = try {
            redisClient.removeReservation(resId)
        } catch (t: Throwable) {
            logger.error("reservation {} exception removes on redis... ", resId, t)
            -1
        }

        if (removed < 0) {
            logger.error("reservation {} could not remove on redis", resId)
            jobrunrGateway.scheduleRemoveReservationRedis(resId)
            return
        }

        logger.info("reservation {} removed on redis", resId)
    }

    suspend fun createReservationDb(reservation: TicketReservationDocument) {
        val createdRes = try {
            ticketResDB.insertReservation(reservation)
        } catch (t: Throwable) {
            logger.error("reservation {} exception save in db... ", reservation.id, t)
            null
        }
        if (createdRes == null) {
            logger.error("reservation {} failed save to db", reservation.id)
            jobrunrGateway.scheduleCreateReservationDb(reservation)
            return
        }

        logger.info("reservation {} saved to db", reservation.id)
    }

    suspend fun updateReservationStatusDb(resId: String, oldStatus: TicketReservationStatus, newStatus: TicketReservationStatus) {
        val updated = try {
            ticketResDB.updateStatus(resId, oldStatus, newStatus, before = true)
        } catch (t: Throwable) {
            logger.error("reservation {} exception update status from {} to {} in db... ", resId, oldStatus, newStatus, t)
            null
        }

        if (updated == null) {
            logger.error("reservation {} update status from {} to {} failed in db", resId, oldStatus, newStatus)
            jobrunrGateway.scheduleUpdateReservationStatusDb(resId, oldStatus, newStatus)
            return
        }

        if (updated.status == newStatus) {
            logger.info("reservation {} status {} updated already in db", resId, newStatus)
            return
        }

        val updateSoldTicket = if (newStatus == OrderSuccessful || newStatus == Locked) {
            updated.details.sumOf { it.quantity }
        } else if (newStatus == Unlocked || (newStatus == OrderCancelled && updated.status == OrderSuccessful)) {
            updated.details.sumOf { it.quantity } * -1
        } else null

        if (updateSoldTicket != null) {
            val sentToShow = showGateway.updateSoldTicket(resId, updated.showId, updateSoldTicket)
            if (!sentToShow) {
                jobrunrGateway.scheduleUpdateSoldTicket(resId, updated.showId, updateSoldTicket)
            }
        }

        logger.info("reservation {} updated from {} to {} in db", resId, updated.status, newStatus)
    }
}
