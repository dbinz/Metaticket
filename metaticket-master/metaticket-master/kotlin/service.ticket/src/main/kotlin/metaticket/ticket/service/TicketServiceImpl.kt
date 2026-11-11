package metaticket.ticket.service

import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.joinAll
import metaticket.common.coroutine.mapLaunch
import metaticket.common.logger.Logging
import metaticket.ticket.database.service.TicketCalendarDayDBService
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.datastructure.TicketStatus
import metaticket.ticket.grpc.TicketMessage
import metaticket.ticket.grpc.TicketServiceGrpcKt.TicketServiceCoroutineImplBase
import metaticket.ticket.pojo.db.TicketDocument
import metaticket.ticket.redis.RedisAsyncClient

/**
 * Implementation of the Ticket Service that handles ticket reservations and management.
 * This service provides functionality for managing ticket reservations, retrieving ticket information,
 * and handling order status changes.
 *
 * @property ticketDocDB Database service for ticket documents
 */
class TicketServiceImpl(
    private val ticketDocDB: TicketDocDBService,
    private val ticketCapacityDB: TicketCapacityDBService,
    private val redisAsyncClient: RedisAsyncClient,
    private val calendarDayDB: TicketCalendarDayDBService,
) : TicketServiceCoroutineImplBase(), Logging {

    private companion object {
        const val REDIS_DELETE_CHUNK = 500
    }

    /**
     * Retrieves tickets information by IDs.
     *
     * @param request The request containing the ticket IDs
     * @return Response containing tickets details
     */
    override suspend fun getTicketByIds(request: TicketMessage.GetTicketByIdsRequest): TicketMessage.GetTicketsResponse {
        try {
            val tickets = ticketDocDB.loadTicketDocByIds(request.idsList)

            return TicketMessage.GetTicketsResponse.newBuilder()
                .addAllTickets(tickets.map { ticket -> ticket.toProto() })
                .build()

        } catch (e: StatusException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Error in getTicket... ", e)
            throw StatusException(Status.INTERNAL.withDescription("Load ticket failed"))
        }
    }

    override suspend fun getTicketBySids(request: TicketMessage.GetTicketBySidsRequest): TicketMessage.GetTicketsResponse {
        try {
            val tickets = ticketDocDB.loadTicketDocBySids(request.showId, request.sessionId,request.sidsList)

            return TicketMessage.GetTicketsResponse.newBuilder()
                .addAllTickets(tickets.map { ticket -> ticket.toProto() })
                .build()

        } catch (e: StatusException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Error in getTicket... ", e)
            throw StatusException(Status.INTERNAL.withDescription("Load ticket failed"))
        }
    }

    override suspend fun getTicketByShowAndSession(request: TicketMessage.GetTicketByShowAndSessionRequest): TicketMessage.GetTicketsResponse {
        try {
            val tickets = ticketDocDB.loadAllTicketDoc(request.showId, request.sessionId)

            return TicketMessage.GetTicketsResponse.newBuilder()
                .addAllTickets(tickets.map { ticket -> ticket.toProto() })
                .build()

        } catch (e: StatusException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Error in getTicket... ", e)
            throw StatusException(Status.INTERNAL.withDescription("Load ticket failed"))
        }
    }

    /**
     * Nạp bộ đếm Redis từ Mongo khi mở bán. Chỉ tạo key còn thiếu (SETNX): Paused → Open mà ghi đè thì mất các lượt đã
     * trừ trên Redis nhưng chưa kịp ghi Mongo, gây bán quá số vé. Stopped → Open vẫn nạp đủ vì stopTicket đã xóa key.
     * Vé theo lịch không nạp ở đây (hàng nghìn ngày), bộ đếm của chúng được khởi tạo lười khi giữ chỗ.
     */
    private suspend fun initRemainingTicket(showId: String, sessionId: String) {
        ticketDocDB.loadOpenNonCalendarTicketDoc(showId, sessionId)
            .mapLaunch { tk ->
                if (tk.capacityId != null) {
                    ticketCapacityDB.loadTicketCapacityById(tk.capacityId!!)?.let { c ->
                        val res = redisAsyncClient.setNxTicketRemCap(c.id, c.remCap)
                        logger.info("ticket {} init remaining with capacity {} {}", tk.id, c.id, res)
                    } ?: run {
                        logger.error("ticket {} failed to init remaining not found capacity {}", tk.id, tk.capacityId)
                    }
                } else {
                    val res = redisAsyncClient.setNxTicketRemCap(tk.id, tk.remCap!!)
                    logger.info("ticket {} init remaining {}", tk.id, res)
                }
            }.joinAll()
    }

    override suspend fun startTicket(request: TicketMessage.StartTicketRequest): Empty {
        ticketDocDB.updateAllTicketStatus(request.showId, request.sessionId, TicketStatus.Opened)
        initRemainingTicket(request.showId, request.sessionId)
        logger.info("started ticket for show {} session {}", request.showId, request.sessionId)
        return Empty.getDefaultInstance()
    }

    /**
     * Remove all remaining ticket from redis. Keys are collected and deleted in chunks: a calendar session has one
     * ticket per date (thousands), so loading each ticket's capacity one by one would be an N+1 over the whole year.
     */
    private suspend fun removeRemainingTicket(showId: String, sessionId: String) {
        val ticketKeys = ticketDocDB.loadAllTicketDoc(showId, sessionId).map { it.capacityId ?: it.id }
        val keys = (ticketKeys + calendarDayDB.loadAllCapacityIds(sessionId)).distinct()
        keys.chunked(REDIS_DELETE_CHUNK).forEach { chunk ->
            val res = redisAsyncClient.removeTicketRemCap(*chunk.toTypedArray())
            logger.info("show {} session {} removed {} of {} remaining counters", showId, sessionId, res, chunk.size)
        }
    }

    override suspend fun getCalendarMinPrice(request: TicketMessage.GetCalendarMinPriceRequest): TicketMessage.GetCalendarMinPriceResponse {
        try {
            val minPrice = ticketDocDB.calendarMinPrice(request.showId, request.sessionId, request.fromDate)
            return TicketMessage.GetCalendarMinPriceResponse.newBuilder()
                .also { builder -> minPrice?.let { builder.setMinPrice(it) } }
                .build()
        } catch (t: Throwable) {
            logger.error("Error in getCalendarMinPrice show {} session {}... ", request.showId, request.sessionId, t)
            throw StatusException(Status.INTERNAL.withDescription("Load calendar min price failed"))
        }
    }

    override suspend fun stopTicket(request: TicketMessage.StopTicketRequest): Empty {
        try {
            removeRemainingTicket(request.showId, request.sessionId)
            ticketDocDB.updateAllTicketStatus(request.showId, request.sessionId, TicketStatus.Closed)
            logger.info("stopped ticket for show {} session {}", request.showId, request.sessionId)
            return Empty.getDefaultInstance()
        } catch (t: Throwable) {
            logger.error("Error in stopTicket... ", t)
            throw StatusException(
                Status.INTERNAL.withDescription("Stop ticket failed")
            )
        }
    }
}

fun TicketDocument.toProto(): TicketMessage.TicketProto? {
    return TicketMessage.TicketProto.newBuilder()
        .setId(id)
        .setShowId(showId)
        .setSessionId(sessionId)
        .setSid(sid)
        .setSeatType(seatType.name)
        .setPriceCat(priceCat)
        .setCreatedAt(createdAt.time)
        .setLastModified(lastModified.time)
        .setStatus(status.name)
        .setBasePrice(basePrice)
        .setVersion(version)
        .also {
            dname?.let { dname -> it.setDname(dname) }
            policy?.let { policy -> it.setPolicy(policy) }
            totCap?.let { totCap -> it.setTotCap(totCap) }
            resCap?.let { resCap -> it.setResCap(resCap) }
            remCap?.let { remCap -> it.setRemCap(remCap) }
            ticketSlots?.let { slots -> it.setTicketSlots(slots) }
            capacityId?.let { capId -> it.setCapacityId(capId) }
            calendarDate?.let { date -> it.setCalendarDate(date) }
        }
        .build()
}