package metaticket.ticketslots.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.ticketslots.dlq.service.OrderSucceedEventRetryService
import metaticket.ticketslots.dlq.storage.OrderSucceedEventConsumerDLQStorage
import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.pojo.db.OrderSucceedEventConsumerDLQDoc
import metaticket.ticketslots.processor.OrderSucceedProcessor
import java.util.*

/**
 * Admin controller để quản lý các OrderSucceedEvent bị lỗi
 * Cung cấp các endpoint để xem, retry và quản lý failed events
 */
class AdminOrderSucceedEventController(
    private val storage: OrderSucceedEventConsumerDLQStorage,
    private val processor: OrderSucceedProcessor,
    private val retryService: OrderSucceedEventRetryService
) : Logging {

    /**
     * Lấy danh sách các failed events với phân trang và filter
     */
    suspend fun getFailedEvents(call: ApplicationCall) {
        try {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val status = call.request.queryParameters["status"]?.let { 
                try {
                    FailedEventStatus.valueOf(it.uppercase())
                } catch (e: Throwable) {
                    return call.respond(HttpStatusCode.BadRequest, "Invalid status")
                }
            }
            val errorType = call.request.queryParameters["errorType"]
            
            // Validate parameters
            if (limit < 1 || limit > 100) {
                return call.respond(HttpStatusCode.BadRequest, "Limit phải từ 1 đến 100")
            }
            
            if (offset < 0) {
                return call.respond(HttpStatusCode.BadRequest, "Offset phải >= 0")
            }
            
            val events = storage.getFailedEvents(
                page = offset,
                status = status,
                errorType = errorType,
                size = limit
            )
            
            val totalCount = storage.countFailedEvents(
                status = status,
                errorType = errorType
            )
            
            val response = mapOf(
                "events" to events.map { it.toResponse() },
                "total" to totalCount,
                "limit" to limit,
                "offset" to offset,
                "hasMore" to (offset + limit < totalCount),
                "timestamp" to Date().time
            )
            
            call.respond(HttpStatusCode.OK, response)
            
        } catch (e: Exception) {
            logger.error("Error getting failed OrderSucceed events", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to get events: ${e.message}")
        }
    }

    /**
     * Lấy chi tiết một failed event theo ID
     */
    suspend fun getFailedEventById(call: ApplicationCall) {
        val eventId = call.parameters["id"] ?: run {
            return call.respond(HttpStatusCode.BadRequest, "Thiếu ID event")
        }
        try {
            val event = storage.getById(eventId) ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }
            
            call.respond(HttpStatusCode.OK, event.toResponse())
            
        } catch (e: Exception) {
            logger.error("Error getting failed OrderSucceed event by ID: {}", eventId, e)
            call.respond(HttpStatusCode.InternalServerError,  "Failed to get event: ${e.message}")
        }
    }

    /**
     * Retry thủ công một failed event
     */
    suspend fun retryFailedEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["id"] ?: run {
                return call.respond(HttpStatusCode.BadRequest, "Thiếu ID event")
            }
            
            val failedEvent = storage.getById(eventId) ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }

            failedEvent.event ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }
            
            // Kiểm tra trạng thái event
            if (failedEvent.status == FailedEventStatus.DISABLED) {
                return call.respond(HttpStatusCode.BadRequest, "Event đã bị disable, không thể retry")
            }
            
            if (failedEvent.status == FailedEventStatus.PROCESSED) {
                return call.respond(HttpStatusCode.BadRequest, "Event đã được xử lý thành công")
            }
            
            // Đánh dấu event đang retrying
            storage.updateEventStatus(eventId, FailedEventStatus.RETRYING)
            
            try {
                // Xử lý event bằng processor
                processor.process(failedEvent.event)
                
                // Nếu xử lý thành công, đánh dấu là processed
                storage.markAsProcessed(eventId)
                
                logger.info(
                    "Successfully manually retried OrderSucceed event for orderId: {}",
                    failedEvent.event.orderId
                )
                
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Event đã được xử lý thành công",
                    "orderId" to failedEvent.event.orderId,
                    "orderCode" to failedEvent.event.orderCode,
                    "processedAt" to Date().time
                ))
                
            } catch (processingException: Exception) {
                // Nếu xử lý thất bại, cập nhật thông tin retry
                val newRetryCount = failedEvent.retryCount + 1

                storage.updateRetryInfo(
                    eventId,
                    newRetryCount,
                    FailedEventStatus.PENDING,
                    "Manual retry failed: ${processingException.message}"
                )
                
                logger.error(
                    "Manual retry failed for OrderSucceed event orderId: {}",
                    failedEvent.event.orderId, processingException
                )
                
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "message" to "Xử lý event thất bại",
                    "error" to processingException.message,
                    "retryCount" to newRetryCount,
                ))
            }
            
        } catch (e: Exception) {
            logger.error("Error retrying failed OrderSucceed event", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to retry event: ${e.message}")
        }
    }

    /**
     * Disable một failed event để không retry nữa
     */
    suspend fun disableFailedEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["id"] ?: run {
                return call.respond(HttpStatusCode.BadRequest, "Thiếu ID event")
            }
            
            val failedEvent = storage.getById(eventId) ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }

            failedEvent.event ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }
            
            if (failedEvent.status == FailedEventStatus.DISABLED) {
                return call.respond(HttpStatusCode.BadRequest, "Event đã được disable")
            }
            
            if (failedEvent.status == FailedEventStatus.PROCESSED) {
                return call.respond(HttpStatusCode.BadRequest, "Event đã được xử lý thành công, không thể disable")
            }
            
            storage.updateEventStatus(eventId, FailedEventStatus.DISABLED)
            
            logger.info("Disabled failed OrderSucceed event for orderId: {}", failedEvent.event.orderId)
            
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Event đã được disable",
                "orderCode" to failedEvent.event.orderCode,
                "disabledAt" to Date().time
            ))
            
        } catch (e: Exception) {
            logger.error("Error disabling failed OrderSucceed event", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to disable event: ${e.message}")

        }
    }

    /**
     * Enable lại một failed event đã bị disable
     */
    suspend fun enableFailedEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["id"] ?: run {
                return call.respond(HttpStatusCode.BadRequest, "Thiếu ID event")
            }
            
            val failedEvent = storage.getById(eventId) ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }

            failedEvent.event ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }
            
            if (failedEvent.status != FailedEventStatus.DISABLED) {
                return call.respond(HttpStatusCode.BadRequest, "Chỉ có thể enable các event đã bị disable")
            }
            
            storage.updateEventStatus(eventId, FailedEventStatus.PENDING)
            
            logger.info("Enabled failed OrderSucceed event for orderId: {}", failedEvent.event.orderId)
            
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Event đã được enable",
                "orderId" to failedEvent.event.orderId,
                "enabledAt" to Date().time
            ))
            
        } catch (e: Exception) {
            logger.error("Error enabling failed OrderSucceed event", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to enable event: ${e.message}")
        }
    }

    /**
     * Lấy thống kê failed events
     */
    suspend fun getFailedEventsStats(call: ApplicationCall) {
        try {
            val totalCount = storage.countFailedEvents()
            val pendingCount = storage.countFailedEvents(status = FailedEventStatus.PENDING)
            val retryingCount = storage.countFailedEvents(status = FailedEventStatus.RETRYING)
            val processedCount = storage.countFailedEvents(status = FailedEventStatus.PROCESSED)
            val disabledCount = storage.countFailedEvents(status = FailedEventStatus.DISABLED)
            val archivedCount = storage.countFailedEvents(status = FailedEventStatus.ARCHIVED)
            
            val processingErrorCount = storage.countFailedEvents(errorType = "PROCESSING_ERROR")
            val parsingErrorCount = storage.countFailedEvents(errorType = "PARSING_ERROR")
            
            val retryServiceStatus = retryService.getStatus()
            
            val stats = mapOf(
                "total" to totalCount,
                "byStatus" to mapOf(
                    "pending" to pendingCount,
                    "retrying" to retryingCount,
                    "processed" to processedCount,
                    "disabled" to disabledCount,
                    "archived" to archivedCount
                ),
                "byErrorType" to mapOf(
                    "processingError" to processingErrorCount,
                    "parsingError" to parsingErrorCount,
                    "other" to (totalCount - processingErrorCount - parsingErrorCount)
                ),
                "retryService" to retryServiceStatus,
                "timestamp" to Date().time
            )
            
            call.respond(HttpStatusCode.OK, stats)
            
        } catch (e: Exception) {
            logger.error("Error getting failed OrderSucceed events stats", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to get stats: ${e.message}")
        }
    }

    /**
     * Chạy retry service một lần (manual trigger)
     */
    suspend fun runRetryService(call: ApplicationCall) {
        try {
            if (!retryService.isRunning()) {
                return call.respond(HttpStatusCode.BadRequest, "Retry service không đang chạy")
            }
            
            // Chạy retry job một lần
            retryService.runOnce()
            
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "Retry service đã được chạy thủ công",
                "triggeredAt" to Date().time
            ))
            
        } catch (e: Exception) {
            logger.error("Error running retry service manually", e)
            call.respond(HttpStatusCode.InternalServerError, "Failed to run retry service: ${e.message}")
        }
    }
}

/**
 * Extension function để chuyển đổi OrderSucceedEventConsumerDLQDoc thành response format
 */
fun OrderSucceedEventConsumerDLQDoc.toResponse(): Map<String, Any?> {
    return mapOf(
        "id" to this.id,
        "orderCode" to this.event?.orderCode,
        "showId" to this.event?.showId,
        "sessionId" to this.event?.sessionId,
        "ticketInfo" to this.event?.ticketInfo?.map { ticket ->
            mapOf(
                "ticketId" to ticket.ticketId,
                "priceCat" to ticket.priceCat,
                "sid" to ticket.sid,
                "ticketSlots" to ticket.ticketSlots,
            )
        },
        "originalMessage" to this.originalMessage,
        "sourceTopic" to this.sourceTopic,
        "messageKey" to this.messageKey,
        "errorMessage" to this.errorMessage,
        "errorType" to this.errorType,
        "retryCount" to this.retryCount,
        "lastRetryAt" to this.lastRetryAt?.time,
        "status" to this.status.name,
        "createdAt" to this.createdAt.time,
        "updatedAt" to this.updatedAt.time
    )
}