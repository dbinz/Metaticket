package metaticket.order.service

import io.ktor.server.config.*
import kotlinx.coroutines.runBlocking
import metaticket.common.logger.Logging
import metaticket.order.kafka.OrderSucceedProducer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Background service to retry failed order succeed events
 */
class OrderSucceedRetryService(
    private val config: ApplicationConfig,
    private val orderSucceedProducer: OrderSucceedProducer,
) : Logging {

    private var retryScheduledExecutor: ScheduledExecutorService? = null
    private var isRunning = false

    /**
     * Start the retry service
     */
    fun start() {
        if (isRunning) {
            logger.warn("OrderSucceedRetryService is already running")
            return
        }

        val intervalMinutes = config.propertyOrNull("kafka.orderSucceedRetry.retryJobIntervalMinutes")?.getString()?.toLongOrNull() ?: 5L

        // Start retry job
        retryScheduledExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "order-succeed-retry-thread").apply {
                isDaemon = true
            }
        }

        retryScheduledExecutor?.scheduleWithFixedDelay(
            {
                try {
                    runBlocking {
                        retryFailedEvents()
                    }
                } catch (exception: Exception) {
                    logger.error("Error in retry job", exception)
                }
            },
            intervalMinutes, // Initial delay
            intervalMinutes, // Period
            TimeUnit.MINUTES
        )
        
        isRunning = true
        logger.info("OrderSucceedRetryService started with interval: $intervalMinutes minutes")
    }

    /**
     * Stop the retry service
     */
    fun stop() {
        if (!isRunning) {
            return
        }

        // Stop retry executor
        retryScheduledExecutor?.shutdown()
        try {
            if (retryScheduledExecutor?.awaitTermination(30, TimeUnit.SECONDS) == false) {
                retryScheduledExecutor?.shutdownNow()
            }
        } catch (exception: InterruptedException) {
            retryScheduledExecutor?.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        isRunning = false
        logger.info("OrderSucceedRetryService stopped")
    }

    /**
     * Retry failed events and cleanup old ones
     */
    private suspend fun retryFailedEvents() {
        logger.debug("Starting retry job for failed order succeed events")
        
        try {
            // Retry failed events
            orderSucceedProducer.retryFailedEvents()
            
            // Cleanup old events
            orderSucceedProducer.cleanupOldEvents()
            
        } catch (exception: Exception) {
            logger.error("Error during retry process", exception)
        }
        
        logger.debug("Completed retry job for failed order succeed events")
    }

    /**
     * Check if the service is running
     */
    fun isRunning(): Boolean = isRunning
}