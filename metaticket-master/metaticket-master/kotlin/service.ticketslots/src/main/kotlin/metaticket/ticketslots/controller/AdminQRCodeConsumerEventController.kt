package metaticket.ticketslots.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.ticketslots.dlq.storage.QRCodeEventConsumerDLQStorage
import metaticket.ticketslots.dlq.service.QRCodeEventConsumerRetryService
import metaticket.ticketslots.model.FailedEventStatus
import java.util.Date

/**
 * Controller cho các admin endpoints để quản lý QR code consumer events
 */
class AdminQRCodeConsumerEventController(
    private val qrCodeConsumerDLQStorage: QRCodeEventConsumerDLQStorage,
    private val consumerRetryService: QRCodeEventConsumerRetryService
) : Logging {

    /**
     * Lấy tất cả failed consumer events
     * GET /admin/qrcode-events/consumer/failed
     */
    suspend fun getFailedEvents(call: ApplicationCall) {
        try {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val status = call.request.queryParameters["status"]?.let {
                try {
                    FailedEventStatus.valueOf(it.uppercase())
                } catch (e: Throwable) {
                    return call.respond(HttpStatusCode.BadRequest, "Trạng thái không đúng")
                }
            }
            
            val events = qrCodeConsumerDLQStorage.getFailedEvents(
                page = offset / limit,
                size = limit,
                status = status
            )
            
            val response = mapOf(
                "events" to events,
                "total" to events.size,
                "limit" to limit,
                "offset" to offset,
                "type" to "consumer",
                "timestamp" to Date().time
            )
            
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            logger.error("Error getting failed consumer events", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to get consumer events: ${e.message}")
        }
    }

    /**
     * Manual retry một consumer event cụ thể
     * POST /admin/qrcode-events/consumer/{eventId}/retry
     */
    suspend fun retryEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["eventId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Event ID is required")
                return
            }
            
            val event = try {
                qrCodeConsumerDLQStorage.getById(eventId)
            } catch (e: Throwable) {
                return call.respond(HttpStatusCode.BadRequest, "Lỗi hệ thống")
            }
            
            if (event == null) {
                call.respond(HttpStatusCode.NotFound, "Consumer event not found")
                return
            }
            
            if (event.status != FailedEventStatus.PENDING && event.status != FailedEventStatus.DISABLED) {
                call.respond(HttpStatusCode.BadRequest, "Consumer event cannot be retried. Current status: ${event.status}")
                return
            }
            
            // Trigger manual retry
            val success = consumerRetryService.retrySpecificEvent(eventId)
            
            if (!success) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to retry consumer event")
                return
            }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "Consumer event retry initiated",
                    "eventId" to eventId,
                    "type" to "consumer",
                    "timestamp" to Date().time
                )
            )
        } catch (e: Exception) {
            logger.error("Error retrying consumer event", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to retry consumer event: ${e.message}")
        }
    }

    /**
     * Disable một consumer event
     * PUT /admin/qrcode-events/consumer/{eventId}/disable
     */
    suspend fun disableEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["eventId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Event ID is required")
                return
            }
            
            val event = try {
                qrCodeConsumerDLQStorage.getById(eventId)
            } catch (e: IllegalArgumentException) {
                return call.respond(HttpStatusCode.BadRequest, "Lỗi hệ thống")
            }
            
            if (event == null) {
                call.respond(HttpStatusCode.NotFound, "Consumer event not found")
                return
            }
            
            if (event.status == FailedEventStatus.PROCESSED) {
                call.respond(HttpStatusCode.BadRequest, "Cannot disable processed consumer event")
                return
            }
            
            qrCodeConsumerDLQStorage.updateEventStatus(eventId, FailedEventStatus.DISABLED)
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "Consumer event disabled successfully",
                    "eventId" to eventId,
                    "type" to "consumer",
                    "timestamp" to Date().time
                )
            )
        } catch (e: Exception) {
            logger.error("Error disabling consumer event", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to disable consumer event: ${e.message}")
        }
    }

    /**
     * Archive một consumer event
     * PUT /admin/qrcode-events/consumer/{eventId}/archive
     */
    suspend fun archiveEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["eventId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Event ID is required")
                return
            }
            
            val event = try {
                qrCodeConsumerDLQStorage.getById(eventId)
            } catch (e: IllegalArgumentException) {
                return call.respond(HttpStatusCode.BadRequest, "Lỗi hệ thống")
            }
            
            if (event == null) {
                call.respond(HttpStatusCode.NotFound, "Consumer event not found")
                return
            }
            
            if (event.status == FailedEventStatus.PROCESSED) {
                call.respond(HttpStatusCode.BadRequest, "Cannot archive processed consumer event")
                return
            }
            
            qrCodeConsumerDLQStorage.updateEventStatus(eventId, FailedEventStatus.ARCHIVED)
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "Consumer event archived successfully",
                    "eventId" to eventId,
                    "type" to "consumer",
                    "timestamp" to Date().time
                )
            )
        } catch (e: Exception) {
            logger.error("Error archiving consumer event", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to archive consumer event: ${e.message}")
        }
    }
}