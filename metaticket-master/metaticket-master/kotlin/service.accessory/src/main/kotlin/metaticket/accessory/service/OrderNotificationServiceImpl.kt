package metaticket.accessory.service

import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.StatusException
import metaticket.common.logger.Logging
import metaticket.order.notification.grpc.OrderMessage
import metaticket.order.notification.grpc.OrderNotificationServiceGrpcKt
import metaticket.payment.datastructure.PaymentStatus

/**
 * Accessory-side gRPC implementation of the **shared** `OrderNotificationService` proto (defined in
 * `service.order`, consumed here via its `grpc` artifact — design
 * "Component: OrderNotificationService (gRPC, accessory-side impl)").
 *
 * `service.payment` dials this impl for payments whose `orderType = ACCESSORY` (Req 8.2), using the
 * exact same proto contract it already uses for `service.order`. This class is therefore the
 * accessory equivalent of `service.order`'s `OrderNotificationServiceImpl`: it receives the payment
 * callbacks and forwards the terminal outcome to [AccessoryOrderService.onPaymentStatusChanged],
 * which owns all state-machine / stock / discount / email side effects.
 *
 * The impl itself is intentionally thin — it only translates the wire contract into the
 * accessory-owned [PaymentStatus] and delegates. Idempotency, guarded transitions, stock decrement,
 * discount revert, and email dispatch all live behind [AccessoryOrderService.onPaymentStatusChanged]
 * (tasks 7.1–7.3).
 *
 * Final gRPC-server registration (adding this service to the accessory gRPC server / Koin) is done
 * separately in task 16.2; this task only provides the impl class.
 */
class OrderNotificationServiceImpl(
    private val orderService: AccessoryOrderService,
) : OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineImplBase(), Logging {

    /**
     * Payment-started callback (Req 8.2), sent synchronously by `service.payment` before it redirects
     * the customer to the gateway. Moves the order to `PaymentProcessing` so the platform TTL sweep
     * stops applying — from here the order settles only by the gateway outcome.
     *
     * When the order is no longer payable (typically the TTL sweep already expired it) this fails
     * with `FAILED_PRECONDITION`; `service.payment` then marks the payment Failed and does not send
     * the customer to the gateway, so nobody can pay for an expired order.
     */
    override suspend fun notifyPaymentStarted(request: OrderMessage.NotifyPaymentStartedRequest): Empty {
        logger.info("[pay-flow] accessory order {} received payment-started callback", request.orderId)
        if (!orderService.onPaymentStarted(request.orderId)) {
            throw StatusException(
                Status.FAILED_PRECONDITION.withDescription("accessory order ${request.orderId} is no longer payable")
            )
        }
        return Empty.getDefaultInstance()
    }

    /**
     * Payment status callback (Req 8.2, 9.1, 10.1). Maps the wire `payment_status` string — emitted
     * by `service.payment` as [metaticket.payment.datastructure.PaymentStatus]`.name` — into the
     * accessory-owned [PaymentStatus] and forwards to
     * [AccessoryOrderService.onPaymentStatusChanged].
     *
     * Only the terminal outcomes the accessory order lifecycle acts on are mapped:
     * `Succeed` / `Failed` / `Cancelled`. Any other payment status (e.g. `Processing`, `Refunded`)
     * is not actionable on the accessory side and is acknowledged as a no-op, leaving the order
     * untouched. The delegate itself is idempotent (guarded compare-and-set transition), so a
     * re-delivered callback is safe.
     */
    override suspend fun notifyPaymentStatusChanged(request: OrderMessage.NotifyPaymentStatusChangedRequest): Empty {
        val orderId = request.orderId
        val wireStatus = request.paymentStatus

        val mapped = when (wireStatus) {
            "Succeed" -> PaymentStatus.Succeed
            "Failed" -> PaymentStatus.Failed
            "Cancelled" -> PaymentStatus.Cancelled
            else -> {
                logger.info(
                    "[pay-flow] DIRECT gRPC accessory order {} received non-actionable payment status '{}' (no-op)",
                    orderId, wireStatus
                )
                return Empty.getDefaultInstance()
            }
        }

        logger.info(
            "[pay-flow] DIRECT gRPC notifyPaymentStatusChanged received: accessory order {} wireStatus='{}' -> {}",
            orderId, wireStatus, mapped,
        )
        orderService.onPaymentStatusChanged(orderId, mapped)
        logger.info("[pay-flow] DIRECT gRPC notifyPaymentStatusChanged done for accessory order {}", orderId)
        return Empty.getDefaultInstance()
    }
}
