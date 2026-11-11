package metaticket.ticketslots.dlq.service

import metaticket.ticketslots.dlq.storage.QRCodeEventProducerDLQStorage
import metaticket.ticketslots.dlq.storage.RetryableEventStorage
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeProducer
import metaticket.ticketslots.pojo.db.QRCodeEventProducerDLQDoc
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeEvent
import java.time.Duration
import java.util.*

/**
 * Service để retry các QR code events thất bại từ Producer DLQ
 * Service này quản lý việc retry các events đã thất bại khi gửi lên Kafka
 */
class QRCodeEventProducerRetryService constructor(
    private val qrCodeProducerDLQStorageImpl: QRCodeEventProducerDLQStorage,
    private val qrCodeProducer: GenerateTicketSlotQRCodeProducer,
) : BaseRetryService<QRCodeEventProducerDLQDoc, GenerateTicketSlotQRCodeEvent>() {

    override val serviceName: String = "QRCodeEventProducerRetryService"
    override val storage: RetryableEventStorage<QRCodeEventProducerDLQDoc> = qrCodeProducerDLQStorageImpl
    
    // Override configuration nếu cần
    override val retryIntervalMinutes: Long = 5
    override val maxRetryAttempts: Int = 5
    override val maxEventAgeHours: Long = 24
    override val batchSize: Int = 100
    
    // Triển khai các phương thức trừu tượng từ BaseRetryService
    override suspend fun getEventsToRetry(batchSize: Int): List<QRCodeEventProducerDLQDoc> {
        return qrCodeProducerDLQStorageImpl.getEventsToRetry(batchSize)
    }
    
    override suspend fun retryEvent(eventData: QRCodeEventProducerDLQDoc): Boolean {
        return try {
            qrCodeProducer.sendEvent(eventData.event)
            true
        } catch (e: Throwable) {
            logger.error("Failed to retry QR code producer event for slotId {}: {}",
                eventData.event.slotId, e.message, e)
            false
        }
    }

    override fun getEventIdentifier(eventData: QRCodeEventProducerDLQDoc): String {
        return "slotId: ${eventData.event.slotId}"
    }
    
    override suspend fun cleanupOldEvents() {
        try {
            val cutoffTime = Date(System.currentTimeMillis() - Duration.ofHours(maxEventAgeHours).toMillis())
            qrCodeProducerDLQStorageImpl.deleteProcessedEvents(cutoffTime)
            logger.debug("Cleaned up old QR code producer events")
        } catch (e: Throwable) {
            logger.error("Error cleaning up old QR code producer events", e)
        }
    }
    

}