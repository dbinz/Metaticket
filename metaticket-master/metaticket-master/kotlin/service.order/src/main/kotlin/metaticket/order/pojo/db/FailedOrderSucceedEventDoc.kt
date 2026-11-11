package metaticket.order.pojo.db

import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.order.datastructure.FailedEventStatus
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.time.Instant
import java.util.Date

/**
 * Document representing a failed order succeed event stored in MongoDB.
 * This is used for retry mechanism when Kafka publishing fails.
 */
data class FailedOrderSucceedEventDoc constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),

    val event: OrderSucceedEvent,
    val failureReason: String,
    val retryCount: Int = 0,
    val status: FailedEventStatus = FailedEventStatus.PENDING,
    val createdAt: Instant = Instant.now(),
    val lastRetryAt: Instant? = null,
    val nextRetryAt: Instant? = null,

    // Additional metadata for better tracking
    val lastModified: Date = Date(),
    val version: Int = 1
)