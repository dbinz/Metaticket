package metaticket.accessory.service

/**
 * Domain-level failures raised by the order-review lookup ([AccessoryOrderService.getOrder]) and the
 * private resend ([AccessoryOrderService.resendConfirmation]) — task 12.4.
 *
 * Each subtype carries the HTTP [status] the controller should surface, mirroring the
 * [CheckoutException] pattern so the wiring layer maps a failure to the exact mandated status:
 * - a malformed/undecodable Encoded_Id or an id that matches no order → `404` (Req 13.4, 15.3),
 * - a resend requested for an order that is not in `PaymentSucceed` → `409` (Req 15.4).
 */
sealed class OrderAccessException(
    val status: Int,
    message: String,
) : RuntimeException(message) {

    /**
     * The requested Encoded_Id is malformed/undecodable or does not match any existing order. Maps
     * to `404` and carries no order data (Req 13.4 for the public order fetch; Req 15.3 for resend).
     */
    class OrderNotFound(message: String = "no order matches the requested identifier") :
        OrderAccessException(404, message)

    /**
     * A resend was requested for an order that is not in `PaymentSucceed` status (Req 15.4). Maps to
     * `409`; no email is dispatched. [currentStatus] is the order's actual status for diagnostics.
     */
    class ResendNotAllowed(val currentStatus: String) :
        OrderAccessException(
            409,
            "order is not in PaymentSucceed status (current=$currentStatus); resend not permitted",
        )

    /**
     * A customer-initiated cancel was requested for an order that is no longer in a cancellable
     * (pre-payment) state — payment is already in-flight (`PaymentProcessing`) or the order has
     * reached a terminal state (`PaymentSucceed` / `PaymentFailed` / `Expired`). Maps to `409`; no
     * state change or reservation release is performed. [currentStatus] is the order's actual status
     * for diagnostics.
     */
    class CancelNotAllowed(val currentStatus: String) :
        OrderAccessException(
            409,
            "order can no longer be cancelled (current=$currentStatus)",
        )

    /**
     * A cancel was requested by a customer who is not the order's owner (object-level authorization
     * failure). Maps to `403`; no state change or reservation release is performed.
     */
    class NotOrderOwner(message: String = "you are not allowed to cancel this order") :
        OrderAccessException(403, message)
}
