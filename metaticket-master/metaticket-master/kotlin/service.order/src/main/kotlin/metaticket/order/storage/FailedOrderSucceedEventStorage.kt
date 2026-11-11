package metaticket.order.storage

import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.order.pojo.db.FailedOrderSucceedEventDoc
import java.time.Instant

/**
 * Storage interface for managing failed order succeed events that need to be retried.
 * This provides a fallback mechanism when Kafka publishing fails.
 */
interface FailedOrderSucceedEventStorage {

    /**
     * Store a failed order succeed event for later retry
     * @param event The original order succeed event that failed to publish
     * @param failureReason Description of why the event failed to publish
     * @return The stored document ID
     */
    suspend fun store(event: OrderSucceedEvent, failureReason: String): String

    /**
     * Retrieve events that are ready for retry based on their nextRetryAt timestamp
     * @param limit Maximum number of events to retrieve
     * @return List of failed events ready for retry
     */
    suspend fun retrieveEventsForRetry(limit: Int = 100): List<FailedOrderSucceedEventDoc>

    /**
     * Mark an event as successfully processed and remove it from storage
     * @param eventId The ID of the event that was successfully processed
     */
    suspend fun markAsProcessed(eventId: String)

    /**
     * Update retry information for a failed event
     * @param eventId The ID of the event
     * @param newFailureReason Updated failure reason
     * @param nextRetryAt When the next retry should be attempted
     */
    suspend fun updateRetryInfo(eventId: String, newFailureReason: String, nextRetryAt: Instant)

    /**
     * Clean up old events that have exceeded maximum retry attempts or age
     * @param maxAge Maximum age of events to keep
     * @param maxRetryCount Maximum retry count before giving up
     * @return Number of events cleaned up
     */
    suspend fun cleanupOldEvents(maxAge: Instant, maxRetryCount: Int): Long

    /**
     * Clear all old events from storage (for maintenance)
     * @param olderThan Only clear events older than this timestamp
     * @return Number of events cleared
     */
    suspend fun clearOldEvents(olderThan: Instant): Long

    /**
     * Get all failed events for monitoring/debugging purposes
     * @param limit Maximum number of events to return
     * @return List of failed events
     */
    suspend fun getFailedEvents(limit: Int = 50): List<FailedOrderSucceedEventDoc>

    /**
     * Get a specific failed event by ID
     * @param eventId The ID of the event to retrieve
     * @return The failed event document or null if not found
     */
    suspend fun getEventById(eventId: String): FailedOrderSucceedEventDoc?

    /**
     * Disable a failed event to prevent further retry attempts
     * @param eventId The ID of the event to disable
     */
    suspend fun disableEvent(eventId: String)
}