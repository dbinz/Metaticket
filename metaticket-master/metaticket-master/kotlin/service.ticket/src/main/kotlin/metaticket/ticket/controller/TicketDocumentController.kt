package metaticket.ticket.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.CalendarException
import metaticket.ticket.calendar.CalendarTicketService
import metaticket.ticket.calendar.isCalendar
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.database.service.TicketReservationDBService
import metaticket.ticket.datastructure.TicketStatus
import metaticket.ticket.pojo.db.TicketCapacityDocument
import metaticket.ticket.pojo.db.TicketDocument
import metaticket.ticket.pojo.request.*
import metaticket.ticket.pojo.response.CountTicketOfShowResponse
import metaticket.ticket.pojo.response.toResponse
import metaticket.ticket.redis.RedisAsyncClient
import java.util.*

/**
 * Show each ticket's shared capacity numbers instead of its own. Each capacity only applies to the tickets pointing at
 * it — assigning every capacity to every ticket made the last capacity win for all of them.
 */
internal fun applyCapacities(tickets: List<TicketDocument>, capacities: List<TicketCapacityDocument>) {
    val ticketsByCapacity = tickets.groupBy { it.capacityId }
    capacities.forEach { cap ->
        ticketsByCapacity[cap.id]?.forEach {
            it.totCap = cap.totCap
            it.remCap = cap.remCap
            it.resCap = cap.resCap
        }
    }
}

