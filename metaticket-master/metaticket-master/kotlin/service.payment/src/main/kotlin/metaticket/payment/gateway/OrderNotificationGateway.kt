package metaticket.payment.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.notification.grpc.OrderMessage
import metaticket.order.notification.grpc.OrderNotificationServiceGrpcKt
import metaticket.payment.datastructure.OrderType
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.pojo.db.PaymentDocument
import kotlin.random.Random

/**
 * Routes payment callbacks to the order-owning service selected by [PaymentDocument.orderType].
 *
 * Routing (Req 8.1, 8.2): the gateway holds one [OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub]
 * per [OrderType] — `TICKET` targets `service.order` (the pre-existing, unchanged behavior) and `ACCESSORY`
 * targets the accessory-side `OrderNotificationService` impl.
 *
 * Default/legacy (Req 8.3, 8.4): [PaymentDocument.orderType] is nullable; a `null`/blank value resolves to
 * `TICKET`, so every pre-existing payment row and every ticket payment continues to route to `service.order`
 * exactly as before.
 *
 * Unknown value (Req 8.5): an `orderType` that is neither `TICKET` nor `ACCESSORY` is delivered to no
 * order-owning service, the payment status is left untouched, and an error indication is recorded.
 *
 * Delivery retry (Req 8.6): a failed status callback is retried up to [MAX_DELIVERY_ATTEMPTS] attempts with a
 * randomized [MIN_RETRY_DELAY_MS]–[MAX_RETRY_DELAY_MS] delay between attempts. The payment status is never
 * mutated by the gateway, so it is preserved across retries; when all attempts are exhausted an error
 * indication is recorded and the failure is propagated so the caller's existing JobRunr-backed retry fallback
 * still engages (ticket behavior unchanged).
 */
class OrderNotificationGateway(
    private val stubs: Map<OrderType, OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub>
): Logging {

    companion object {
        const val MAX_DELIVERY_ATTEMPTS = 5
        const val MIN_RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY_DELAY_MS = 60_000L
    }

    /**
     * Resolves the routing target for a payment.
     *
     * @return the stub to deliver to, or `null` when the [PaymentDocument.orderType] is an unrecognized value
     *         (Req 8.5) or no stub is configured for the resolved type.
     */
    private fun resolveStub(payment: PaymentDocument): OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub? {
        val raw = payment.orderType
        // null/blank/legacy => TICKET (Req 8.3, 8.4).
        val type = if (raw.isNullOrBlank()) {
            OrderType.TICKET
        } else {
            try {
                OrderType.valueOf(raw.trim())
            } catch (e: IllegalArgumentException) {
                // Unrecognized orderType value (Req 8.5): not a valid OrderType.
                null
            }
        }

        if (type == null) {
            logger.error(
                "payment {} has unrecognized orderType '{}'; not delivering callback to any order-owning service",
                payment.id, raw
            )
            return null
        }

        val stub = stubs[type]
        if (stub == null) {
            logger.error(
                "payment {} orderType {} has no configured notification stub; not delivering callback",
                payment.id, type
            )
        } else {
            logger.info(
                "[pay-flow] payment {} orderType raw='{}' resolved={} — routing DIRECT gRPC to {} service",
                payment.id, raw, type, type
            )
        }
        return stub
    }

    /**
     * Notify payment starting and wait for the response before doing anything with the payment.
     * Routed by [PaymentDocument.orderType]; ticket payments target `service.order` exactly as before.
     */
    suspend fun notifyPaymentStarting(payment: PaymentDocument) {
        logger.info(
            "[pay-flow] notifyPaymentStarting ENTER payment {} orderId={} orderType='{}'",
            payment.id, payment.orderId, payment.orderType
        )
        val stub = resolveStub(payment)
        if (stub == null) {
            logger.error(
                "[pay-flow] notifyPaymentStarting payment {} resolved NO stub (orderType='{}') — not notifying start",
                payment.id, payment.orderType
            )
            return
        }
        val request = OrderMessage.NotifyPaymentStartedRequest.newBuilder()
            .setOrderId(payment.orderId)
            .build()
        logger.info("[pay-flow] notifyPaymentStarting payment {} sending DIRECT gRPC to order {}", payment.id, payment.orderId)
        withContext(Dispatchers.IO) { stub.notifyPaymentStarted(request) }
        logger.info("[pay-flow] notifyPaymentStarting payment {} DIRECT gRPC returned for order {}", payment.id, payment.orderId)
    }

    suspend fun notifyPaymentStatusChanged(payment: PaymentDocument, newPaymentStatus: PaymentStatus) {
        logger.info(
            "[pay-flow] notifyPaymentStatusChanged ENTER payment {} orderId={} orderType='{}' newStatus={}",
            payment.id, payment.orderId, payment.orderType, newPaymentStatus
        )
        val stub = resolveStub(payment)
        if (stub == null) {
            // Req 8.5: unknown/unmapped orderType => deliver to no one, retain the payment's current
            // status (the gateway never mutates status), record an error indication.
            logger.error(
                "[pay-flow] payment {} status change {} not delivered: unroutable orderType '{}' (status retained)",
                payment.id, newPaymentStatus, payment.orderType
            )
            return
        }
        deliverWithRetry(stub, payment, newPaymentStatus)
    }

    /**
     * Delivers a status-changed callback with a bounded retry (Req 8.6): up to [MAX_DELIVERY_ATTEMPTS]
     * attempts with a randomized [MIN_RETRY_DELAY_MS]–[MAX_RETRY_DELAY_MS] delay between attempts. The
     * payment status is never mutated here, so it is preserved across retries. If every attempt fails, an
     * error indication is recorded and the last error is rethrown so the caller's existing JobRunr-backed
     * fallback still engages.
     */
    private suspend fun deliverWithRetry(
        stub: OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub,
        payment: PaymentDocument,
        newPaymentStatus: PaymentStatus
    ) {
        val request = OrderMessage.NotifyPaymentStatusChangedRequest.newBuilder()
            .setOrderId(payment.orderId)
            .setPaymentStatus(newPaymentStatus.name)
            .build()

        var lastError: Throwable? = null
        for (attempt in 1..MAX_DELIVERY_ATTEMPTS) {
            try {
                logger.info(
                    "payment {} sending notify payment status changed {} to order {} (attempt {}/{})",
                    payment.id, newPaymentStatus, payment.orderId, attempt, MAX_DELIVERY_ATTEMPTS
                )
                withContext(Dispatchers.IO) { stub.notifyPaymentStatusChanged(request) }
                logger.info(
                    "payment {} sent notify payment status changed {} to order {} on attempt {}",
                    payment.id, newPaymentStatus, payment.orderId, attempt
                )
                return
            } catch (t: Throwable) {
                lastError = t
                logger.error(
                    "payment {} failed to deliver status changed {} to order {} on attempt {}/{}",
                    payment.id, newPaymentStatus, payment.orderId, attempt, MAX_DELIVERY_ATTEMPTS, t
                )
                if (attempt < MAX_DELIVERY_ATTEMPTS) {
                    val delayMs = Random.nextLong(MIN_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS + 1)
                    delay(delayMs)
                }
            }
        }

        // All attempts exhausted (Req 8.6): record an error indication; status is untouched (preserved).
        logger.error(
            "payment {} exhausted all {} delivery attempts for status changed {} to order {}",
            payment.id, MAX_DELIVERY_ATTEMPTS, newPaymentStatus, payment.orderId, lastError
        )
        throw lastError ?: IllegalStateException("payment ${payment.id} delivery failed with no captured error")
    }
}
