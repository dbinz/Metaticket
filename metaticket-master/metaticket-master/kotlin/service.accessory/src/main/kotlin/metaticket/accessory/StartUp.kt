package metaticket.accessory

import io.grpc.Server
import io.ktor.events.*
import io.ktor.server.application.*
import kotlinx.coroutines.*
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.database.DiscountDatabaseService
import metaticket.accessory.database.OrderDatabaseService
import metaticket.accessory.service.ExpirySweepService
import metaticket.accessory.service.AccessoryShowOpenService
import metaticket.common.logger.Logging

/**
 * Application lifecycle hook for `service.accessory`.
 *
 * On start it:
 * 1. Eagerly resolves [CatalogDatabaseService], [OrderDatabaseService], and
 *    [DiscountDatabaseService], whose `init` blocks declare the MongoDB indexes (unique
 *    `accessory-show.code`; compound unique `(accessoryShowId, sku)`; unique `accessory-order.code`;
 *    uppercase-canonical unique `accessory-discount-code.code`).
 * 2. Starts the gRPC [server] that hosts the accessory-side `OrderNotificationService` impl —
 *    `service.payment` dials this for `orderType = ACCESSORY` payment callbacks (Req 8.2) — together
 *    with the JobRunr payment-callback fallback executor. The server is stopped on application shutdown.
 * 3. Runs the pre-terminal expiry sweep (Req 11.2) in-process on a periodic background loop, exactly
 *    like `service.order` expires its created orders — the accessory service owns and drives its own
 *    expiry, so nothing is scheduled on `service.jobrunr`. The loop is cancelled on shutdown.
 */
class StartUp(
    private val monitor: Events,
    @Suppress("unused") private val catalogDatabaseService: CatalogDatabaseService,
    @Suppress("unused") private val orderDatabaseService: OrderDatabaseService,
    @Suppress("unused") private val discountDatabaseService: DiscountDatabaseService,
    private val server: Server,
    private val expirySweepService: ExpirySweepService,
    private val expirySweepIntervalSeconds: Long,
    private val showOpenService: AccessoryShowOpenService,
    private val saleOpenSweepIntervalSeconds: Long,
) : Logging {

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun start() {
        logger.info("service.accessory starting...")

        // Start the gRPC server (OrderNotificationService + JobRunr payment-callback executor).
        try {
            server.start()
            logger.info("gRPC server started on port {}", server.port)

            monitor.subscribe(ApplicationStopPreparing) {
                try {
                    server.shutdown()
                    logger.info("gRPC server stopped")
                } catch (t: Throwable) {
                    logger.error("failed to stop gRPC server", t)
                }
            }
        } catch (t: Throwable) {
            logger.error("failed to start gRPC server", t)
            // A missing gRPC server means payment callbacks cannot be served; fail fast so the
            // service is not silently half-up.
            throw t
        }

        // Self-contained periodic pre-terminal expiry sweep (Req 11.2), mirroring service.order's
        // scheduleExpireCreatedOrder: a dedicated single-thread scope runs the sweep every
        // `expirySweepIntervalSeconds`. No service.jobrunr dependency for expiry.
        val expiryDispatcher = newSingleThreadContext("accessoryExpirySweepThread")
        val expiryScope = CoroutineScope(expiryDispatcher + SupervisorJob())
        scheduleExpirySweep(expiryScope)

        val saleOpenDispatcher = newSingleThreadContext("accessorySaleOpenSweepThread")
        val saleOpenScope = CoroutineScope(saleOpenDispatcher + SupervisorJob())
        scheduleSaleOpenSweep(saleOpenScope)

        monitor.subscribe(ApplicationStopPreparing) {
            try {
                expiryScope.cancel()
                expiryDispatcher.close()
                logger.info("accessory expiry sweep stopped")
            } catch (t: Throwable) {
                logger.error("failed to stop accessory expiry sweep", t)
            }
            try {
                saleOpenScope.cancel()
                saleOpenDispatcher.close()
                logger.info("accessory sale-open sweep stopped")
            } catch (t: Throwable) {
                logger.error("failed to stop accessory sale-open sweep", t)
            }
        }

        logger.info("service.accessory started")

        monitor.subscribe(ApplicationStopped) {
            logger.info("service.accessory stopped")
        }
    }

    /**
     * Background loop that runs [ExpirySweepService.sweep] every [expirySweepIntervalSeconds]. A
     * failure in one pass is logged and does not stop the loop; the next tick retries.
     */
    private fun scheduleExpirySweep(scope: CoroutineScope) = scope.launch {
        while (true) {
            runCatching { expirySweepService.sweep() }
                .onFailure { logger.error("accessory expiry sweep run failed ...", it) }
            delay(expirySweepIntervalSeconds * 1000L)
        }
    }

    /**
     * Background loop that CAS-opens Waiting shows whose `saleOpenAt` is due. Covers jobs missed
     * while the service was down or a JobRunr callback failed.
     */
    private fun scheduleSaleOpenSweep(scope: CoroutineScope) = scope.launch {
        while (true) {
            runCatching { showOpenService.sweepDueShows() }
                .onFailure { logger.error("accessory sale-open sweep run failed ...", it) }
            delay(saleOpenSweepIntervalSeconds * 1000L)
        }
    }
}
