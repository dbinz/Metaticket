package metaticket.ticketslots.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.ticketslots.dlq.storage.QRCodeEventProducerDLQStorage
import metaticket.ticketslots.dlq.service.QRCodeEventProducerRetryService
import metaticket.ticketslots.model.FailedEventStatus
import java.util.Date

/**
 * Controller cho các admin endpoints để quản lý QR code producer events
 */
class AdminQRCodeProducerEventController(
    private val qrCodeProducerDLQStorage: QRCodeEventProducerDLQStorage,
    private val producerRetryService: QRCodeEventProducerRetryService
) : Logging {

    /**
     * Lấy tất cả failed producer events
     * GET /admin/qrcode-events/producer/failed
     */
    suspend fun getFailedEvents(call: ApplicationCall) {
        try {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val status = call.request.queryParameters["status"]?.let { 
                try {
                    FailedEventStatus.valueOf(it.uppercase())
                } catch (e: Throwable) {
                    return call.respond(HttpStatusCode.BadRequest, "Invalid status")
                }
            }
            
            val events = qrCodeProducerDLQStorage.getFailedEvents(
                page = offset / limit,
                size = limit,
                status = status
            )
            
            val response = mapOf(
                "events" to events,
                "total" to events.size,
                "limit" to limit,
                "offset" to offset,
                "type" to "producer",
                "timestamp" to Date().time
            )
            
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            logger.error("Error getting failed producer events", e)
            return call.respond(HttpStatusCode.InternalServerError, "Failed to get producer events: ${e.message}")
        }
    }

    /**
     * Manual retry một producer event cụ thể
     * POST /admin/qrcode-events/producer/{eventId}/retry
     */
    suspend fun retryEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["eventId"] ?: run {
                return call.respond(HttpStatusCode.BadRequest, "Event ID is required")
            }
            
            val event = qrCodeProducerDLQStorage.getById(eventId)
            if (event == null) {
                return call.respond(HttpStatusCode.NotFound, "Producer event not found")
            }
            
            if (event.status != FailedEventStatus.PENDING && event.status != FailedEventStatus.DISABLED) {
                return call.respond(HttpStatusCode.BadRequest, "Producer event cannot be retried. Current status: ${event.status}")
            }
            
            // Trigger manual retry
            val success = producerRetryService.retrySpecificEvent(eventId)
            
            if (!success) {
                return call.respond(HttpStatusCode.InternalServerError, "Failed to retry producer event")
            }
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "Producer event retry initiated",
                    "eventId" to eventId,
                    "type" to "producer",
                    "timestamp" to Date().time
                )
            )
        } catch (e: Exception) {
            logger.error("Error retrying producer event", e)
            return call.respond(HttpStatusCode.InternalServerError, "Failed to retry producer event: ${e.message}")
        }
    }

    /**
     * Disable một producer event
     * PUT /admin/qrcode-events/producer/{eventId}/disable
     */
    suspend fun disableEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["eventId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Event ID is required")
                return
            }
            
            val event = qrCodeProducerDLQStorage.getById(eventId)
            if (event == null) {
                call.respond(HttpStatusCode.NotFound, "Producer event not found")
                return
            }
            
            if (event.status == FailedEventStatus.PROCESSED) {
                call.respond(HttpStatusCode.BadRequest, "Cannot disable processed producer event")
                return
            }
            
            qrCodeProducerDLQStorage.updateEventStatus(eventId, FailedEventStatus.DISABLED)
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "Producer event disabled successfully",
                    "eventId" to eventId,
                    "type" to "producer",
                    "timestamp" to Date().time
                )
            )
        } catch (e: Exception) {
            logger.error("Error disabling producer event", e)
            return call.respond(HttpStatusCode.InternalServerError, "Failed to disable producer event: ${e.message}")
        }
    }

    /**
     * Archive một producer event
     * PUT /admin/qrcode-events/producer/{eventId}/archive
     */
    suspend fun archiveEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["eventId"] ?: run {
                return call.respond(HttpStatusCode.BadRequest, "Event ID is required")
            }
            
            val event = qrCodeProducerDLQStorage.getById(eventId)
            if (event == null) {
                return call.respond(HttpStatusCode.NotFound, "Producer event not found")
            }
            
            if (event.status == FailedEventStatus.PROCESSED) {
                return call.respond(HttpStatusCode.BadRequest, "Cannot archive processed producer event")
            }
            
            qrCodeProducerDLQStorage.updateEventStatus(eventId, FailedEventStatus.ARCHIVED)
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "message" to "Producer event archived successfully",
                    "eventId" to eventId,
                    "type" to "producer",
                    "timestamp" to Date().time
                )
            )
        } catch (e: Exception) {
            logger.error("Error archiving producer event", e)
            return call.respond(HttpStatusCode.InternalServerError,"Failed to archive producer event: ${e.message}")
        }
    }
}