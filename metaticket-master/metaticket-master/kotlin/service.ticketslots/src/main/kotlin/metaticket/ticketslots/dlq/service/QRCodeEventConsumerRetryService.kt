package metaticket.ticketslots.dlq.service

import metaticket.ticketslots.pojo.db.QRCodeEventConsumerDLQDoc
import metaticket.ticketslots.processor.GenerateTicketSlotQRCodeProcessor
import metaticket.ticketslots.dlq.storage.QRCodeEventConsumerDLQStorage
import metaticket.ticketslots.dlq.storage.RetryableEventStorage
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeEvent
import java.time.Duration
import java.util.Date

/**
 * Service để retry các QR code events thất bại từ Consumer DLQ
 * Service này quản lý việc retry các events đã thất bại khi xử lý ở consumer
 */
class QRCodeEventConsumerRetryService constructor(
    private val qrCodeConsumerDLQStorageImpl: QRCodeEventConsumerDLQStorage,
    private val qrCodeProcessor: GenerateTicketSlotQRCodeProcessor,
) : BaseRetryService<QRCodeEventConsumerDLQDoc, GenerateTicketSlotQRCodeEvent>() {
    override val serviceName: String = "QRCodeEventConsumerRetryService"
    override val storage: RetryableEventStorage<QRCodeEventConsumerDLQDoc> = qrCodeConsumerDLQStorageImpl
    
    // Override configuration nếu cần
    override val retryIntervalMinutes: Long = 5
    override val maxRetryAttempts: Int = 5
    override val maxEventAgeHours: Long = 24
    override val batchSize: Int = 100
    
    // Triển khai các phương thức trừu tượng từ BaseRetryService
    override suspend fun getEventsToRetry(batchSize: Int): List<QRCodeEventConsumerDLQDoc> {
        return qrCodeConsumerDLQStorageImpl.getEventsToRetry(batchSize)
    }
    
    override suspend fun retryEvent(eventData: QRCodeEventConsumerDLQDoc): Boolean {
        return try {
            if (eventData.event == null) return false
            qrCodeProcessor.process(eventData.event)
            true
        } catch (e: Throwable) {
            logger.error("Failed to retry QR code consumer event for slotId {}: {}", 
                eventData.event?.slotId, e.message, e)
            false
        }
    }
    

    

    
    override fun getEventIdentifier(eventData: QRCodeEventConsumerDLQDoc): String {
        return "slotId: ${eventData.event?.slotId}"
    }
    
    override suspend fun cleanupOldEvents() {
        try {
            val cutoffTime = Date(System.currentTimeMillis() - Duration.ofHours(maxEventAgeHours).toMillis())
            qrCodeConsumerDLQStorageImpl.deleteProcessedEvents(cutoffTime)
            logger.debug("Cleaned up old QR code consumer events")
        } catch (e: Throwable) {
            logger.error("Error cleaning up old QR code consumer events", e)
        }
    }
}