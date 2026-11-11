package metaticket.ticketslots.dlq.storage

import com.mongodb.kotlin.client.coroutine.MongoCollection
import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeEvent
import metaticket.ticketslots.pojo.db.QRCodeEventConsumerDLQDoc

/**
 * Implementation of QRCodeEventConsumerDLQStorage for storing failed QR code events
 * that couldn't be processed by the consumer.
 */
class QRCodeEventConsumerDLQStorage(
    override val collection: MongoCollection<QRCodeEventConsumerDLQDoc>
) : BaseDLQStorage<QRCodeEventConsumerDLQDoc, GenerateTicketSlotQRCodeEvent>() {
    override val serviceName = "QRCodeEventConsumer"

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
            val failedEvent = QRCodeEventConsumerDLQDoc(
                event = event,
                originalMessage = originalMessage ?: "",
                sourceTopic = sourceTopic,
                messageKey = messageKey,
                errorMessage = failureReason,
                errorType = errorType,
                status = status,
                ticketSlotId = event?.slotId,
                stateIdx = event?.stateIdx,
            )
            
            collection.insertOne(failedEvent)
            logger.info(
                "Stored failed consumer event for slotId {} from topic {}",
                event?.slotId ?: "unknown", sourceTopic
            )
            return failedEvent.id
        } catch (e: Throwable) {
            logger.error(
                "Failed to store consumer error for slotId {}: {}",
                event?.slotId ?: "unknown", e.message, e
            )
            throw e
        }
    }
}