package metaticket.ticketslots.dlq.storage

import com.mongodb.kotlin.client.coroutine.MongoCollection
import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeEvent
import metaticket.ticketslots.pojo.db.QRCodeEventProducerDLQDoc

/**
 * Implementation of QRCodeEventProducerDLQStorage for storing failed QR code events
 * that couldn't be sent to Kafka (Producer errors).
 */
class QRCodeEventProducerDLQStorage(
    override val collection: MongoCollection<QRCodeEventProducerDLQDoc>
) : BaseDLQStorage<QRCodeEventProducerDLQDoc, GenerateTicketSlotQRCodeEvent>() {
    override val serviceName = "QRCodeEventProducer"
    
    override suspend fun store(
        failureReason: String,
        errorType: String,
        status: FailedEventStatus,
        sourceTopic: String,
        event: GenerateTicketSlotQRCodeEvent?,
        messageKey: String?,
        originalMessage: String?
    ): String {
        try {
            val failedEvent = QRCodeEventProducerDLQDoc(
                event = event!!,
                messageKey = messageKey,
                errorMessage = failureReason,
                errorType = errorType,
                status = status,
                sourceTopic = sourceTopic,
            )

            collection.insertOne(failedEvent)
            logger.info("Stored failed producer event: {}", event)
            return failedEvent.id
        } catch (e: Throwable) {
            logger.error(
                "Failed to store producer error for slotId {}: {}",
                event?.slotId, e.message, e
            )
            throw e
        }
    }
}