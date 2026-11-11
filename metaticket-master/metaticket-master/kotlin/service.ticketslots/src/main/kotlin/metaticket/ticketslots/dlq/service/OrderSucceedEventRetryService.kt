package metaticket.ticketslots.dlq.service

import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.dlq.storage.OrderSucceedEventConsumerDLQStorage
import metaticket.ticketslots.pojo.db.OrderSucceedEventConsumerDLQDoc
import metaticket.ticketslots.processor.OrderSucceedProcessor
import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.ticketslots.dlq.storage.RetryableEventStorage
import java.time.Duration
import java.util.*

/**
 * Service để tự động retry các OrderSucceedEvent bị lỗi
 * Chỉ retry các lỗi PROCESSING_ERROR, các lỗi khác cần manual retry
 */
class OrderSucceedEventRetryService(
    private val storageImpl: OrderSucceedEventConsumerDLQStorage,
    private val processor: OrderSucceedProcessor,
) : BaseRetryService<OrderSucceedEventConsumerDLQDoc, OrderSucceedEvent>() {

    override val serviceName: String = "OrderSucceedEventRetryService"
    override val storage: RetryableEventStorage<OrderSucceedEventConsumerDLQDoc> = storageImpl
    
    // Override configuration nếu cần
    override val retryIntervalMinutes: Long = 5
    override val maxRetryAttempts: Int = 5
    override val maxEventAgeHours: Long = 24
    override val batchSize: Int = 100

    // Implement abstract methods từ BaseRetryService
    override suspend fun getEventsToRetry(batchSize: Int): List<OrderSucceedEventConsumerDLQDoc> {
        // Chỉ retry các PROCESSING_ERROR events
        return storageImpl.getEventsToRetry(
            errorTypes = listOf("PROCESSING_ERROR"),
            limit = batchSize
        )
    }
    
    override suspend fun retryEvent(eventData: OrderSucceedEventConsumerDLQDoc): Boolean {
        return try {
            // Kiểm tra xem event có null không (parsing error)
            if (eventData.event == null) {
                logger.warn(
                    "Cannot retry event with ID {} - event is null (parsing error)",
                    eventData.id
                )
                return false
            }
            
            // Đánh dấu đang xử lý
            storageImpl.updateEventStatus(eventData.id, FailedEventStatus.RETRYING)
            
            // Xử lý event bằng processor
            processor.process(eventData.event)
            true
        } catch (e: Throwable) {
            logger.error(
                "Failed to retry OrderSucceed event for orderCode {}: {}",
                eventData.event?.orderCode ?: "unknown", e.message
            )
            false
        }
    }
    
    
    override fun getEventIdentifier(eventData: OrderSucceedEventConsumerDLQDoc): String {
        return "orderCode ${eventData.event?.orderCode ?: "unknown"}"
    }
    
    override suspend fun cleanupOldEvents() {
        try {
            // Xóa các event đã processed và cũ hơn 7 ngày
            val sevenDaysAgo = Date(Date().time - Duration.ofDays(7).toMillis())
            val deletedCount = storageImpl.deleteProcessedEvents(sevenDaysAgo)
            
            if (deletedCount > 0) {
                logger.info("Deleted {} processed OrderSucceed events older than 7 days", deletedCount)
            }
            
        } catch (e: Throwable) {
            logger.error("Error during cleanup of failed OrderSucceed events", e)
        }
    }
}