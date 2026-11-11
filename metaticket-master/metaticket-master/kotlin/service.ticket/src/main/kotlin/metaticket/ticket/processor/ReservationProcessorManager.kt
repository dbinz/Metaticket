package metaticket.ticket.processor

import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import metaticket.common.logger.Logging
import metaticket.ticket.ReservationExecutionRetryable
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.redis.RedisAsyncClient


/**
 * Manages a pool of [ReservationProcessor] instances, distributing reservation events
 * and handling their lifecycle.
 */
class ReservationProcessorManager(
    private val env : ApplicationEnvironment,
    private val eventDistributor: ReservationEventDistributor,
    private val redisClient: RedisAsyncClient,
    private val ticketDocDB: TicketDocDBService,
    private val ticketCapacityDBS: TicketCapacityDBService,
    private val resExeRetryable: ReservationExecutionRetryable,
    private val showSessionCache: ShowSessionCache,
): Logging {

    private val processors: List<ReservationProcessor>

    init {
        val noProcessor: Int = env.config.property("ticket.reservation.processor.total").getString().toInt()
        processors = (1..noProcessor)
            .map { ReservationProcessor(showSessionCache, redisClient, ticketDocDB, ticketCapacityDBS, resExeRetryable) }
        logger.info("Initialized {} ReservationProcessors.", processors.size)
    }

    /**
     * Starts the ReservationProcessorManager.
     */
    fun start() {
        logger.info("Starting ReservationProcessorManager...")
        processors.forEach { eventDistributor.register(it) }
        eventDistributor.start()
        logger.info("Started ReservationProcessorManager")
    }

    /**
     * Stops the ReservationProcessorManager gracefully.
     */
    suspend fun stop() {
        logger.info("Stopping ReservationProcessorManager...")
        // Call the distributor's stop() (which is suspend) to allow it to wait gracefully
        eventDistributor.stop()
        // Unregister after the distributor has stopped accepting new work and attempts to stop ongoing jobs
        processors.forEach { eventDistributor.unregister(it) }
        logger.info("Stopped ReservationProcessorManager")
    }

    /**
     * Stops the ReservationProcessorManager using runBlocking.
     * Use this function when you need to call stop from a non-suspend context (like a JVM shutdown hook).
     * Be cautious when using runBlocking as it blocks the calling thread.
     */
    fun stopBlocking() = runBlocking { // Example using runBlocking
        logger.info("Stopping ReservationProcessorManager (blocking)...")
        // Call the distributor's stop() (which is suspend) - runBlocking handles the suspension
        eventDistributor.stop()
        // Unregister after the distributor has stopped accepting new work and attempts to stop ongoing jobs
        processors.forEach { eventDistributor.unregister(it) }
        logger.info("Stopped ReservationProcessorManager (blocking)")
    }
}