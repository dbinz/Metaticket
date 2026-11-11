package metaticket.order

import io.grpc.Server
import io.ktor.events.*
import io.ktor.server.application.*
import kotlinx.coroutines.*
import metaticket.common.coroutine.mapLaunch
import metaticket.order.configuration.OrderConfig
import metaticket.order.controller.OrderFailedHandler
import metaticket.order.database.service.OrderDBService
import metaticket.order.datastructure.OrderStatus
import metaticket.order.kafka.OrderSucceedProducer
import metaticket.order.service.OrderSucceedRetryService
import java.util.*


/**
 * Handles the startup and shutdown procedures for the order service.
 * This includes starting the gRPC server, managing its lifecycle,
 * and running background tasks like expiring created orders.
 *
 * @author dongcatchay
 */
class StartUp(
    private val monitor: Events,
    private val server: Server,
    private val orderDBS: OrderDBService,
    private val orderConfig: OrderConfig,
    private val orderFailedHandler: OrderFailedHandler,
    private val orderSucceedProducer: OrderSucceedProducer,
    private val orderSucceedRetryService: OrderSucceedRetryService,
) {

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun start() {
        // Start OrderSucceedRetryService
        try {
            orderSucceedRetryService.start()
            logger.info("OrderSucceedRetryService started")
        } catch (e: Throwable) {
            logger.error("Failed to start OrderSucceedRetryService: ", e)
        }

        // Subscribe to the Ktor application stop event to gracefully shut down services
        monitor.subscribe(ApplicationStopPreparing) {
            try {
                orderSucceedRetryService.stop()
                logger.info("OrderSucceedRetryService stopped")
            } catch (e: Throwable) {
                logger.error("Failed to stop OrderSucceedRetryService: ", e)
            }
            
            try {
                orderSucceedProducer.stop()
                logger.info("OrderSucceedProducer stopped")
            } catch (e: Throwable) {
                logger.error("Failed to stop OrderSucceedProducer: ", e)
            }
        }

        try {
            // Start the gRPC server
            server.start()
            logger.info("gRPC server started on port ${server.port}")

            // Subscribe to the Ktor application stop event to gracefully shut down the gRPC server
            monitor.subscribe(ApplicationStopPreparing) {
                try {
                    server.shutdown()
                    logger.info("gRPC server stopped")
                } catch (e: Throwable) {
                    logger.error("Failed to stop gRPC server: ", e)
                }
            }
        } catch (e: Throwable) {
            logger.error("Failed to start gRPC server: ", e)
            // Stop the Application if the gRPC server cannot start
            throw e
        }

        // Create a dedicated single-thread dispatcher for the background task.
        // This dispatcher needs to be closed when no longer in use.
        val expireOrderDispatcher = newSingleThreadContext("scheduleExpireCreatedOrderThread")
        // Use a SupervisorJob so that if a child coroutine within this scope fails,
        // it doesn't cancel other coroutines or the scope itself.
        // Although there's only one main task here, this is good practice.
        val supervisorJob = SupervisorJob()
        // Create a coroutine scope for the background task, combining the dispatcher and the supervisor job.
        val scheduleExpireCreatedOrderScope = CoroutineScope(expireOrderDispatcher + supervisorJob)

        // Launch the background task to schedule expiration of created orders.
        scheduleExpireCreatedOrder(scheduleExpireCreatedOrderScope)

        // Subscribe to the Ktor application stopped event for cleanup.
        monitor.subscribe(ApplicationStopped) {
            logger.info("ApplicationStopped event received. Cleaning up background task for order expiration...")

            // Use runBlocking to execute suspend functions (like cancelAndJoin)
            // synchronously during the Ktor application's shutdown phase.
            // This will block the shutdown thread until cleanup is complete.
            runBlocking {
                try {
                    logger.info("Attempting to cancel and join supervisorJob for order expiration task...")
                    // Cancel the supervisor job and wait for its completion.
                    supervisorJob.cancelAndJoin() // Suspend function call
                    logger.info("SupervisorJob for order expiration task cancelled and joined successfully.")
                } catch (e: CancellationException) {
                    // This can happen if the job was already cancelled or completed.
                    logger.info("CancellationException during supervisorJob.cancelAndJoin() (potentially expected during shutdown): ${e.message}")
                } catch (t: Throwable) {
                    // Log other unexpected errors during job cancellation.
                    logger.error("Error during supervisorJob.cancelAndJoin() for order expiration task: ", t)
                }
            }

            // Close the dispatcher after its jobs have been cancelled.
            // close() is a blocking function, not a suspend function.
            try {
                logger.info("Closing expireOrderDispatcher...")
                expireOrderDispatcher.close() // Release the thread
                logger.info("expireOrderDispatcher closed successfully.")
            } catch (t: Throwable) {
                logger.error("Failed to close expireOrderDispatcher: ", t)
            }
            logger.info("Background task cleanup for order expiration finished.")
        }
    }

    /**
     * Schedules a recurring task to check for and expire orders that have been in the 'Created' state for too long.
     * This function runs in an infinite loop within the provided [scope].
     *
     * @param scope The CoroutineScope in which this background task will run.
     */
    private fun scheduleExpireCreatedOrder(scope: CoroutineScope) = scope.launch {
        // Infinite loop to continuously check for expired orders.
        while (true) {
            kotlin.runCatching {
                // Load orders from the database that are in the 'Created' status.
                orderDBS.loadOrderByStatus(listOf(OrderStatus.Created)).mapLaunch { order -> // Process each order concurrently
                    kotlin.runCatching {
                        // Check if the order has expired based on its last modified time and configured expiration duration.
                        if (Date().time - order.lastModified.time > orderConfig.expireInMinute * 60000L) {
                            // If expired, call the handler to process the expired order.
                            orderFailedHandler.onOrderExpired(order.reservationId, order.id)
                        }
                    }.onFailure { t ->
                        logger.error("order {} fail to expire... ", order, t)
                    }
                }.joinAll() // Wait for all concurrent order processing tasks to complete.
            }.onFailure {
                logger.error("fail to load created order from db when schedule to expire... ", it)
            }

            // Wait for the configured scan interval before checking again.
            delay(orderConfig.scanExpireInMinute * 60000L)
        }
    }
}