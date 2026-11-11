package metaticket.accessory.service

import metaticket.accessory.database.OrderDatabaseService
import metaticket.common.logger.Logging
import java.util.Date

/**
 * The pre-terminal expiry sweep (Req 11.2 / design "Order State Machine → expiry").
 *
 * On each run it finds every expirable order (`Created` / `WaitingForPayment` — `PaymentProcessing`
 * is excluded so an in-flight payment settles by its real outcome, not by TTL) whose age exceeds the
 * configured TTL (`accessory.orderTtlMinutes`) and expires it via [AccessoryOrderService.expireOrder],
 * which performs the guarded `from = EXPIRABLE_STATES, to = Expired` transition and, on winning it,
 * releases the order's reserved stock + discount (reserve-on-create: a never-paid reservation must be
 * returned to inventory). Using the same compare-and-set guard the payment callbacks
 * use guarantees the sweep can never race a concurrent payment callback into a corrupt state: if an
 * order advances to a terminal state between the query and the transition, the guarded update simply
 * matches nothing and is a no-op (Property 7, terminal monotonicity).
 *
 * This class holds no scheduling concern of its own — it is driven by the accessory service's own
 * in-process periodic background loop (see [metaticket.accessory.StartUp]), exactly like
 * `service.order` drives its created-order expiry. No `service.jobrunr` recurring job is involved.
 *
 * Once the customer is handed to the gateway the order is `PaymentProcessing` and is governed by the
 * gateway's own clock + IPN, not by [orderTtlMinutes]. [paymentProcessingStaleMinutes] is only a
 * safety net for a gateway that never reports (abandoned gateway page): it must stay well above the
 * gateway session length plus service.payment's inquiry window. `0` disables it.
 */
class ExpirySweepService constructor(
    private val orderDatabaseService: OrderDatabaseService,
    private val orderService: AccessoryOrderService,
    private val orderTtlMinutes: Long,
    private val paymentProcessingStaleMinutes: Long,
) : Logging {

    /**
     * Run one sweep pass. Returns the number of orders actually transitioned to `Expired` (orders
     * that lost the compare-and-set race to a payment callback are not counted).
     */
    suspend fun sweep(): Int = sweepUnpaid() + sweepStaleProcessing()

    private suspend fun sweepStaleProcessing(): Int {
        if (paymentProcessingStaleMinutes <= 0) return 0
        val cutoff = Date(System.currentTimeMillis() - paymentProcessingStaleMinutes * 60_000L)
        val candidates = orderDatabaseService.findStaleProcessingOrdersStartedBefore(cutoff)
        var expired = 0
        for (order in candidates) {
            try {
                if (orderService.expireStaleProcessingOrder(order)) expired++
            } catch (t: Throwable) {
                logger.error("stale-processing sweep: failed to expire order {} ...", order.id, t)
            }
        }
        if (candidates.isNotEmpty()) {
            logger.warn(
                "stale-processing sweep: {} PaymentProcessing order(s) without gateway outcome for {} min, {} expired",
                candidates.size, paymentProcessingStaleMinutes, expired,
            )
        }
        return expired
    }

    private suspend fun sweepUnpaid(): Int {
        val cutoff = Date(System.currentTimeMillis() - orderTtlMinutes * 60_000L)
        val candidates = orderDatabaseService.findExpirableOrdersOlderThan(cutoff)

        if (candidates.isEmpty()) {
            logger.debug("expiry sweep: no pre-terminal orders older than {} min", orderTtlMinutes)
            return 0
        }

        var expired = 0
        for (order in candidates) {
            try {
                // Delegate to the order service so the guarded Expired transition AND the
                // reserve-on-create release (return reserved stock + revert discount) happen together.
                val transitioned = orderService.expireOrder(order)
                if (transitioned) {
                    expired++
                    logger.info("expiry sweep: order {} ({}) transitioned to Expired", order.id, order.code)
                } else {
                    logger.debug("expiry sweep: order {} left pre-terminal before expiry (no-op)", order.id)
                }
            } catch (t: Throwable) {
                // one bad order must not abort the whole sweep; the next recurring run retries it.
                logger.error("expiry sweep: failed to expire order {} ...", order.id, t)
            }
        }

        logger.info("expiry sweep: {} candidate(s) past TTL, {} transitioned to Expired", candidates.size, expired)
        return expired
    }
}
