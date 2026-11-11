package metaticket.ticketslots.dlq.storage

import com.mongodb.kotlin.client.coroutine.MongoCollection
import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.pojo.db.OrderSucceedEventConsumerDLQDoc

/**
 * Implementation của FailedOrderSucceedEventStorage sử dụng MongoDB
 * Extends BaseDLQStorage để tránh code trùng lặp
 */
class OrderSucceedEventConsumerDLQStorage(
    override val collection: MongoCollection<OrderSucceedEventConsumerDLQDoc>
) : BaseDLQStorage<OrderSucceedEventConsumerDLQDoc, OrderSucceedEvent>() {
    
    override val serviceName: String = "OrderSucceedEventConsumerDLQStorage"

    override suspend fun store(
        failureReason: String,
        errorType: String,
        status: FailedEventStatus,
        sourceTopic: String,
        event: OrderSucceedEvent?,
        messageKey: String?,
        originalMessage: String?
    ): String {
        val doc = OrderSucceedEventConsumerDLQDoc(
            event = event,
            originalMessage = originalMessage ?: "",
            sourceTopic = sourceTopic,
            messageKey = messageKey,
            errorMessage = failureReason, // Map failureReason to errorMessage field
            errorType = errorType,
            status = status,
            orderCode = event?.orderCode
        )
        
        collection.insertOne(doc)
        logger.info("Stored failed OrderSucceedEvent with ID: ${doc.id}, errorType: $errorType")
        return doc.id
    }
}