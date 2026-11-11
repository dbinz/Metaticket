package metaticket.ticketslots.pojo.db

import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.ticketslots.model.FailedEventStatus
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

/**
 * Document để lưu trữ các OrderSucceedEvent bị lỗi trong MongoDB
 */
data class OrderSucceedEventConsumerDLQDoc constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    override val id: String = ObjectId().toHexString(),
    override val errorMessage: String,
    override val errorType: String,
    override val retryCount: Int = 0,
    override val lastRetryAt: Date? = null,
    override val status: FailedEventStatus = FailedEventStatus.PENDING,
    override val createdAt: Date = Date(),
    override val updatedAt: Date = Date(),
    override val sourceTopic: String,

    /**
     * OrderSucceedEvent gốc bị lỗi (null nếu parsing error)
     */
    val event: OrderSucceedEvent?,

    /**
     * Message gốc từ Kafka (trước khi parse)
     */
    val originalMessage: String,

    /**
     * Message key từ Kafka
     */
    val messageKey: String?,

    /**
     * Order code liên quan đến order succeed event
     */
    val orderCode: String? = event?.orderCode
) : RetryableEventDoc {

}