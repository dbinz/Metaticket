package metaticket.order.storage

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.order.datastructure.FailedEventStatus
import metaticket.order.pojo.db.FailedOrderSucceedEventDoc
import org.bson.types.ObjectId
import java.time.Instant
import java.util.*

/**
 * MongoDB implementation of FailedOrderSucceedEventStorage
 */
class FailedOrderSucceedEventStorageImpl(
    private val collection: MongoCollection<FailedOrderSucceedEventDoc>
) : FailedOrderSucceedEventStorage {

    override suspend fun store(event: OrderSucceedEvent, failureReason: String): String {
        val doc = FailedOrderSucceedEventDoc(
            event = event,
            failureReason = failureReason,
            retryCount = 0,
            createdAt = Instant.now(),
            lastRetryAt = null,
            nextRetryAt = calculateNextRetryTime(0),
            lastModified = Date(),
            version = 1
        )
        
        withContext(Dispatchers.IO) {
            collection.insertOne(doc)
        }
        return doc.id
    }

    override suspend fun retrieveEventsForRetry(limit: Int): List<FailedOrderSucceedEventDoc> {
        val now = Instant.now()
        return collection.find(
            Filters.and(
                Filters.lte("nextRetryAt", now),
                Filters.lt("retryCount", MAX_RETRY_COUNT),
                Filters.`in`("status", FailedEventStatus.PENDING, FailedEventStatus.RETRYING)
            )
        )
        .sort(Sorts.ascending("nextRetryAt"))
        .limit(limit)
        .toList()
    }

    override suspend fun markAsProcessed(eventId: String) {
        collection.updateOne(
            Filters.eq("_id", ObjectId(eventId)),
            Updates.combine(
                Updates.set("status", FailedEventStatus.PROCESSED),
                Updates.set("lastModified", Date())
            )
        )
    }

    override suspend fun updateRetryInfo(eventId: String, newFailureReason: String, nextRetryAt: Instant) {
        collection.updateOne(
            Filters.eq("_id", ObjectId(eventId)),
            Updates.combine(
                Updates.set("failureReason", newFailureReason),
                Updates.set("lastRetryAt", Instant.now()),
                Updates.set("nextRetryAt", nextRetryAt),
                Updates.set("status", FailedEventStatus.RETRYING),
                Updates.inc("retryCount", 1),
                Updates.set("lastModified", Date()),
                Updates.inc("version", 1)
            )
        )
    }

    override suspend fun cleanupOldEvents(maxAge: Instant, maxRetryCount: Int): Long {
        val result = collection.deleteMany(
            Filters.or(
                Filters.lt("createdAt", maxAge),
                Filters.gte("retryCount", maxRetryCount)
            )
        )
        return result.deletedCount
    }

    override suspend fun clearOldEvents(olderThan: Instant): Long {
        val result = collection.deleteMany(
            Filters.lt("createdAt", olderThan)
        )
        return result.deletedCount
    }

    override suspend fun getFailedEvents(limit: Int): List<FailedOrderSucceedEventDoc> {
        return collection.find()
            .sort(Sorts.descending("createdAt"))
            .limit(limit)
            .toList()
    }

    override suspend fun getEventById(eventId: String): FailedOrderSucceedEventDoc? {
        return collection.find(Filters.eq("_id", ObjectId(eventId)))
            .firstOrNull()
    }

    override suspend fun disableEvent(eventId: String) {
        collection.updateOne(
            Filters.eq("_id", ObjectId(eventId)),
            Updates.combine(
                Updates.set("status", FailedEventStatus.DISABLED),
                Updates.set("lastModified", Date())
            )
        )
    }

    /**
     * Calculate next retry time using exponential backoff
     */
    private fun calculateNextRetryTime(retryCount: Int): Instant {
        val baseDelaySeconds = 30L // 30 seconds base delay
        val maxDelaySeconds = 3600L // 1 hour max delay
        
        val delaySeconds = minOf(
            baseDelaySeconds * (1L shl retryCount), // Exponential backoff: 30s, 60s, 120s, 240s...
            maxDelaySeconds
        )
        
        return Instant.now().plusSeconds(delaySeconds)
    }

    companion object {
        private const val MAX_RETRY_COUNT = 10
    }
}