class TicketDocumentController constructor(
    private val showSessionCache: ShowSessionCache,
    private val ticketDocDB : TicketDocDBService,
    private val ticketCapacityDB: TicketCapacityDBService,
    private val redisAsyncClient : RedisAsyncClient,
    private val reservationDBService: TicketReservationDBService,
    private val calendarService: CalendarTicketService,
) : Logging {

    private companion object {
        const val CALENDAR_MANAGED_MESSAGE = "Vé theo lịch được quản lý ở màn Lịch bán vé"
    }

    private suspend fun isCalendarSession(showId: String, sessionId: String): Boolean =
        showSessionCache.get(showId, sessionId)?.isCalendar() == true

    /** Vé của một ngày cho session vé theo lịch; null khi đã trả lỗi cho client. */
    private suspend fun calendarTicketsForSale(
        call: ApplicationCall, showId: String, sessionId: String, changedSince: Long? = null, until: Long? = null,
    ): List<TicketDocument>? {
        val date = call.parameters["date"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Thiếu ngày tham quan")
            return null
        }
        return try {
            calendarService.ticketsForSale(showId, sessionId, date, changedSince, until)
        } catch (e: CalendarException) {
            call.respond(HttpStatusCode.fromValue(e.status), e.message.orEmpty())
            null
        }
    }

    suspend fun createTicketDoc(call : ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        // TODO : some validation are needed, seats that are already existed, must not be re-insert
        val reqData = call.receive<CreateTicketDocRequest>()

        if (isCalendarSession(reqData.showId, reqData.sessionId)) {
            return call.respond(HttpStatusCode.BadRequest, CALENDAR_MANAGED_MESSAGE)
        }

        val checking = showSessionCache.checkShowSessionInOpen(reqData.showId, reqData.sessionId) ?: kotlin.run {
            logger.error("createTicketDoc, user {}, show {} session {} not found", opUserId, reqData.showId, reqData.sessionId)
            return call.respond(HttpStatusCode.InternalServerError, "Không tải được suất diễn")
        }
        if (checking) {
            logger.error("createTicketDoc, user {}, insert ticket doc failed {}, show session is open",opUserId, reqData)
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn đã mở bán")
        }

        // create tickets from the DTO
        val tickets = reqData.tickets.map {
            TicketDocument(
                showId = reqData.showId,
                sessionId = reqData.sessionId,
                sid = it.sid,
                dname = it.dname,
                seatType = it.seatType,
                status = TicketStatus.Created,
                basePrice = it.basePrice,
                priceCat = it.priceCat,
                totCap = it.totCap,
                remCap = it.totCap,
                resCap = it.totCap?.let { 0 },
                capacityId = it.capacityId,
                policy = it.policy,
                ticketSlots = 1,
            )
        }

        val res = ticketDocDB.insertManyTicketDocument(tickets)

        if (!res) {
            logger.error("createTicketDoc, user {}, insert ticket doc failed {}", opUserId, reqData)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }

        logger.info("createTicketDoc, user {}, inserted tickets document {}", opUserId, tickets)
        call.respond(HttpStatusCode.OK, tickets.map { it.toResponse() })
    }

    suspend fun updateTicketDoc(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val req = call.receive<UpdateTicketDocRequest>()

        // Lọc theo sid sẽ khớp vé của mọi ngày, nên vé theo lịch chỉ sửa qua màn Lịch bán vé.
        if (isCalendarSession(req.showId, req.sessionId)) {
            return call.respond(HttpStatusCode.BadRequest, CALENDAR_MANAGED_MESSAGE)
        }

        val checking = showSessionCache.checkShowSessionInOpen(req.showId, req.sessionId) ?: kotlin.run {
            logger.error("updateTicketDoc, user {}, show {} session {} not found", opUserId,  req.showId, req.sessionId)
            return call.respond(HttpStatusCode.NotFound, "Không tải được suất diễn")
        }
        if (checking) {
            logger.error("updateTicketDoc, user {}, show {} session {} update ticket doc failed, session is open {}", opUserId,  req.showId, req.sessionId, req)
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn đã mở bán")
        }

        val updatedTicket = ticketDocDB.updateTicketDocument(req) ?: kotlin.run {
            logger.error("updateTicketDoc, user {}, show {} session {} update ticket doc failed, cannot update in db {}", opUserId,  req.showId, req.sessionId, req)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }

        logger.info("updateTicketDoc, user {}, show {} session {} updated ticket doc {}", opUserId,  req.showId, req.sessionId, updatedTicket)

        call.respond(HttpStatusCode.OK, updatedTicket)
    }

    suspend fun updateTicketSlot(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val (showId, sessionId, ticketIds, slot) = call.receive<UpdateTicketSlotRequest>()

        val checking = showSessionCache.checkShowSessionInOpen(showId, sessionId) ?: kotlin.run {
            logger.error("updateTicketSlot, user {}, show {} session {} not found", opUserId, showId, sessionId)
            return call.respond(HttpStatusCode.NotFound, "Không tải được suất diễn")
        }
        if (checking) {
            logger.error("updateTicketSlot, user {}, show {} session {} update ticket doc failed, session is open", opUserId, showId, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn đã mở bán")
        }

        val updateResult = try {
            ticketDocDB.updateTicketSlot(ticketIds, slot)
        } catch (t: Throwable) {
            logger.error("updateTicketSlot {}, user {}, show {} session {}: Update failed with error", ticketIds, opUserId, showId, sessionId, t)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }

        logger.info("updateTicketSlot, user {}, Update completed {} to {}. ticketIds {}", opUserId, updateResult, slot, ticketIds)

        call.respond(HttpStatusCode.OK)
    }

    private suspend fun assignCapacity(tickets: List<TicketDocument>) {

        if (tickets.isEmpty()) return

        val capacityIds = tickets.mapNotNull { it.capacityId }.distinct()
        applyCapacities(tickets, ticketCapacityDB.loadTicketCapacityByIds(capacityIds))
    }

    suspend fun loadTicketDocByIds(call: ApplicationCall) {
        val ids = call.receive<List<String>>()
        val documents = ticketDocDB.loadTicketDocByIds(ids)
        assignCapacity(documents.filter { it.capacityId != null })
        call.respond(documents.map { it.toResponse() })
    }

    suspend fun loadTicketDocByIdsCheckSession(call: ApplicationCall) {
        val ids = call.receive<List<String>>()
        val documents = ticketDocDB.loadTicketDocByIds(ids)

        val showIds = documents.map { it.showId }.toSet()
        val sessionIds = documents.map { it.sessionId }.toSet()
        if (showIds.size != 1 || sessionIds.size != 1) {
            return call.respond(HttpStatusCode.BadRequest, "only can load ticket in session")
        }

        val checking = showSessionCache.checkShowSessionInOpen(showIds.first(), sessionIds.first()) ?: kotlin.run {
            logger.error("show {} session {} not found", showIds.first(), showIds.first())
            return call.respond(HttpStatusCode.InternalServerError, "Không tải được suất diễn")
        }
        if (!checking) {
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn chưa được mở bán")
        }

        assignCapacity(documents.filter { it.capacityId != null })
        call.respond(documents.map { it.toResponse() })
    }

    suspend fun loadTicketDocById(call: ApplicationCall) {
        val id = call.parameters["id"]
            ?: return call.respond(HttpStatusCode.BadRequest,"Thiếu ticket id")

        val document = ticketDocDB.loadTicketDocById(id)
            ?: return call.respond(HttpStatusCode.NotFound, "Không tìm thấy vé")

        document.capacityId?.let { assignCapacity(listOf(document)) }

        call.respond(document.toResponse())
    }

    suspend fun loadTicketDocByIdCheckSession(call: ApplicationCall) {
        val id = call.parameters["id"]
            ?: return call.respond(HttpStatusCode.BadRequest,"Thiếu ticket id")

        val document = ticketDocDB.loadTicketDocById(id)
            ?: return call.respond(HttpStatusCode.NotFound, "Không tìm thấy vé")

        val checking = showSessionCache.checkShowSessionInOpen(document.showId, document.sessionId) ?: kotlin.run {
            logger.error("show {} session {} not found", document.showId, document.sessionId)
            return call.respond(HttpStatusCode.InternalServerError, "Không tải được suất diễn")
        }
        if (!checking) {
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn chưa được mở bán")
        }

        document.capacityId?.let { assignCapacity(listOf(document)) }

        call.respond(document.toResponse())
    }

    suspend fun versionedLoadTicketDocByShowIdAndSessionIdCheckSession(call : ApplicationCall) {

        val showId = call.parameters["showId"]
        val sessionId = call.parameters["sessionId"]

        if (showId == null || sessionId == null) {
            return call.respond(HttpStatusCode.BadRequest, "Thiếu thông tin")
        }

        val reqData = LoadAllTicketDocRequest(showId , sessionId)

        val checking = showSessionCache.checkShowSessionInOpen(reqData.showId, reqData.sessionId) ?: kotlin.run {
            logger.error("show {} session {} not found", reqData.showId, reqData.sessionId)
            return call.respond(HttpStatusCode.InternalServerError, "Không tải được suất diễn")
        }
        if (!checking) {
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn chưa được mở bán")
        }

        val curr = Date()
        val documents = if (isCalendarSession(reqData.showId, reqData.sessionId)) {
            calendarTicketsForSale(call, reqData.showId, reqData.sessionId) ?: return
        } else {
            ticketDocDB.loadAllTicketDoc(reqData.showId, reqData.sessionId)
                .also { assignCapacity(it.filter { tk -> tk.capacityId != null }) }
        }

        call.respond(mapOf(
            Pair("tickets", documents.map { it.toResponse() }),
            Pair("version", curr.time)
        ))
    }

    suspend fun loadTicketDocByShowIdAndSessionId(call : ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val reqData = call.receive<LoadAllTicketDocRequest>()

        val documents = ticketDocDB.loadAllTicketDoc(reqData.showId, reqData.sessionId)
        assignCapacity(documents.filter { it.capacityId != null })

        call.respond(documents.map { it.toResponse() })
    }

    suspend fun versionedLoadTicketDocByTime(call: ApplicationCall) {

        val showId = call.parameters["showId"]
        val sessionId = call.parameters["sessionId"]
        val endTime = call.parameters["endTime"]
        val startTime = call.parameters["startTime"]

        if (showId == null || sessionId == null || startTime == null) {
            return call.respond(HttpStatusCode.BadRequest, "Thiếu thông tin")
        }

        val reqData = LoadTicketDocByTimeRequest(
            showId , sessionId, startTime.toLong(), endTime?.toLong()
        )

        val curr = Date()
        if (isCalendarSession(reqData.showId, reqData.sessionId)) {
            val documents = calendarTicketsForSale(call, showId, sessionId, reqData.startTime, reqData.endTime) ?: return
            call.response.header("version", curr.time)
            return call.respond(mapOf(
                Pair("tickets", documents.map { it.toResponse() }),
                Pair("version", reqData.endTime ?: curr.time)
            ))
        }
        val documents = ticketDocDB.loadTicketDocByTime(
            reqData.showId, reqData.sessionId, reqData.startTime, reqData.endTime
        )

        val checking = showSessionCache.checkShowSessionInOpen(reqData.showId, reqData.sessionId) ?: kotlin.run {
            logger.error("show {} session {} not found", reqData.showId, reqData.sessionId)
            return call.respond(HttpStatusCode.NotFound, "Không tải được suất diễn")
        }
        if (!checking) {
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn chưa được mở bán")
        }

        assignCapacity(documents.filter { it.capacityId != null })
        call.response.header("version", curr.time)
        call.respond(mapOf(
            Pair("tickets", documents.map { it.toResponse() }),
            Pair("version", reqData.endTime ?: curr.time)
        ))
    }

    suspend fun loadTicketDocByShowIdAndSessionIdCheckSession(call : ApplicationCall) {
        val reqData = call.receive<LoadAllTicketDocRequest>()

        // API cũ trả mọi vé của session; vé theo lịch phải lấy theo ngày qua bản GET có tham số date.
        if (isCalendarSession(reqData.showId, reqData.sessionId)) {
            return call.respond(HttpStatusCode.BadRequest, CALENDAR_MANAGED_MESSAGE)
        }

        val checking = showSessionCache.checkShowSessionInOpen(reqData.showId, reqData.sessionId)

        if (checking == null) {
            logger.error("show {} session {} not found", reqData.showId, reqData.sessionId)
            return call.respond(HttpStatusCode.NotFound, "Không tải được suất diễn")
        }

        if (checking == false) {
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn chưa được mở bán")
        }

        val documents = ticketDocDB.loadAllTicketDoc(reqData.showId, reqData.sessionId)
        assignCapacity(documents.filter { it.capacityId != null })

        call.respond(documents.map { it.toResponse() })
    }

    /**
     * Deprecated
     */
    suspend fun loadTicketDocByTime(call: ApplicationCall) {
        val curr = Date()
        val reqData = call.receive<LoadTicketDocByTimeRequest>()
        if (isCalendarSession(reqData.showId, reqData.sessionId)) {
            return call.respond(HttpStatusCode.BadRequest, CALENDAR_MANAGED_MESSAGE)
        }
        val documents = ticketDocDB.loadTicketDocByTime(
            reqData.showId, reqData.sessionId, reqData.startTime, reqData.endTime
        )

        val checking = showSessionCache.checkShowSessionInOpen(reqData.showId, reqData.sessionId) ?: kotlin.run {
            logger.error("show {} session {} not found", reqData.showId, reqData.sessionId)
            return call.respond(HttpStatusCode.NotFound, "Không tải được suất diễn")
        }
        if (!checking) {
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn chưa được mở bán")
        }

        assignCapacity(documents.filter { it.capacityId != null })
        call.response.header("version", curr.time)
        call.respond(documents.map { it.toResponse() })
    }

    suspend fun countTicketOfShow(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val showId = call.parameters["showId"]
            ?: return call.respond(HttpStatusCode.BadRequest, "Thiếu show id")

        val counts = ticketDocDB.countTicket(showId)
            .map { CountTicketOfShowResponse(sessionId = it.sessionId, noTicket = it.noTicket) }

        return call.respond(HttpStatusCode.OK, counts)
    }

    suspend fun deleteTicketByCat(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val req = call.receive<ClearTicketByCategoryRequest>()

        if (isCalendarSession(req.showId, req.sessionId)) {
            return call.respond(HttpStatusCode.BadRequest, CALENDAR_MANAGED_MESSAGE)
        }

        val checking = showSessionCache.checkShowSessionInOpen(req.showId, req.sessionId) ?: kotlin.run {
            logger.error("deleteTicketByCat, user {}, show {} session {} not found", opUserId, req.showId, req.sessionId)
            return call.respond(HttpStatusCode.NotFound, "Không tải được suất diễn")
        }
        if (checking) {
            logger.error("deleteTicketByCat, user {}, show {} session {} is after open state, cannot delete ticket of cat {}", opUserId, req.showId, req.sessionId, req.category)
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn đã được mở bán")
        }

        val ordering = reservationDBService.loadReservationOrderingByTicketCat(req.showId, req.sessionId, req.category)
            .map { it.id }
        if (ordering.isNotEmpty()) {
            logger.error("deleteTicketByCat, user {}, show {} session {}, cannot delete ticket of cat {}, order is paying or pay success {}", opUserId, req.showId, req.sessionId, req.category, ordering)
            return call.respond(HttpStatusCode.Forbidden, "Có đơn đang/đã thanh toán")
        }

        val tickets = ticketDocDB.loadTicketDocByCat(req.showId, req.sessionId, req.category)
        val ticketIds = tickets.map { it.id }

        var deletedInDb = 0L
        if (ticketIds.isNotEmpty()) {
            val deletedOnRedis = redisAsyncClient.removeTicketRemCap(*ticketIds.toTypedArray())
            logger.info("deleteTicketByCat, user {}, show {} session {} deleting ticket of cat {}, deleted {} ticket on redis", opUserId, req.showId, req.sessionId, req.category, deletedOnRedis)

            deletedInDb = ticketDocDB.deleteTickets(ticketIds)
            logger.info("deleteTicketByCat, user {}, show {} session {} deleting ticket of cat {}, deleted {} ticket in db", opUserId, req.showId, req.sessionId, req.category, deletedInDb)
        }

        logger.info("deleteTicketByCat, user {}, show {} session {} deleted {} ticket of cat {}", opUserId, req.showId, req.sessionId, deletedInDb, req.category)
        return call.respond(HttpStatusCode.OK, deletedInDb)
    }
}
