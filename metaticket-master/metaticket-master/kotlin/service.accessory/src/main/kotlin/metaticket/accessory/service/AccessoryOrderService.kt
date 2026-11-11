package metaticket.accessory.service

import metaticket.accessory.pojo.request.CartContext
import metaticket.accessory.pojo.request.CheckoutInput
import metaticket.accessory.pojo.request.CheckoutLine
import metaticket.accessory.pojo.request.CheckoutResult
import metaticket.accessory.pojo.response.AccessoryOrderView
import metaticket.accessory.pojo.response.CustomerAccessoryOrderItem
import metaticket.accessory.pojo.response.PendingAccessoryOrderResponse
import metaticket.payment.datastructure.PaymentStatus

/**
 * Owns the accessory order lifecycle (design "Component: AccessoryOrderService" and "Core
 * Interfaces/Types → AccessoryOrderService").
 *
 * The service receives the **entire** frontend cart in the checkout request body, validates and
 * prices it directly against the catalog (existence, active, single-`Open`-show, per-variant stock,
 * catalog-derived unit price — Req 6.3–6.9), computes subtotal/total, atomically reserves any
 * supplied discount, and inserts exactly one order. No cart is persisted server-side.
 *
 * NOTE: this interface declares [checkout] (task 6.2), [onPaymentStatusChanged] (task 7), the
 * order-review lookup [getOrder] and the private [resendConfirmation] (task 12.4).
 */
interface AccessoryOrderService {

    /**
     * Validate and price the submitted cart, then create the order (Req 6).
     *
     * Steps (design "Algorithmic Pseudocode → checkout"):
     * 1. Re-assert the payload is non-empty and every quantity is positive (the pure
     *    [metaticket.accessory.pojo.request.CheckoutValidator] gate runs first, in the controller).
     * 2. Load the referenced accessories and assert they all exist (else `404`, Req 6.3), are
     *    active, and belong to a single `Open` accessory show (else `422`, Req 6.4).
     * 3. Per line, resolve the applicable stock cell (selected variant's stock, else the top-level
     *    stock), assert the variant selection is consistent with the catalog (else `400`, Req 6.5),
     *    and assert the line does not exceed its applicable stock unless unlimited (else `409`,
     *    Req 6.6).
     * 4. Compute `subtotal = Σ catalogPrice * qty` (Req 6.7, 6.9) and
     *    `total = max(0, subtotal - discount)` (Req 6.8), reserving any supplied discount atomically
     *    (Req 6.11; exhausted ⇒ full price, Req 6.12).
     * 5. Insert exactly one [metaticket.accessory.pojo.db.AccessoryOrderDocument] with a unique
     *    order code, `PickupAtShow` fulfillment, and each line snapshotting `variantKey` /
     *    `variantLabel` / `unitPrice` (Req 6.10, 6.15) — **without decrementing any stock**
     *    (Req 6.13).
     *
     * @param customerUserId the authenticated customer's id (gateway `X-Customer-User-Id`), recorded
     *   on the created order for object-level authorization (only the owner may later cancel it).
     * @throws CheckoutException when catalog re-validation fails (see subtypes for the HTTP status).
     */
    suspend fun checkout(input: CheckoutInput, customerUserId: String? = null): CheckoutResult

    /**
     * Re-derive the owning accessory show and the catalog-priced subtotal for a submitted set of
     * cart lines, **without** creating an order, reserving a discount, or touching stock (Req 6.9).
     *
     * This reuses the same catalog re-derivation `checkout` performs (existence ⇒ `404`, single
     * `Open` show / active ⇒ `422`), then computes `subtotal = Σ catalogPrice * quantity`. It powers
     * the `POST /accessory/discount/validate` preview endpoint (task 12.3), which evaluates a
     * discount code against this server-derived subtotal (Req 4.1, 4.3) rather than trusting any
     * client-supplied price.
     *
     * @param lines the submitted cart lines (must be non-empty and reference active accessories of a
     *   single `Open` show).
     * @return the [CartContext] carrying the resolved `showId` and the catalog-derived `subtotal`.
     * @throws CheckoutException when the lines are empty ([CheckoutException.EmptyCart]), reference an
     *   unknown accessory ([CheckoutException.UnknownAccessory] ⇒ `404`), or do not all belong to a
     *   single active `Open` show ([CheckoutException.NotSingleOpenShow] ⇒ `422`).
     */
    suspend fun deriveCartContext(lines: List<CheckoutLine>): CartContext

