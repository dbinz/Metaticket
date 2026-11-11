package metaticket.ticket.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.CancellationException
import metaticket.common.logger.Logging
import metaticket.ticket.calendar.CalendarException
import metaticket.ticket.calendar.CalendarTicketService
import metaticket.ticket.pojo.request.BulkUpdateCalendarRequest
import metaticket.ticket.pojo.request.DeleteCalendarRequest
import metaticket.ticket.pojo.request.GenerateCalendarRequest

class CalendarTicketController(
    private val calendarService: CalendarTicketService,
) : Logging {

    suspend fun generate(call: ApplicationCall) = withOperator(call, "generate") { opUserId ->
        call.respond(HttpStatusCode.OK, calendarService.generate(call.receive<GenerateCalendarRequest>(), opUserId))
    }

    suspend fun bulkUpdate(call: ApplicationCall) = withOperator(call, "bulkUpdate") { opUserId ->
        call.respond(HttpStatusCode.OK, calendarService.bulkUpdate(call.receive<BulkUpdateCalendarRequest>(), opUserId))
    }

    suspend fun delete(call: ApplicationCall) = withOperator(call, "delete") { opUserId ->
        call.respond(HttpStatusCode.OK, calendarService.delete(call.receive<DeleteCalendarRequest>(), opUserId))
    }

    suspend fun adminMonth(call: ApplicationCall) = withOperator(call, "adminMonth") {
        val (showId, sessionId, month) = monthParams(call) ?: return@withOperator
        call.respond(HttpStatusCode.OK, calendarService.adminMonth(showId, sessionId, month))
    }

    suspend fun publicMonth(call: ApplicationCall) {
        val (showId, sessionId, month) = monthParams(call) ?: return
        handleErrors(call, "publicMonth", null) {
            call.respond(HttpStatusCode.OK, calendarService.publicMonth(showId, sessionId, month))
        }
    }

    private suspend fun monthParams(call: ApplicationCall): Triple<String, String, String>? {
        val showId = call.parameters["showId"]
        val sessionId = call.parameters["sessionId"]
        val month = call.parameters["month"]
        if (showId.isNullOrBlank() || sessionId.isNullOrBlank() || month.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Thiếu thông tin")
            return null
        }
        return Triple(showId, sessionId, month)
    }

    private suspend fun withOperator(call: ApplicationCall, action: String, block: suspend (String) -> Unit) {
        val opUserId = call.request.headers["X-OP-User-Id"] ?: run {
            logger.error("calendar {}: missing op user id in header, maybe user haven't logged in", action)
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }
        handleErrors(call, action, opUserId) { block(opUserId) }
    }

    private suspend fun handleErrors(call: ApplicationCall, action: String, opUserId: String?, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            // Call bị hủy (client ngắt, server dừng): ném lại để coroutine kết thúc đúng, không trả 500 lên call đã hủy.
            throw e
        } catch (e: CalendarException) {
            logger.warn("calendar {}, user {}: rejected ({})", action, opUserId, e.message)
            call.respond(HttpStatusCode.fromValue(e.status), e.message.orEmpty())
        } catch (e: BadRequestException) {
            logger.warn("calendar {}, user {}: malformed request ({})", action, opUserId, e.message)
            call.respond(HttpStatusCode.BadRequest, "Dữ liệu không hợp lệ")
        } catch (e: ContentTransformationException) {
            logger.warn("calendar {}, user {}: malformed request ({})", action, opUserId, e.message)
            call.respond(HttpStatusCode.BadRequest, "Dữ liệu không hợp lệ")
        } catch (e: Throwable) {
            logger.error("calendar {}, user {}: failed", action, opUserId, e)
            call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }
    }
}
