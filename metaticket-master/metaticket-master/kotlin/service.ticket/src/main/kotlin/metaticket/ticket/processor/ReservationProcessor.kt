package metaticket.ticket.processor

import kotlinx.coroutines.joinAll
import metaticket.common.coroutine.mapLaunch
import metaticket.common.logger.Logging
import metaticket.ticket.ReservationExecutionRetryable
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.isCalendar
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.datastructure.TicketReservationStatus.Reserved
import metaticket.ticket.datastructure.TicketReservationStatus.Reserving
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import metaticket.ticket.pojo.db.toCounterUnits
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.redis.UpdateRedisFailedConstants
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class is responsible for processing ticket reservations.
 * It handles the logic for reserving tickets, updating capacities, and managing reservation status.
 */
class ReservationProcessor constructor(
    private val showSessionCache: ShowSessionCache,
    private val redisClient: RedisAsyncClient,
    private val ticketDocDB: TicketDocDBService,
    private val ticketCapacityDB: TicketCapacityDBService,
    private val resExeRetryable: ReservationExecutionRetryable
) : Logging {

    val available = AtomicBoolean(true)

    /**
     * Processes a ticket reservation.
     *
     */
    suspend fun process(resId: String) {
        available.getAndSet(false)

        logger.debug("reservation {} is reserving", resId)

        try {
            val updatedRem = ArrayList<TicketReservationDetailsDocument>()

            val reservation = try {
                redisClient.getReservation(resId) ?: run {
                    logger.error("reservation {} not found when process reserve", resId)
                    return
                }
            } catch (t: Throwable) {
                logger.error("reservation {}, exception when fetching on redis when process reserve... ", resId, t)
                return
            }

            val checking = showSessionCache.checkShowSessionInOpen(reservation.showId, reservation.sessionId) ?: kotlin.run {
                logger.error("show {} session {} not found", reservation.showId, reservation.sessionId)
                return
            }
            if (!checking) {
                logger.error("reservation {} cannot reserve cause show session is not open", resId)
                return
            }

            val calendarSession = showSessionCache.get(reservation.showId, reservation.sessionId)?.isCalendar() == true

            // Vé theo lịch có sức chứa chung cả ngày thì một dòng là hai bộ đếm (vé + ngày), phải trừ được cả hai.
            val units = reservation.details.toCounterUnits()
            units.mapLaunch {
                try {
                    val rem = takeOnRedis(it, calendarSession)
                    if (rem >= 0) {
                        logger.debug("reservation {} added on redis remaining capacity for {}", resId, it)
                        updatedRem.add(it)
                    } else if (rem == UpdateRedisFailedConstants.NOT_EXIST) {
                        logger.info("reservation {} not exist ticket {} on redis", resId, it.ticketId)
                        updatedRem.add(it)
                    } else {
                        logger.debug("reservation {} failed, not enough remaining capacity for {}", resId, it)
                    }
                } catch (t: Throwable) {
                    logger.error("reservation {}, exception when adding remaining capacity on redis when process reserve... ", resId, t)
                }
            }.joinAll()

            if (updatedRem.isEmpty()) {
                logger.debug("reservation {} failed to reserve, but not need to rollback", resId)
                resExeRetryable.removeReservationOnRedis(resId)
                return
            }

            if (updatedRem.size < units.size) {
                logger.error("reservation {} rollback ticket remaining {}", resId, updatedRem)
                resExeRetryable.releaseRemainingCapOnRedis(resId, updatedRem)
                resExeRetryable.removeReservationOnRedis(resId)

            } else {
                reservation.status = Reserved

                val updateTicketP = updatedRem.filter { it.capacityId == null }
                val updateTicketCapP = updatedRem.filter { it.capacityId != null }

                var updateResTicketDb = true
                var updateResTicketCapDb = true

                if (updateTicketP.isNotEmpty()) {
                    updateResTicketDb = try {
                        ticketDocDB.addReservedCap(updateTicketP.map { Pair(it.ticketId, it.quantity) })
                    } catch (t: Throwable) {
                        logger.error("reservation {} failed to add reserved capacity in ticket db... ", resId, t)
                        false
                    }
                    if (!updateResTicketDb) {
                        // Hoàn mọi bộ đếm đã trừ trên Redis (cả capacity/sức chứa ngày) và bỏ bước ghi capacity bên
                        // dưới: chỉ hoàn nhóm vé thì phần capacity bị giữ vĩnh viễn trên Redis lẫn trong Mongo.
                        logger.error("reservation {} will rollback all counters {}", resId, updatedRem)
                        resExeRetryable.releaseRemainingCapOnRedis(resId, updatedRem)
                    } else {
                        logger.info("reservation {} remaining {} updated in db", resId, updateTicketP)
                    }
                }

                if (updateResTicketDb && updateTicketCapP.isNotEmpty()) {
                    updateResTicketCapDb = try {
                        ticketCapacityDB.addReservedCap(updateTicketCapP.map { Pair(it.capacityId!!, it.quantity) })
                    } catch (t: Throwable) {
                        logger.error("reservation {} failed to add reserved capacity in ticket capacity db... ", resId, t)
                        false
                    }
                    if (!updateResTicketCapDb) {
                        logger.error("reservation {} rollback ticket {}", resId, updateTicketCapP)
                        resExeRetryable.releaseRemainingCapOnRedis(resId, updatedRem)
                    } else {
                        logger.info("reservation {} remaining {} updated in db", resId, updateTicketCapP)
                    }
                }

                if (!updateResTicketDb || !updateResTicketCapDb) {
                    logger.error("reservation {} will be removed on redis", resId)
                    resExeRetryable.removeReservationOnRedis(resId)
                    return
                }

                val updateRedis = try {
                    redisClient.updateReservationStatus(resId, Reserving, Reserved)
                } catch (t: Throwable) {
                    logger.error("reservation {} failed to update to reserved on redis... ", resId, t)
                    UpdateRedisFailedConstants.FAILED
                }
                if (updateRedis != 0) {
                    logger.error("reservation {} rollback all", resId)
                    resExeRetryable.releaseRemainingCapOnRedis(resId, updatedRem)
                    resExeRetryable.removeReservationOnRedis(resId)
                    resExeRetryable.releaseReservedCapInDb(resId, updatedRem)
                    return
                }
                logger.info("reservation {} updated to {} on redis", resId, Reserved)

                resExeRetryable.createReservationDb(reservation)

                logger.info("reservation {} reserved tickets {} ", resId, reservation.details)
            }
        } catch (t: Throwable) {
            // TODO not handle yet
            logger.error("reservation {} failed to process... ", resId, t)
        } finally {
            logger.info("reservation {} is ended reserving", resId)
            available.getAndSet(true)
        }

    }

    /**
     * Trừ bộ đếm Redis của một dòng. Key không tồn tại: vé suất thường giữ hành vi cũ (trả NOT_EXIST); vé theo lịch
     * không được nạp lúc mở bán nên khởi tạo lười từ Mongo (SETNX) rồi trừ lại — nếu vẫn không có key thì coi là
     * thất bại, không để vé lịch bán không giới hạn.
     */
    private suspend fun takeOnRedis(unit: TicketReservationDetailsDocument, calendarSession: Boolean): Int {
        val key = unit.capacityId ?: unit.ticketId
        val rem = redisClient.addTicketRemCap(key, -unit.quantity)
        if (rem != UpdateRedisFailedConstants.NOT_EXIST) return rem

        // Suất vé lịch chỉ bán vé lịch, nên không nạp được từ Mongo (vé/sức chứa ngày vừa bị admin xóa sau khi khách
        // tạo giữ chỗ) là thất bại — không được rơi về NOT_EXIST = "không giới hạn" của vé suất thường.
        val calendarRemCap = loadCalendarRemCap(unit)
            ?: return if (calendarSession) UpdateRedisFailedConstants.FAILED else rem
        redisClient.setNxTicketRemCap(key, calendarRemCap)
        val retried = redisClient.addTicketRemCap(key, -unit.quantity)
        return if (retried == UpdateRedisFailedConstants.NOT_EXIST) UpdateRedisFailedConstants.FAILED else retried
    }

    /** remCap trong Mongo nếu bộ đếm thuộc vé theo lịch; null với vé/capacity thường hoặc doc không còn. */
    private suspend fun loadCalendarRemCap(unit: TicketReservationDetailsDocument): Int? {
        val capacityId = unit.capacityId
        return if (capacityId != null) {
            ticketCapacityDB.loadTicketCapacityById(capacityId)?.takeIf { it.calendarDate != null }?.remCap
        } else {
            ticketDocDB.loadTicketDocById(unit.ticketId)?.takeIf { it.calendarDate != null }?.remCap
        }
    }
}
