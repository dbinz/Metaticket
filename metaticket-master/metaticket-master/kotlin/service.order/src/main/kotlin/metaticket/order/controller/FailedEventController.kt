package metaticket.order.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.order.storage.FailedOrderSucceedEventStorage
import metaticket.order.pojo.db.FailedOrderSucceedEventDoc
import metaticket.order.kafka.OrderSucceedProducer
import metaticket.order.datastructure.FailedEventStatus
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Controller để xử lý API endpoints liên quan đến failed order succeed events
 * @author system
 */
class FailedEventController(
    private val failedEventStorage: FailedOrderSucceedEventStorage,
    private val orderSucceedProducer: OrderSucceedProducer,
) : Logging {

    /**
     * Lấy danh sách các failed events với phân trang
     */
    suspend fun getFailedEvents(call: ApplicationCall) {
        try {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            
            // Validate parameters
            if (limit < 1 || limit > 100) {
                return call.respond(HttpStatusCode.BadRequest, "Limit phải từ 1 đến 100")
            }
            
            val events = failedEventStorage.getFailedEvents(limit)
            
            val response = mapOf(
                "events" to events.map { it.toResponse() },
                "total" to events.size
            )
            
            call.respond(HttpStatusCode.OK, response)
            
        } catch (e: Exception) {
            logger.error("Error getting failed events: ", e)
            call.respond(HttpStatusCode.InternalServerError, "Lỗi khi lấy danh sách failed events")
        }
    }
    
    /**
     * Lấy chi tiết một failed event theo ID
     */
    suspend fun getFailedEventById(call: ApplicationCall) {
        try {
            val eventId = call.parameters["id"] ?: run {
                return call.respond(HttpStatusCode.BadRequest, "Thiếu ID event")
            }
            
            val event = failedEventStorage.getEventById(eventId) ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }
            
            call.respond(HttpStatusCode.OK, event.toResponse())
            
        } catch (e: Exception) {
            logger.error("Error getting failed event by ID: ", e)
            call.respond(HttpStatusCode.InternalServerError, "Lỗi khi lấy chi tiết failed event")
        }
    }
    
    /**
     * Retry thủ công một failed event - gửi ngay lên Kafka và đánh dấu đang retrying
     */
    suspend fun retryFailedEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["id"] ?: run {
                return call.respond(HttpStatusCode.BadRequest, "Thiếu ID event")
            }
            
            val failedEvent = failedEventStorage.getEventById(eventId) ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }
            
            // Kiểm tra trạng thái event
            if (failedEvent.status == FailedEventStatus.DISABLED) {
                return call.respond(HttpStatusCode.BadRequest, "Event đã bị disable, không thể retry")
            }
            
            if (failedEvent.status == FailedEventStatus.PROCESSED) {
                return call.respond(HttpStatusCode.BadRequest, "Event đã được xử lý thành công")
            }
            
            // Đánh dấu event đang retrying trước khi gửi
            failedEventStorage.updateRetryInfo(
                eventId = eventId,
                newFailureReason = "Manual retry in progress",
                nextRetryAt = java.time.Instant.now()
            )
            
            try {
                 // Gửi event ngay lên Kafka
                 val metadata = orderSucceedProducer.sendEventDirectly(failedEvent.event)
                
                // Nếu gửi thành công, đánh dấu là processed
                failedEventStorage.markAsProcessed(eventId)
                
                logger.info(
                    "Successfully sent manual retry for order succeed event {}. Topic: {}, Partition: {}, Offset: {}",
                    failedEvent.event.orderCode,
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset()
                )
                
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Event đã được gửi thành công lên Kafka",
                    "orderCode" to failedEvent.event.orderCode,
                    "topic" to metadata.topic(),
                    "partition" to metadata.partition(),
                    "offset" to metadata.offset()
                ))
                
            } catch (kafkaException: Exception) {
                // Nếu gửi thất bại, cập nhật lý do thất bại
                val newRetryCount = failedEvent.retryCount + 1
                val nextRetryTime = calculateNextRetryTime(newRetryCount)
                
                failedEventStorage.updateRetryInfo(
                    eventId = eventId,
                    newFailureReason = "Manual retry failed: ${kafkaException.message}",
                    nextRetryAt = nextRetryTime
                )
                
                logger.error("Manual retry failed for order succeed event ${failedEvent.event.orderCode}", kafkaException)
                
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "message" to "Gửi event thất bại",
                    "error" to kafkaException.message,
                    "nextRetryAt" to nextRetryTime.toString()
                ))
            }
            
        } catch (e: Exception) {
            logger.error("Error retrying failed event: ", e)
            call.respond(HttpStatusCode.InternalServerError, "Lỗi khi retry failed event")
        }
    }
    
    /**
     * Tính toán thời gian retry tiếp theo với exponential backoff
     */
    private fun calculateNextRetryTime(retryCount: Int): java.time.Instant {
        val baseDelayMinutes = 5
        val maxDelayMinutes = 60 * 24 // 24 hours
        val delayMinutes = minOf(baseDelayMinutes * (1 shl (retryCount - 1)), maxDelayMinutes)
        return java.time.Instant.now().plusSeconds(delayMinutes * 60L)
    }
    
    /**
     * Disable một failed event để không retry nữa
     */
    suspend fun disableFailedEvent(call: ApplicationCall) {
        try {
            val eventId = call.parameters["id"] ?: run {
                return call.respond(HttpStatusCode.BadRequest, "Thiếu ID event")
            }
            
            val event = failedEventStorage.getEventById(eventId) ?: run {
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy failed event")
            }
            
            failedEventStorage.disableEvent(eventId)
            
            call.respond(HttpStatusCode.OK, mapOf("message" to "Event đã được disable"))
            
        } catch (e: Exception) {
            logger.error("Error disabling failed event: ", e)
            call.respond(HttpStatusCode.InternalServerError, "Lỗi khi disable failed event")
        }
    }
    
    /**
     * Lấy thống kê failed events
     */
    suspend fun getFailedEventsStats(call: ApplicationCall) {
        try {
            val events = failedEventStorage.getFailedEvents(1000) // Get more events for stats
            val stats = mapOf(
                "total" to events.size,
                "byRetryCount" to events.groupBy { it.retryCount }.mapValues { it.value.size },
                "byStatus" to events.groupBy { it.status }.mapValues { it.value.size },
                "oldestEvent" to events.minByOrNull { it.createdAt }?.createdAt,
                "newestEvent" to events.maxByOrNull { it.createdAt }?.createdAt
            )
            call.respond(HttpStatusCode.OK, stats)
            
        } catch (e: Exception) {
            logger.error("Error getting failed events stats: ", e)
            call.respond(HttpStatusCode.InternalServerError, "Lỗi khi lấy thống kê failed events")
        }
    }
}

/**
 * Extension function để chuyển đổi FailedOrderSucceedEventDoc thành response format
 */
fun FailedOrderSucceedEventDoc.toResponse(): Map<String, Any?> {
    return mapOf(
        "id" to this.id,
        "orderId" to this.event.orderId,
        "orderCode" to this.event.orderCode,
        "eventData" to this.event,
        "failureReason" to this.failureReason,
        "retryCount" to this.retryCount,
        "status" to this.status.name,
        "createdAt" to this.createdAt.toString(),
        "lastRetryAt" to this.lastRetryAt?.toString(),
        "nextRetryAt" to this.nextRetryAt?.toString(),
        "lastModified" to this.lastModified.toString(),
        "version" to this.version
    )
}