    /**
     * Handle a payment status callback delivered by `service.payment` (routed here only for
     * `orderType = ACCESSORY`, Req 8.2). Invoked by the accessory-side `OrderNotificationService`
     * gRPC impl (task 10.1) after it maps the wire status into [PaymentStatus].
     *
     * The handler is **idempotent**: it advances the order via the guarded compare-and-set
     * [OrderStateMachine.transition], so a re-delivered callback for an order already in the target
     * (or a conflicting terminal) state is a safe no-op (Req 9.7, 10.3).
     *
     * Success path (this task, 7.1 — design "onPaymentStatusChanged" Succeed branch):
     * 1. Guarded transition from a pre-terminal state (`Created` / `WaitingForPayment` /
     *    `PaymentProcessing`) to `PaymentSucceed` (Req 9.1). A `null` result means the order was
     *    already processed (idempotent no-op, Req 9.7) or sits in a conflicting/non-pre-terminal
     *    state — in the latter case an error indication is recorded and the order is left unchanged
     *    (Req 9.5).
     * 2. On a successful transition, atomically decrement each line's applicable stock (the selected
     *    variant's `variants.$.stock`, otherwise the top-level `stock`) by the line quantity, never
     *    below zero, skipping unlimited cells and recording a `STOCK_SHORTFALL` reconciliation entry
     *    for any cell floored at zero (Req 9.2, 9.3).
     *
     * Fail/cancel path (task 7.2 — design "onPaymentStatusChanged" Failed/Cancelled branch):
     * 1. Guarded transition from a pre-terminal state to `PaymentFailed` (for `Failed`) or
     *    `PaymentCancelled` (for `Cancelled`) (Req 10.1); no stock is changed on this path (Req 10.4).
     *    A `null` result is an idempotent no-op when the order is already in that same terminal state
     *    (Req 10.3), or leaves the order unchanged and records an error indication when it sits in a
     *    different, conflicting terminal state (Req 10.6).
     * 2. On a successful transition, revert any reserved discount **exactly once** — gated by an
     *    atomic `discountReverted` claim — with a retry budget of 3 attempts, recording a
     *    `DISCOUNT_REVERT_FAILED` reconciliation entry (keeping the terminal state) if all attempts
     *    fail (Req 10.2, 10.5).
     *
     * Confirmation-email dispatch on success (task 7.3) is added by its own task.
     *
     * @param orderId the raw accessory order id carried by the payment callback.
     * @param status  the final payment status mapped to the accessory-owned [PaymentStatus].
     */
    suspend fun onPaymentStatusChanged(orderId: String, status: PaymentStatus)

    /**
     * Handle the "payment started" callback `service.payment` sends right before redirecting the
     * customer to the gateway. Moves the order `WaitingForPayment → PaymentProcessing`, after which
     * the platform TTL no longer applies: the order settles only by the gateway outcome (IPN /
     * inquiry) or, if the gateway never reports, by the stale-processing safety net.
     *
     * @return `true` if the customer may proceed to the gateway (transitioned now, or already
     *   `PaymentProcessing` — idempotent); `false` if the order is missing or no longer payable
     *   (e.g. already `Expired`/`PaymentCancelled`), so the caller must refuse the payment start.
     */
    suspend fun onPaymentStarted(orderId: String): Boolean

    /**
     * Expire a single order past its TTL (driven by the expiry sweep). Performs the guarded
     * compare-and-set transition from an expirable state (`Created` / `WaitingForPayment`) to
     * `Expired` and, when it wins the transition, releases the order's reserved stock and reserved
     * discount exactly once (reserve-on-create model — a never-paid reservation must be returned to
     * inventory). Returns `true` iff this call performed the transition.
     *
     * @param order the candidate order loaded by the sweep.
     */
    suspend fun expireOrder(order: metaticket.accessory.pojo.db.AccessoryOrderDocument): Boolean

    /**
     * Safety net for a `PaymentProcessing` order whose gateway never reported an outcome (e.g. the
     * customer closed the gateway page and no IPN/inquiry result ever arrives). Guarded
     * `PaymentProcessing → Expired`, then releases reserved stock + discount exactly once.
     * Returns `true` iff this call performed the transition.
     */
    suspend fun expireStaleProcessingOrder(order: metaticket.accessory.pojo.db.AccessoryOrderDocument): Boolean

    /**
     * Cancel a single order at the customer's request (the "Hủy đơn" action on the payment step),
     * backing `POST /accessory/order/cancel?id=`.
     *
     * The [encodedOrderId] is a `Checksum`-encoded public id: it is decoded and validated via
     * [metaticket.accessory.security.PublicId] before any DB access. A malformed/undecodable id, or a
     * well-formed id that matches no order, is a not-found ([OrderAccessException.OrderNotFound] ⇒ `404`).
     *
     * The cancel performs the guarded compare-and-set transition from a cancellable pre-payment state
     * (`Created` / `WaitingForPayment`, i.e. [EXPIRABLE_STATES]) to `PaymentCancelled` and, when it
     * wins the transition, releases the order's reserved stock and reserved discount exactly once
     * (reserve-on-create model — a never-paid reservation must be returned to inventory), reusing the
     * same guarded release path as expiry / payment-failure.
     *
     * Idempotency & guarding:
     * - an order already in `PaymentCancelled` is a successful no-op (double-click / retry);
     * - an order whose payment is already in-flight (`PaymentProcessing`) or that has reached any
     *   other terminal state (`PaymentSucceed` / `PaymentFailed` / `Expired`) can no longer be
     *   cancelled and is rejected with [OrderAccessException.CancelNotAllowed] ⇒ `409`.
     *
     * Object-level authorization: [customerUserId] (the authenticated caller) must match the order's
     * recorded owner (`AccessoryOrderDocument.customerUserId`); otherwise the cancel is rejected with
     * [OrderAccessException.NotOrderOwner] ⇒ `403` and nothing is changed.
     *
     * @param customerUserId the authenticated customer's id (gateway `X-Customer-User-Id`).
     * @throws OrderAccessException.OrderNotFound    when the id is malformed or matches no order.
     * @throws OrderAccessException.NotOrderOwner    when the caller does not own the order.
     * @throws OrderAccessException.CancelNotAllowed when the order is past the cancellable window.
     */
    suspend fun cancelOrder(encodedOrderId: String, customerUserId: String)

    /**
     * Load a single accessory order for the customer-facing review view (Req 13.3), powering
     * `GET /public/accessory/order/fetch?id=` (the "Hoàn tất" review link).
     *
     * The [encodedOrderId] is a `Checksum`-encoded public id: it is decoded and validated via
     * [metaticket.accessory.security.PublicId] before any DB access. A malformed/undecodable id, or a
     * well-formed id that matches no order, is a not-found ([OrderAccessException.OrderNotFound] ⇒
     * `404`) with no order data returned (Req 13.4). The returned [AccessoryOrderView] carries the
     * order code, status, customer, subtotal/discount/total, the always-`PickupAtShow` fulfillment
     * (no shipping fields — Req 14.5), and each line's composed display string (product name +
     * variant label when present + quantity; variant label omitted for single-SKU — Req 13.3, 13.5).
     *
     * @throws OrderAccessException.OrderNotFound when the id is malformed or matches no order (Req 13.4).
     */
    suspend fun getOrder(encodedOrderId: String): AccessoryOrderView

    /**
     * List [PaymentSucceed] accessory orders owned by [customerUserId] (newest first), powering
     * `GET /accessory/order/fetch/mine` on the storefront "Quản lý đơn hàng" page.
     * Each item carries show branding + full line/fulfillment detail for the detail view.
     */
    suspend fun listMyPaidOrders(customerUserId: String): List<CustomerAccessoryOrderItem>

    /**
     * Newest pre-payment order (`Created` / `WaitingForPayment`) for [customerUserId], or null.
     * Powers `GET /accessory/order/pending` so the product page can resume an unpaid hold.
     */
    suspend fun findPendingOrderForCustomer(customerUserId: String): PendingAccessoryOrderResponse?

    /**
     * Resend the confirmation email for a single order (operational recovery), backing
     * `POST /private/accessory/order/notify/resend?id=` (design "Notification → Resend path", Req 15).
     *
     * The [encodedOrderId] is decoded and validated via [metaticket.accessory.security.PublicId]. A
     * malformed/undecodable id, or an id that matches no order, is a not-found
     * ([OrderAccessException.OrderNotFound] ⇒ `404`) and dispatches no email (Req 15.3). An order that
     * is not in `PaymentSucceed` is rejected ([OrderAccessException.ResendNotAllowed] ⇒ `409`) and
     * dispatches no email (Req 15.4).
     *
     * For a `PaymentSucceed` order it reuses the exact same dispatch path (same plugin, template, and
     * payload builder) as the on-success dispatch: it first releases the `emailDispatched` claim so a
     * fresh claim can proceed, then forces exactly one send (Req 15.1). This is the recovery for the
     * "email failed after success" case (Req 9.6, 15.2).
     *
     * @throws OrderAccessException.OrderNotFound   when the id is malformed or matches no order (Req 15.3).
     * @throws OrderAccessException.ResendNotAllowed when the order is not `PaymentSucceed` (Req 15.4).
     */
    suspend fun resendConfirmation(encodedOrderId: String)
}
