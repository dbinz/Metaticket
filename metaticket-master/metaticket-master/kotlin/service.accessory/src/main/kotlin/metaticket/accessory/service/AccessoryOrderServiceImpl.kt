package metaticket.accessory.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.database.CatalogDatabaseService.StockReserveOutcome
import metaticket.accessory.database.OrderDatabaseService
import metaticket.accessory.database.ReconciliationDatabaseService
import metaticket.accessory.gateway.AccessoryMailGateway
import metaticket.accessory.gateway.JobrunrGateway
import metaticket.accessory.gateway.PaymentGateway
import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryOrderDeliveryAddress
import metaticket.accessory.pojo.db.AccessoryOrderDiscount
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderLine
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.AccessoryShowStatus
import metaticket.accessory.pojo.db.FulfillmentType
import metaticket.accessory.pojo.db.ReconKind
import metaticket.accessory.pojo.db.UNLIMITED_STOCK
import metaticket.accessory.pojo.db.validate
import metaticket.accessory.pojo.request.CartContext
import metaticket.accessory.pojo.request.CheckoutInput
import metaticket.accessory.pojo.request.CheckoutLine
import metaticket.accessory.pojo.request.CheckoutResult
import metaticket.accessory.pojo.request.CheckoutValidator
import metaticket.accessory.pojo.request.DELIVERY
import metaticket.accessory.pojo.request.META_TICKET_PICKUP
import metaticket.accessory.pojo.request.PICKUP_AT_SHOW
import metaticket.accessory.pojo.response.AccessoryOrderView
import metaticket.accessory.pojo.response.CustomerAccessoryOrderItem
import metaticket.accessory.pojo.response.PendingAccessoryOrderResponse
import metaticket.accessory.pojo.response.toCustomerOrderItem
import metaticket.accessory.pojo.response.toOrderView
import metaticket.accessory.security.PublicId
import metaticket.common.logger.Logging
import metaticket.payment.datastructure.PaymentStatus
import java.time.LocalDate
import kotlin.math.max

/**
 * Default [AccessoryOrderService] implementation (task 6.2 — checkout pricing/creation).
 *
 * This implements the design "Algorithmic Pseudocode → checkout" **up to and including the single
 * order insert**: it re-validates the submitted cart against the catalog, prices it from the stored
 * catalog, atomically reserves any applicable discount, and inserts exactly one
 * [AccessoryOrderDocument] with a unique code, `PickupAtShow` fulfillment, and snapshotted line
 * fields — **without decrementing any stock** (Req 6.13). The order is inserted in the `Created`
 * state.
 *
 * **Payment wiring (task 6.3).** After the order is inserted in `Created`, checkout creates a
 * payment in `service.payment` (`orderType = ACCESSORY`) within a 30s deadline (Req 7.1, 7.2). On
 * success it persists the returned `paymentId`, transitions `Created → WaitingForPayment` via
 * [OrderStateMachine], and returns the `Checksum`-encoded payment/order ids (Req 7.3). On an
 * error/timeout/no-id it moves the order to the `PaymentFailed` terminal state via the guarded
 * transition, reverts any reserved discount exactly once, and surfaces a `502`
 * ([CheckoutException.PaymentCreationFailed], Req 7.4). When the clamped total is `0` (e.g. a 100%
 * discount), checkout skips `createPayment`, transitions `Created → PaymentSucceed`, dispatches the
 * confirmation email, and returns an empty `encodedPaymentId` so the customer UI can jump to Hoàn tất.
 *
 * DI note: dependencies are constructor-injected; Koin wiring is deferred to task 16.2.
 *
 * @param catalogDb loads accessories/shows for catalog re-validation and pricing.
 * @param orderDb inserts the created order and persists the payment id.
 * @param discountService evaluates and atomically reserves a supplied discount code.
 * @param orderCodeService allocates a unique order code (with duplicate-key retry) around the insert.
 * @param paymentGateway creates the payment in `service.payment` with `orderType = ACCESSORY`.
 * @param orderStateMachine performs the guarded `Created → WaitingForPayment` / `→ PaymentFailed` transitions.
 * @param reconciliationDb records out-of-band anomalies (e.g. `STOCK_SHORTFALL`) off the customer path.
 * @param mailGateway sends the confirmation email via the dedicated `SendMailAccessoryOrder` RPC (task 7.3).
 * @param jobrunrGateway durable fallback: when the direct send is exhausted it schedules a
 *   `ResendMailAccessoryOrder` job on `service.jobrunr` so `service.notification` keeps retrying the
 *   send out-of-process (mirrors `service.order`).
 * @param emailDispatchScope background scope the confirmation-email dispatch runs on so it never
 *   blocks the payment callback ack (design "Notification → Dispatch"); overridable for tests.
 */
class AccessoryOrderServiceImpl(
    private val catalogDb: CatalogDatabaseService,
    private val orderDb: OrderDatabaseService,
    private val discountService: DiscountCodeService,
    private val orderCodeService: OrderCodeService,
    private val paymentGateway: PaymentGateway,
    private val orderStateMachine: OrderStateMachine,
    private val reconciliationDb: ReconciliationDatabaseService,
    private val mailGateway: AccessoryMailGateway,
    private val jobrunrGateway: JobrunrGateway,
    private val emailDispatchScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** Payment hold TTL used for `expiresAt` on pending-order lookup (matches expiry sweep). */
    private val orderTtlMinutes: Long = 5L,
) : AccessoryOrderService, Logging {

    companion object {
        /** Payment-creation deadline: the payment id must arrive within 30 seconds (Req 7.2, 7.4). */
        const val PAYMENT_CREATE_TIMEOUT_MS = 30_000L

        /** Max attempts to revert a reserved discount on fail/cancel before reconciliation (Req 10.5). */
        const val DISCOUNT_REVERT_MAX_ATTEMPTS = 3

        /** Base backoff between discount-revert attempts (linear: attempt × base). */
        const val DISCOUNT_REVERT_RETRY_DELAY_MS = 200L

        /** Max attempts to dispatch the confirmation email before reconciliation (Req 9.6). */
        const val EMAIL_DISPATCH_MAX_ATTEMPTS = 3

        /** Base backoff between confirmation-email dispatch attempts (linear: attempt × base). */
        const val EMAIL_DISPATCH_RETRY_DELAY_MS = 500L

        /**
         * Pure: resolve a submitted [line] against its already-loaded catalog accessory [acc] into a
         * priced [AccessoryOrderLine].
         *
         * This is the extracted, side-effect-free core of `checkout`'s per-line loop (design
         * "Algorithmic Pseudocode → checkout" step 3). It enforces:
         * - variant-selection consistency with the catalog (Req 6.5): a has-variants accessory must
         *   carry a `variantKey` that matches an existing variant, and a single-SKU accessory must
         *   carry none — otherwise [CheckoutException.InvalidVariant] (400);
         * - variant availability (Req 6.4 parity): the resolved variant must be `active`, otherwise
         *   [CheckoutException.InactiveVariant] (422) — an inactive combination is withdrawn from sale;
         * - the applicable-stock gate (Req 6.6): a non-unlimited applicable stock cell must cover the
         *   line quantity — otherwise [CheckoutException.OutOfStock] (409);
         * - catalog-derived pricing (Req 6.9): the snapshotted `unitPrice` is always the stored
         *   catalog price, never a client-supplied value (the payload carries no price at all).
         *
         * Extracted so the pricing / variant / stock core is unit- and property-testable without a
         * live catalog DB (Properties 3, 9, 12). Behaviour is identical to the previous inline loop.
         */
        fun priceLine(line: CheckoutLine, acc: AccessoryDocument): AccessoryOrderLine {
            val applicableStock: Int
            val variantKey: String?
            val variantLabel: String?
            if (acc.hasVariants) {
                if (line.variantKey == null) {
                    throw CheckoutException.InvalidVariant(
                        line.accessoryId, "accessory has variants but no variantKey was supplied",
                    )
                }
                val variant = acc.variants.find { it.key == line.variantKey }
                    ?: throw CheckoutException.InvalidVariant(
                        line.accessoryId, "variantKey '${line.variantKey}' does not match any variant",
                    )
                // The selected combination must be active (Req 6.4 parity): an inactive variant is
                // withdrawn from sale and rejected before any order/stock/discount change.
                if (!variant.active) {
                    throw CheckoutException.InactiveVariant(line.accessoryId, variant.key)
                }
                applicableStock = variant.stock
                variantKey = variant.key
                variantLabel = variant.label
            } else {
                if (line.variantKey != null) {
                    throw CheckoutException.InvalidVariant(
                        line.accessoryId, "single-SKU accessory must not carry a variantKey",
                    )
                }
                applicableStock = acc.stock
                variantKey = null
                variantLabel = null
            }

            // Applicable-stock gate (Req 6.6 → 409); unlimited stock never blocks.
            if (applicableStock != UNLIMITED_STOCK && line.quantity > applicableStock) {
                val suffix = variantKey?.let { " ($it)" } ?: ""
                throw CheckoutException.OutOfStock("${acc.sku}$suffix")
            }

            // Catalog-derived unit price (Req 6.9) — never a client-supplied price.
            return AccessoryOrderLine(
                accessoryId = acc.id,
                sku = acc.sku,
                name = acc.name,
                variantKey = variantKey,
                variantLabel = variantLabel,
                unitPrice = acc.price,
                quantity = line.quantity,
            )
        }

        /**
         * Pure: enforce the applicable-stock limit against the **aggregate** quantity requested for
         * each stock cell across all submitted lines (Req 6.6).
         *
         * The per-line gate in [priceLine] only compares a single line's quantity to the cell's stock.
         * But the same purchasable unit can appear in more than one cart line — the same single-SKU
         * accessory, or the same variant selected twice — and the
         * limit governs the *total* units ordered for that cell, not each line in isolation. Without
         * this aggregation two lines of 6 against a stock of 10 would each pass while the combined 12
         * silently overflows the limit (and would later floor at zero on the success-time decrement).
         *
         * The stock cell is the accessory itself for a single-SKU product (`variantKey == null`) and
         * the `(accessory, variantKey)` pair for a variant product. Unlimited cells ([UNLIMITED_STOCK])
         * never block. A cell whose combined demand exceeds a non-unlimited stock is rejected with
         * [CheckoutException.OutOfStock] (409).
         */
        fun validateAggregateStock(
            orderLines: List<AccessoryOrderLine>,
            byRawId: Map<String, AccessoryDocument>,
        ) {
            orderLines
                .groupBy { it.accessoryId to it.variantKey }
                .forEach { (cell, cellLines) ->
                    val (accessoryId, variantKey) = cell
                    val acc = byRawId[accessoryId] ?: return@forEach
                    val applicableStock = if (variantKey != null) {
                        acc.variants.find { it.key == variantKey }?.stock ?: return@forEach
                    } else {
                        acc.stock
                    }
                    if (applicableStock == UNLIMITED_STOCK) return@forEach

                    val totalQty = cellLines.sumOf { it.quantity }
                    if (totalQty > applicableStock) {
                        val suffix = variantKey?.let { " ($it)" } ?: ""
                        throw CheckoutException.OutOfStock("${acc.sku}$suffix")
                    }
                }
        }

        /** Pure: order subtotal as `Σ line.unitPrice * line.quantity` (Req 6.7). */
        fun computeSubtotal(lines: List<AccessoryOrderLine>): Long =
            lines.sumOf { it.unitPrice * it.quantity }

        /** Pure: order total as `max(0, subtotal - discountAmount)` (Req 6.8). */
        fun computeTotal(subtotal: Long, discountAmount: Long): Long =
            max(0L, subtotal - discountAmount)
    }

    override suspend fun checkout(input: CheckoutInput, customerUserId: String?): CheckoutResult {
        // 1. Pure payload gate first (Req 5, 6.1, 6.2, 6.14, 16.3). The controller (task 12.3) also
        //    runs this with the raw-body context (shipping fields / fulfillment); re-running here
        //    with defaults keeps the service correct when called directly. Idempotent + cheap.
        CheckoutValidator.validateOrThrow(input, fulfillment = input.fulfillment)
        if (input.lines.isEmpty()) throw CheckoutException.EmptyCart() // defensive re-assert (Req 6.2)

        // 2. Resolve + load the referenced accessories, then re-validate existence / active /
        //    single-Open-show (Req 6.3, 6.4).
        val rawIdByEncoded: Map<String, String> = input.lines
            .map { it.accessoryId }
            .distinct()
            .associateWith { encoded ->
                // The payload gate already guaranteed each id decodes; treat a decode miss here as
                // an unknown-accessory to stay safe rather than throwing an opaque NPE.
                PublicId.decode(encoded) ?: throw CheckoutException.UnknownAccessory(listOf(encoded))
            }

        val accessories = catalogDb.loadAccessoriesByIds(rawIdByEncoded.values)
        val byRawId: Map<String, AccessoryDocument> = accessories.associateBy { it.id }

        // Existence (Req 6.3 → 404): any submitted id with no matching row is unknown.
        val unknown = rawIdByEncoded.filterValues { rawId -> !byRawId.containsKey(rawId) }.keys
        if (unknown.isNotEmpty()) throw CheckoutException.UnknownAccessory(unknown.toList())

        // Active (Req 6.4 → 422): every referenced accessory must be active.
        val inactive = accessories.filterNot { it.active }
        if (inactive.isNotEmpty()) {
            throw CheckoutException.NotSingleOpenShow(
                "một số phụ kiện trong giỏ hàng không khả dụng",
                inactive.map { PublicId.encode(it.id) },
            )
        }

        // Single show (Req 6.4 → 422): all lines must belong to exactly one accessory show.
        val showRawIds = accessories.map { it.accessoryShowId }.distinct()
        if (showRawIds.size != 1) {
            throw CheckoutException.NotSingleOpenShow("lines span ${showRawIds.size} accessory shows")
        }
        val showRawId = showRawIds.first()

        // Owning show must exist and be Open (Req 6.4 → 422). Waiting/Closed cannot be purchased.
        val show = catalogDb.loadShowById(showRawId)
            ?: throw CheckoutException.NotSingleOpenShow("owning accessory show not found")
        requireShowOnSale(show)

        // Resolve fulfillment against the show's enabled modes + mode-specific payload.
        val fulfillmentSettings = show.resolvedFulfillment()
        val fulfillmentRaw = input.fulfillment?.trim()?.takeIf { it.isNotBlank() } ?: PICKUP_AT_SHOW
        val fulfillmentType = when (fulfillmentRaw) {
            PICKUP_AT_SHOW -> FulfillmentType.PickupAtShow
            META_TICKET_PICKUP -> FulfillmentType.MetaTicketPickup
            DELIVERY -> FulfillmentType.Delivery
            else -> throw CheckoutException.InvalidFulfillment(fulfillmentRaw)
        }

        var orderSessionId: String? = null
        var orderSessionLabel: String? = null
        var orderPickupPointId: String? = null
        var orderPickupPointName: String? = null
        var orderPickupPointAddress: String? = null
        var orderDeliveryAddress: AccessoryOrderDeliveryAddress? = null

        when (fulfillmentType) {
            FulfillmentType.PickupAtShow -> {
                if (!fulfillmentSettings.enablePickupAtShow) {
                    throw CheckoutException.FulfillmentNotEnabled(PICKUP_AT_SHOW)
                }
                // Session-selection: when the show has sessions, require a match; optionally
                // constrain to pickupAtShowSessionIds when that list is non-empty.
                if (show.sessions.isNotEmpty()) {
                    val allowedIds = fulfillmentSettings.pickupAtShowSessionIds
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                    val selectable = if (allowedIds.isEmpty()) {
                        show.sessions
                    } else {
                        show.sessions.filter { it.sessionId() in allowedIds }
                    }
                    val requested = input.showSessionId?.trim()?.takeIf { it.isNotBlank() }
                    val session = selectable.firstOrNull { it.sessionId() == requested }
                        ?: throw CheckoutException.InvalidShowSession(input.showSessionId)
                    orderSessionId = session.sessionId()
                    orderSessionLabel = session.label()
                }
            }
            FulfillmentType.MetaTicketPickup -> {
                if (!fulfillmentSettings.enableMetaTicketPickup) {
                    throw CheckoutException.FulfillmentNotEnabled(META_TICKET_PICKUP)
                }
                val requestedPointId = input.pickupPointId?.trim()?.takeIf { it.isNotBlank() }
                    ?: throw CheckoutException.InvalidPickupPoint(input.pickupPointId)
                val point = fulfillmentSettings.pickupPoints.firstOrNull { it.id == requestedPointId }
                    ?: throw CheckoutException.InvalidPickupPoint(requestedPointId)
                orderPickupPointId = point.id
                orderPickupPointName = point.name
                orderPickupPointAddress = point.address
            }
            FulfillmentType.Delivery -> {
                if (!fulfillmentSettings.enableDelivery) {
                    throw CheckoutException.FulfillmentNotEnabled(DELIVERY)
                }
                val addr = input.deliveryAddress
                    ?: throw CheckoutException.InvalidDeliveryAddress("missing deliveryAddress")
                val address = addr.address?.trim().orEmpty()
                val ward = addr.ward?.trim().orEmpty()
                val city = addr.city?.trim().orEmpty()
                if (address.isEmpty() || ward.isEmpty() || city.isEmpty()) {
                    throw CheckoutException.InvalidDeliveryAddress(
                        "city, ward, and address are required",
                    )
                }
                orderDeliveryAddress = AccessoryOrderDeliveryAddress(
                    address = address,
                    ward = ward,
                    city = city,
                    note = addr.note?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        }

        // 3. Per-line variant resolution + applicable-stock check + catalog-derived pricing
        //    (Req 6.5, 6.6, 6.7, 6.9), delegated to the pure [priceLine] core. The subtotal is then
        //    Σ unitPrice*qty over the priced lines ([computeSubtotal]).
        val orderLines = input.lines.map { line ->
            val rawId = rawIdByEncoded.getValue(line.accessoryId)
            priceLine(line, byRawId.getValue(rawId))
        }

        // Aggregate applicable-stock gate across lines (Req 6.6): the same accessory / variant may be
        // split across several cart lines, so the limit must be checked against the combined demand
        // per stock cell — the per-line check in priceLine is not sufficient on its own.
        validateAggregateStock(orderLines, byRawId)
        validateProductQuantityLimits(orderLines, byRawId, customerUserId)

        val subtotal = computeSubtotal(orderLines)

        // 3b. Reserve inventory ATOMICALLY before creating the order/payment (reserve-on-create —
        //     overselling fix). The guarded per-cell decrement is the serialization point: two
        //     concurrent checkouts for the last units cannot both succeed. A shortfall rolls back any
        //     partial reservation and rejects with 409 (no order created, no discount reserved). This
        //     supersedes the old "decrement only on payment success" model, which allowed two
        //     long-running payment sessions to both settle against the same stock.
        reserveStockOrThrow(orderLines)

        // 4. Discount: typed code only. When the customer sent a code it MUST still apply and
        //    reserve — never silently fall through to full price (preview/checkout consistency).
        var discount: AccessoryOrderDiscount? = null
        val code = input.discountCode
        if (!code.isNullOrBlank()) {
            val evaluated = discountService.evaluate(code, showRawId, subtotal)
            if (evaluated == null) {
                releaseReservedStock(orderLines)
                throw CheckoutException.DiscountNotApplicable(code.trim())
            }
            if (!discountService.reserve(evaluated.discountCodeId)) {
                releaseReservedStock(orderLines)
                throw CheckoutException.DiscountExhausted(evaluated.code)
            }
            discount = evaluated
        }

        // Order total: max(0, subtotal - discount) (Req 6.8). Amounts are whole VND already.
        // A clamped zero total (e.g. 100% discount) is a valid free order — settled without payment.
        val total = computeTotal(subtotal, discount?.amount ?: 0L)

        // 5. Insert exactly one order with a unique code (Req 6.10, 6.15), resolved fulfillment,
        //    and snapshotted lines. Stock is already reserved (step 3b); it is released again only if
        //    the order reaches a non-success terminal state. The order starts in Created; the
        //    WaitingForPayment transition happens after payment creation (task 6.3), or for zero-total
        //    orders we settle Created → PaymentSucceed immediately (no payment session).
        val year = LocalDate.now().year
        val order = try {
            orderCodeService.allocateWithRetry(show.codePrefix, year) { allocatedCode ->
                val doc = AccessoryOrderDocument(
                    accessoryShowId = showRawId,
                    code = allocatedCode,
                    status = AccessoryOrderStatus.Created,
                    fulfillment = fulfillmentType,
                    showSessionId = orderSessionId,
                    showSessionLabel = orderSessionLabel,
                    pickupPointId = orderPickupPointId,
                    pickupPointName = orderPickupPointName,
                    pickupPointAddress = orderPickupPointAddress,
                    deliveryAddress = orderDeliveryAddress,
                    lines = orderLines,
                    subtotal = subtotal,
                    discount = discount,
                    total = total,
                    customerName = input.customerName.trim(),
                    customerPhone = input.customerPhone.trim(),
                    customerEmail = input.customerEmail.trim(),
                    customerUserId = customerUserId, // owner for later cancel authorization
                ).validate() // asserts subtotal/total invariants, fulfillment fields, paired variants
                orderDb.insertOrder(doc)
            }
        } catch (t: Throwable) {
            // The order never persisted: release the stock we reserved and any discount so neither
            // inventory nor the usage count is leaked (checkout postcondition: full rollback on a
            // pre-return failure).
            releaseReservedStock(orderLines)
            discount?.let { discountService.revert(it.discountCodeId) }
            throw t
        }

        logger.info(
            "created accessory order code={} show={} subtotal={} total={} discount={} lines={}",
            order.code, showRawId, subtotal, total, discount?.amount ?: 0L, order.lines.size,
        )

        // 6a. Zero-pay: no payment gateway session. Stock stays reserved (confirmed sale), settle
        //     Created → PaymentSucceed, dispatch confirmation email like handleSucceed, return empty
        //     encodedPaymentId so the FE skips Thanh toán → Hoàn tất.
        if (total == 0L) {
            val settled = orderStateMachine.transition(
                order.id,
                setOf(AccessoryOrderStatus.Created),
                AccessoryOrderStatus.PaymentSucceed,
            )
            if (settled == null) {
                logger.error("zero-pay settle failed for order {} — unexpected state after insert", order.code)
                failOrder(order, "zero-pay settle failed")
                throw CheckoutException.PaymentCreationFailed("zero-pay settle failed")
            }
            emailDispatchScope.launch {
                try {
                    dispatchConfirmationEmail(settled)
                } catch (t: Throwable) {
                    logger.error("unexpected error dispatching confirmation email for zero-pay order {}", settled.code, t)
                }
            }
            logger.info("accessory order code={} settled PaymentSucceed (zero total, no payment)", order.code)
            return CheckoutResult(
                encodedPaymentId = "",
                encodedOrderId = PublicId.encode(order.id),
                orderCode = order.code,
                total = 0L,
            )
        }

        // 6b. Payment creation (design "checkout ALGORITHM → TRY createPayment / CATCH").
        //    createPayment(orderType = ACCESSORY) must return a non-blank payment id within 30s
        //    (Req 7.1, 7.2). Any error / timeout / missing id is a payment-creation failure (Req 7.4).
        val paymentId: String = try {
            val id = withTimeout(PAYMENT_CREATE_TIMEOUT_MS) {
                paymentGateway.createPayment(
                    orderId = order.id,
                    orderCode = order.code,
                    amount = total,
                    pgAccountCode = show.pgAccountCode,
                    // Forward the show's allowed payment methods (empty => gateway default set).
                    paymentMethods = show.allowedPaymentMethods,
                    // orderType defaults to PaymentGateway.ORDER_TYPE_ACCESSORY (Req 7.1, 18.4)
                )
            }
            if (id.isBlank()) throw CheckoutException.PaymentCreationFailed("payment service returned no payment id")
            id
        } catch (ce: CheckoutException) {
            // The no-id case: fall through to the shared failure handling below.
            failOrder(order, ce.message)
            throw ce
        } catch (t: TimeoutCancellationException) {
            // No response within the 30s deadline (Req 7.4).
            failOrder(order, "payment creation timed out after ${PAYMENT_CREATE_TIMEOUT_MS}ms")
            throw CheckoutException.PaymentCreationFailed("timed out after ${PAYMENT_CREATE_TIMEOUT_MS}ms")
        } catch (t: Throwable) {
            // gRPC error / any other failure talking to service.payment (Req 7.4).
            failOrder(order, t.message ?: t.javaClass.simpleName)
            throw CheckoutException.PaymentCreationFailed(t.message ?: t.javaClass.simpleName)
        }

        // 7. Success: persist the payment id and transition Created → WaitingForPayment via the
        //    guarded state machine (Req 7.2), then return the encoded payment/order ids (Req 7.3).
        orderDb.setPaymentId(order.id, paymentId)
        orderStateMachine.transition(
            order.id,
            setOf(AccessoryOrderStatus.Created),
            AccessoryOrderStatus.WaitingForPayment,
        )

        logger.info("accessory order code={} moved to WaitingForPayment with payment id set", order.code)

        return CheckoutResult(
            encodedPaymentId = PublicId.encode(paymentId),
            encodedOrderId = PublicId.encode(order.id),
            orderCode = order.code,
            total = total,
        )
    }

    /**
     * Enforces product-level purchase quantity limits for the submitted cart.
     * Limits apply to the **sum of all lines for the same accessory** (any variants).
     * Per-user cap counts Paid + Created/WaitingForPayment/PaymentProcessing plus this cart.
     */
    private suspend fun validateProductQuantityLimits(
        orderLines: List<AccessoryOrderLine>,
        byRawId: Map<String, AccessoryDocument>,
        customerUserId: String?,
    ) {
        val qtyByAccessory = orderLines.groupBy { it.accessoryId }.mapValues { (_, lines) ->
            lines.sumOf { it.quantity }
        }
        val needingUserCap = qtyByAccessory.keys.filter { rawId ->
            (byRawId[rawId]?.maxPerUserQty ?: 0) > 0
        }
        val priorByAccessory = if (!customerUserId.isNullOrBlank() && needingUserCap.isNotEmpty()) {
            orderDb.sumCountedQtyByAccessoryForCustomer(customerUserId, needingUserCap)
        } else {
            emptyMap()
        }

        for ((rawId, orderQty) in qtyByAccessory) {
            val acc = byRawId[rawId] ?: continue
            val min = acc.minOrderQty
            val max = acc.maxOrderQty
            val perUser = acc.maxPerUserQty
            if (min != null && orderQty < min) {
                throw CheckoutException.ProductQuantityLimit(
                    "Sản phẩm '${acc.name}' yêu cầu mua tối thiểu $min mỗi đơn (hiện $orderQty).",
                )
            }
            if (max != null && orderQty > max) {
                throw CheckoutException.ProductQuantityLimit(
                    "Sản phẩm '${acc.name}' chỉ được mua tối đa $max mỗi đơn (hiện $orderQty).",
                )
            }
            if (perUser != null) {
                val prior = priorByAccessory[rawId] ?: 0
                val total = prior + orderQty
                if (total > perUser) {
                    val remaining = (perUser - prior).coerceAtLeast(0)
                    throw CheckoutException.ProductQuantityLimit(
                        "Sản phẩm '${acc.name}' chỉ được mua tối đa $perUser / người" +
                            if (remaining > 0) " (còn có thể mua thêm $remaining)."
                            else " (bạn đã đạt giới hạn).",
                    )
                }
            }
        }
    }

    /**
     * Atomically reserve inventory for every priced line (reserve-on-create). Reserves each line's
     * applicable cell (variant stock, else the accessory's top-level stock); an unlimited cell is
     * skipped. On the first cell that cannot cover its quantity ([StockReserveOutcome.INSUFFICIENT]),
     * every already-reserved line is released again and the checkout is rejected with
     * [CheckoutException.OutOfStock] (409) — so a losing concurrent buyer never partially holds stock.
     */
    private suspend fun reserveStockOrThrow(orderLines: List<AccessoryOrderLine>) {
        val reserved = mutableListOf<AccessoryOrderLine>()
        for (line in orderLines) {
            val outcome = if (line.variantKey != null) {
                catalogDb.reserveVariantStock(line.accessoryId, line.variantKey, line.quantity)
            } else {
                catalogDb.reserveStock(line.accessoryId, line.quantity)
            }
            when (outcome) {
                StockReserveOutcome.RESERVED -> reserved += line
                StockReserveOutcome.UNLIMITED_SKIPPED -> Unit // unlimited cell: nothing held
                StockReserveOutcome.INSUFFICIENT -> {
                    releaseReservedStock(reserved) // roll back the partial reservation
                    val suffix = line.variantKey?.let { " ($it)" } ?: ""
                    throw CheckoutException.OutOfStock("${line.sku}$suffix")
                }
            }
        }
    }

    /**
     * Directly release (add back) the reserved units of the given lines. Used only for pre-insert
     * rollbacks in [checkout] (the order does not exist yet, so no exactly-once claim applies).
     * Unlimited cells are untouched by the underlying guarded update.
     */
    private suspend fun releaseReservedStock(lines: List<AccessoryOrderLine>) {
        for (line in lines) {
            if (line.variantKey != null) {
                catalogDb.releaseVariantStock(line.accessoryId, line.variantKey, line.quantity)
            } else {
                catalogDb.releaseStock(line.accessoryId, line.quantity)
            }
        }
    }

    /**
     * Release a persisted order's reservation exactly once on a non-success terminal outcome:
     * return its reserved stock to inventory and revert any reserved discount. Both halves are
     * guarded by atomic per-order claims ([OrderDatabaseService.tryClaimStockRelease] /
     * [OrderDatabaseService.tryClaimRevert]) so concurrent or re-delivered fail/cancel/expiry paths
     * can never double-release inventory or double-revert a discount code.
     */
    private suspend fun releaseReservation(order: AccessoryOrderDocument) {
        if (orderDb.tryClaimStockRelease(order.id)) {
            releaseReservedStock(order.lines)
            logger.info("released reserved stock for order {} ({} line(s))", order.code, order.lines.size)
        } else {
            logger.info("stock release for order {} already claimed — skipping", order.code)
        }

        val discount = order.discount
        if (discount != null && orderDb.tryClaimRevert(order.id)) {
            revertDiscountWithRetry(order, discount)
        }
    }

    override suspend fun expireOrder(order: AccessoryOrderDocument): Boolean {
        val updated = orderStateMachine.transition(order.id, EXPIRABLE_STATES, AccessoryOrderStatus.Expired)
            ?: return false
        logger.info("expiry: order {} transitioned to Expired; releasing reservation", updated.code)
        releaseReservation(updated)
        return true
    }

    override suspend fun expireStaleProcessingOrder(order: AccessoryOrderDocument): Boolean {
        val updated = orderStateMachine.transition(
            order.id,
            setOf(AccessoryOrderStatus.PaymentProcessing),
            AccessoryOrderStatus.Expired,
        ) ?: return false
        // Warn: reaching this means the gateway never sent a final outcome for this payment. A late
        // Succeed after this point is still recorded as UNROUTABLE_CALLBACK for manual handling.
        logger.warn(
            "stale-processing: order {} (payment {}) had no gateway outcome since {} — Expired; releasing reservation",
            updated.code, updated.paymentId, updated.paymentStartedAt,
        )
        releaseReservation(updated)
        return true
    }

    override suspend fun onPaymentStarted(orderId: String): Boolean {
        val updated = orderStateMachine.markPaymentProcessing(orderId)
        if (updated != null) {
            logger.info(
                "[pay-flow] accessory order {} moved to PaymentProcessing — now settled by the gateway outcome only",
                updated.code,
            )
            return true
        }
        val current = orderDb.loadOrder(orderId)
        return when (current?.status) {
            null -> {
                logger.warn("[pay-flow] payment start for unknown accessory order {} — rejecting", orderId)
                false
            }
            // Duplicate delivery of the same start callback: the order is already handed to the gateway.
            AccessoryOrderStatus.PaymentProcessing -> true
            else -> {
                // Typically the TTL sweep won the race (Expired) or the customer cancelled: refusing
                // here stops service.payment from sending the customer to pay for a dead order.
                logger.warn(
                    "[pay-flow] payment start for accessory order {} rejected — order is {}",
                    current.code, current.status,
                )
                false
            }
        }
    }

    /** See [AccessoryOrderService.cancelOrder]. Reuses the guarded transition + release path of expiry. */
    override suspend fun cancelOrder(encodedOrderId: String, customerUserId: String) {
        val rawId = PublicId.decode(encodedOrderId) ?: throw OrderAccessException.OrderNotFound()
        val order = orderDb.loadOrder(rawId) ?: throw OrderAccessException.OrderNotFound()

        // Object-level authorization: only the customer who placed the order may cancel it. Legacy
        // orders without a recorded owner (customerUserId == null) can never be cancelled here.
        if (order.customerUserId == null || order.customerUserId != customerUserId) {
            logger.warn(
                "cancel denied: customer {} is not the owner of order {} (owner={})",
                customerUserId, order.code, order.customerUserId,
            )
            throw OrderAccessException.NotOrderOwner()
        }

        // Idempotent: an order already cancelled is a successful no-op (double-click / retry).
        if (order.status == AccessoryOrderStatus.PaymentCancelled) {
            logger.info("cancel: order {} already PaymentCancelled — no-op", order.code)
            return
        }

        // Guarded compare-and-set from the cancellable pre-payment window only. A null result means
        // the order left that window (payment in-flight or another terminal state) → 409.
        val updated = orderStateMachine.transition(rawId, EXPIRABLE_STATES, AccessoryOrderStatus.PaymentCancelled)
        if (updated == null) {
            val current = orderDb.loadOrder(rawId)?.status?.name ?: order.status.name
            throw OrderAccessException.CancelNotAllowed(current)
        }
        logger.info("cancel: order {} transitioned to PaymentCancelled by customer; releasing reservation", updated.code)
        releaseReservation(updated)
    }

    /**
     * Re-derive the owning show + catalog-priced subtotal for a set of cart lines (Req 6.9), reusing
     * the same catalog re-validation `checkout` performs. See [AccessoryOrderService.deriveCartContext].
     *
     * This intentionally mirrors `checkout` steps 1–2 (existence / active / single-`Open`-show) and
     * the catalog-derived pricing, but performs **no** variant-selection, stock, discount, or
     * order-creation work: the discount preview only needs a trustworthy server-computed subtotal
     * and the owning show for scope checks. It never mutates anything.
     */
    override suspend fun deriveCartContext(lines: List<CheckoutLine>): CartContext {
        if (lines.isEmpty()) throw CheckoutException.EmptyCart() // Req 6.2 (preview: caller maps empty → 422)

        // Resolve + load the referenced accessories (same as checkout step 2).
        val rawIdByEncoded: Map<String, String> = lines
            .map { it.accessoryId }
            .distinct()
            .associateWith { encoded ->
                PublicId.decode(encoded) ?: throw CheckoutException.UnknownAccessory(listOf(encoded))
            }

        val accessories = catalogDb.loadAccessoriesByIds(rawIdByEncoded.values)
        val byRawId: Map<String, AccessoryDocument> = accessories.associateBy { it.id }

        // Existence (Req 6.3 → 404).
        val unknown = rawIdByEncoded.filterValues { rawId -> !byRawId.containsKey(rawId) }.keys
        if (unknown.isNotEmpty()) throw CheckoutException.UnknownAccessory(unknown.toList())

        // Active (Req 6.4 → 422).
        val inactive = accessories.filterNot { it.active }
        if (inactive.isNotEmpty()) {
            throw CheckoutException.NotSingleOpenShow(
                "một số sản phẩm trong giỏ hàng không khả dụng",
                inactive.map { PublicId.encode(it.id) },
            )
        }

        // Single, Open owning show (Req 6.4 → 422).
        val showRawIds = accessories.map { it.accessoryShowId }.distinct()
        if (showRawIds.size != 1) {
            throw CheckoutException.NotSingleOpenShow("lines span ${showRawIds.size} accessory shows")
        }
        val showRawId = showRawIds.first()
        val show = catalogDb.loadShowById(showRawId)
            ?: throw CheckoutException.NotSingleOpenShow("owning accessory show not found")
        requireShowOnSale(show)

        // Catalog-derived subtotal: Σ catalogPrice * qty (Req 6.7, 6.9). Variant/stock are irrelevant
        // to pricing (price is per-accessory), so they are not checked on this preview path.
        var subtotal = 0L
        for (line in lines) {
            val acc = byRawId.getValue(rawIdByEncoded.getValue(line.accessoryId))
            subtotal += acc.price * line.quantity
        }

        return CartContext(showId = showRawId, subtotal = subtotal)
    }

    /**
     * Handle a payment status callback (design "Algorithmic Pseudocode → onPaymentStatusChanged").
     *
     * The `Succeed` branch is task 7.1; the `Failed`/`Cancelled` branch is task 7.2 (below).
     * Confirmation-email dispatch on success is task 7.3 (see [dispatchConfirmationEmail]).
     *
     * Success flow (Req 9.1, 9.2, 9.3, 9.5, 9.7):
     * 1. Load the order; a missing order records an error indication and returns (nothing to do).
     * 2. Guarded compare-and-set transition `PRE_TERMINAL → PaymentSucceed`. A `null` result is
     *    either an idempotent no-op (the order is already `PaymentSucceed`, Req 9.7) or a
     *    conflicting/non-pre-terminal state, in which case an error indication is recorded and the
     *    order is left unchanged (Req 9.5). Either way no stock is touched.
     * 3. On a successful transition, atomically decrement each line's applicable stock cell (variant
     *    `variants.$.stock`, else top-level `stock`) by the line quantity, never below zero, skipping
     *    unlimited cells and recording a `STOCK_SHORTFALL` reconciliation entry for any floored cell
     *    (Req 9.2, 9.3). The guarded per-cell decrement means a stock cell is decremented at most
     *    once per successful transition (Property 6).
     */
    override suspend fun onPaymentStatusChanged(orderId: String, status: PaymentStatus) {
        logger.info("[pay-flow] onPaymentStatusChanged ENTER orderId={} status={}", orderId, status)
        val order = orderDb.loadOrder(orderId)
        if (order == null) {
            // Payment routed a callback for an order that does not exist here. Record and stop; there
            // is nothing to transition or decrement.
            logger.warn("[pay-flow] onPaymentStatusChanged no accessory order found for id={} (status={})", orderId, status)
            reconciliationDb.record(
                ReconKind.UNROUTABLE_CALLBACK,
                "onPaymentStatusChanged: no accessory order for id=$orderId (status=$status)",
                orderId,
            )
            return
        }

        logger.info(
            "[pay-flow] onPaymentStatusChanged loaded order code={} currentStatus={} -> incoming status={}",
            order.code, order.status, status,
        )
        when (status) {
            PaymentStatus.Succeed -> handleSucceed(order)

            // Fail/cancel handling (Req 10): guarded transition to the matching terminal state,
            // exactly-once discount revert, and NO stock change.
            PaymentStatus.Failed, PaymentStatus.Cancelled -> handleFailOrCancel(order, status)

            // Non-actionable statuses. The non-terminal ones (Created/Starting/Processing) are
            // normal progress notifications the accessory lifecycle does not react to, and Refunded
            // is not part of the accessory flow. These are already filtered at the wire boundary
            // (OrderNotificationServiceImpl), so reaching here is unexpected — log and no-op,
            // leaving the order untouched.
            PaymentStatus.Created,
            PaymentStatus.Starting,
            PaymentStatus.Processing,
            PaymentStatus.Refunded -> logger.info(
                "[pay-flow] onPaymentStatusChanged non-actionable status={} for order {} — no-op",
                status, order.code,
            )
        }
    }

    /**
     * Order-review lookup (task 12.4, Req 13.3, 13.4, 13.5, 14.5). See [AccessoryOrderService.getOrder].
     *
     * Decodes + validates the public id first; a malformed id or an id that matches no order is a
     * `404` [OrderAccessException.OrderNotFound] with no order data (Req 13.4). The mapped
     * [AccessoryOrderView] carries the always-`PickupAtShow` fulfillment (no shipping fields, Req 14.5)
     * and each line's composed display string (variant label omitted for single-SKU, Req 13.5).
     */
    override suspend fun getOrder(encodedOrderId: String): AccessoryOrderView {
        val rawId = PublicId.decode(encodedOrderId) ?: throw OrderAccessException.OrderNotFound() // Req 13.4
        val order = orderDb.loadOrder(rawId) ?: throw OrderAccessException.OrderNotFound()        // Req 13.4
        // PickupAtShow summary prefers the show's pickupNote when still available.
        val showPickupNote = catalogDb.loadShowById(order.accessoryShowId)?.pickupNote
        return order.toOrderView(showPickupNote)
    }

    /**
     * Customer "Quản lý đơn hàng": paid orders only, enriched with show name/banner.
     * Missing/deleted shows fall back to a placeholder name so the list still renders.
     */
    override suspend fun listMyPaidOrders(customerUserId: String): List<CustomerAccessoryOrderItem> {
        val docs = orderDb.findPaidOrdersByCustomerUserId(customerUserId)
        if (docs.isEmpty()) return emptyList()
        val showCache = mutableMapOf<String, AccessoryShowDocument?>()
        return docs.map { order ->
            val show = showCache.getOrPut(order.accessoryShowId) {
                catalogDb.loadShowById(order.accessoryShowId)
            }
            order.toCustomerOrderItem(
                showName = show?.name?.takeIf { it.isNotBlank() } ?: "Sự kiện bán hàng",
                bannerImageUrl = show?.bannerImageUrl,
                showPickupNote = show?.pickupNote,
            )
        }
    }

    /**
     * Newest Created/WaitingForPayment order for the customer — resume payload for the product page.
     * Encodes public ids; [PendingAccessoryOrderResponse.expiresAt] mirrors the TTL sweep deadline.
     */
    override suspend fun findPendingOrderForCustomer(customerUserId: String): PendingAccessoryOrderResponse? {
        val order = orderDb.findNewestPendingOrderByCustomerUserId(customerUserId) ?: return null
        val show = catalogDb.loadShowById(order.accessoryShowId)
        return PendingAccessoryOrderResponse(
            orderId = PublicId.encode(order.id),
            paymentId = order.paymentId?.let { PublicId.encode(it) },
            orderCode = order.code,
            status = order.status.name,
            showId = PublicId.encode(order.accessoryShowId),
            showName = show?.name?.takeIf { it.isNotBlank() } ?: "Sự kiện bán hàng",
            total = order.total,
            expiresAt = order.createdAt.time + orderTtlMinutes * 60_000L,
            customerName = order.customerName,
            customerPhone = order.customerPhone,
            customerEmail = order.customerEmail,
        )
    }

    /**
     * Private resend (task 12.4, Req 15) — design "Notification → Resend path".
     * See [AccessoryOrderService.resendConfirmation].
     *
     * Decodes the id (malformed/none ⇒ `404`, Req 15.3); rejects a non-`PaymentSucceed` order with
     * `409` (Req 15.4); otherwise releases the `emailDispatched` claim and forces exactly one send via
     * the shared [dispatchConfirmationEmail] path (Req 15.1) — the same plugin/template/payload builder
     * as the on-success dispatch, no divergent code path.
     */
    override suspend fun resendConfirmation(encodedOrderId: String) {
        val rawId = PublicId.decode(encodedOrderId) ?: throw OrderAccessException.OrderNotFound() // Req 15.3
        val order = orderDb.loadOrder(rawId) ?: throw OrderAccessException.OrderNotFound()        // Req 15.3
        if (order.status != AccessoryOrderStatus.PaymentSucceed) {
            throw OrderAccessException.ResendNotAllowed(order.status.name)                        // Req 15.4
        }

        // Release the exactly-once dispatch claim so a fresh claim can proceed, then force one send
        // through the shared dispatch path (Req 15.1). dispatchConfirmationEmail re-claims the slot,
        // sends via the dedicated SendMailAccessoryOrder RPC with its bounded retry, and — on
        // exhaustion — keeps the order PaymentSucceed and resend-eligible (Req 9.6, 15.2).
        orderDb.resetEmailDispatchClaim(order.id)
        dispatchConfirmationEmail(order)
        logger.info("resend confirmation email dispatched for order {} (Req 15.1)", order.code)
    }

    /**
     * Fail/cancel branch of [onPaymentStatusChanged] (Req 10.1–10.6).
     *
     * Mirrors the design "onPaymentStatusChanged" Failed/Cancelled branch:
     * 1. Map the payment status to its terminal order state (`Failed → PaymentFailed`,
     *    `Cancelled → PaymentCancelled`) and perform the guarded compare-and-set transition from a
     *    pre-terminal state (Req 10.1). No stock is touched on this path (Req 10.4).
     * 2. A `null` transition result means the guard matched nothing. If the order is *already* in the
     *    same terminal state this is an idempotent duplicate delivery → no-op success, leaving state,
     *    discount reservations, and stock unchanged (Req 10.3). If the order sits in a *different*,
     *    conflicting terminal state, leave it unchanged and record an error indication (Req 10.6).
     * 3. On a successful transition, if the order reserved a discount, revert it **exactly once**,
     *    gated by [OrderDatabaseService.tryClaimRevert] (atomic `discountReverted` false→true flip),
     *    then decrement the code's usage with a retry budget of [DISCOUNT_REVERT_MAX_ATTEMPTS]; on
     *    final failure keep the terminal state and record a `DISCOUNT_REVERT_FAILED` reconciliation
     *    entry (Req 10.2, 10.5).
     */
    private suspend fun handleFailOrCancel(order: AccessoryOrderDocument, status: PaymentStatus) {
        val target = when (status) {
            PaymentStatus.Failed -> AccessoryOrderStatus.PaymentFailed
            PaymentStatus.Cancelled -> AccessoryOrderStatus.PaymentCancelled
            // Every other status is not actionable on this branch: Succeed is dispatched to
            // handleSucceed, and the non-terminal (Created/Starting/Processing) and Refunded
            // statuses are filtered out by onPaymentStatusChanged. Guard defensively and no-op.
            else -> return
        }

        // 1. Guarded, idempotent transition PRE_TERMINAL → target (Req 10.1). No stock change (Req 10.4).
        val updated = orderStateMachine.transition(order.id, PRE_TERMINAL, target)
        if (updated == null) {
            // The guard matched nothing: either a duplicate delivery for the same terminal state
            // (idempotent no-op, Req 10.3) or a conflicting terminal state (Req 10.6).
            val current = orderDb.loadOrder(order.id)?.status
            if (current == target) {
                logger.info(
                    "onPaymentStatusChanged: {} for order {} already applied — no-op (Req 10.3)",
                    status, order.code,
                )
            } else {
                logger.warn(
                    "onPaymentStatusChanged: {} for order {} ignored — order is in conflicting terminal state {} (Req 10.6)",
                    status, order.code, current,
                )
                reconciliationDb.record(
                    ReconKind.UNROUTABLE_CALLBACK,
                    "$status callback for order ${order.code} ignored; conflicting terminal state=$current (Req 10.6)",
                    order.id,
                )
            }
            return
        }

        logger.info("onPaymentStatusChanged: order {} transitioned to {}; releasing reservation", order.code, target)

        // 2. Release the reservation exactly once: return the reserved stock to inventory (Req 10.4
        //    under reserve-on-create — the order held units from creation) and revert any reserved
        //    discount (Req 10.2). Both halves are guarded by atomic per-order claims.
        releaseReservation(updated)
    }

    /**
     * Revert a reserved discount with a bounded retry budget (Req 10.5).
     *
     * Called only by the single caller that won the [OrderDatabaseService.tryClaimRevert] claim, so
     * the code is decremented at most once per order. On success the code's usage count is restored;
     * on exhausting [DISCOUNT_REVERT_MAX_ATTEMPTS] the terminal order state is preserved (never
     * rolled back) and a `DISCOUNT_REVERT_FAILED` reconciliation entry plus an error log record the
     * anomaly for out-of-band repair.
     */
    private suspend fun revertDiscountWithRetry(order: AccessoryOrderDocument, discount: AccessoryOrderDiscount) {
        var lastError: Throwable? = null
        for (attempt in 1..DISCOUNT_REVERT_MAX_ATTEMPTS) {
            try {
                discountService.revert(discount.discountCodeId)
                logger.info(
                    "reverted discount {} for order {} on attempt {} (Req 10.2)",
                    discount.code, order.code, attempt,
                )
                return
            } catch (t: Throwable) {
                lastError = t
                logger.warn(
                    "discount revert attempt {}/{} failed for order {} (code={})",
                    attempt, DISCOUNT_REVERT_MAX_ATTEMPTS, order.code, discount.code, t,
                )
                if (attempt < DISCOUNT_REVERT_MAX_ATTEMPTS) delay(DISCOUNT_REVERT_RETRY_DELAY_MS * attempt)
            }
        }

        // Final failure (Req 10.5): keep the terminal order state, record reconciliation + error.
        logger.error(
            "discount revert failed after {} attempts for order {} (code={}); recording reconciliation",
            DISCOUNT_REVERT_MAX_ATTEMPTS, order.code, discount.code, lastError,
        )
        reconciliationDb.record(
            ReconKind.DISCOUNT_REVERT_FAILED,
            "discount revert failed after $DISCOUNT_REVERT_MAX_ATTEMPTS attempts for order ${order.code}; " +
                "code=${discount.code} discountCodeId=${discount.discountCodeId} lastError=${lastError?.message}",
            order.id,
        )
    }

    /**
     * Success branch of [onPaymentStatusChanged] (Req 9.1–9.3, 9.5, 9.7).
     *
     * The guarded transition is the single idempotency point: only the first `Succeed` callback that
     * finds the order in a pre-terminal state confirms the sale and dispatches the email; every
     * repeat is a no-op. Stock is NOT changed here — it was reserved at order creation
     * (reserve-on-create) and a successful payment simply keeps it held.
     */
    private suspend fun handleSucceed(order: AccessoryOrderDocument) {
        // 1. Guarded, idempotent transition PRE_TERMINAL → PaymentSucceed (Req 9.1, 9.7).
        val updated = orderStateMachine.transition(order.id, PRE_TERMINAL, AccessoryOrderStatus.PaymentSucceed)
        if (updated == null) {
            // Either already PaymentSucceed (idempotent no-op, Req 9.7) or a conflicting/terminal
            // state (Req 9.5). Distinguish for the operator: only a non-Succeed current state is an
            // anomaly worth recording; a repeat Succeed is an expected duplicate delivery.
            val current = orderDb.loadOrder(order.id)?.status
            if (current == AccessoryOrderStatus.PaymentSucceed) {
                logger.info("onPaymentStatusChanged: Succeed for order {} already applied — no-op (Req 9.7)", order.code)
            } else {
                logger.warn(
                    "onPaymentStatusChanged: Succeed for order {} ignored — order is in conflicting state {} (Req 9.5)",
                    order.code, current,
                )
                reconciliationDb.record(
                    ReconKind.UNROUTABLE_CALLBACK,
                    "Succeed callback for order ${order.code} ignored; conflicting state=$current",
                    order.id,
                )
            }
            return
        }

        logger.info("onPaymentStatusChanged: order {} transitioned to PaymentSucceed", order.code)

        // 2. Stock was already reserved at order creation (reserve-on-create), so a successful
        //    payment performs NO further stock change — the reserved units simply become a confirmed
        //    sale. This is the overselling fix: inventory is held from checkout, not from payment
        //    success, so two long-running payment sessions can never both consume the same units.

        // 3. Confirmation-email dispatch (task 7.3, Req 9.4, 9.6, 9.7, 18.2). Only the first Succeed
        //    callback reaches here (the guarded transition above is the idempotency point), so a
        //    re-delivered Succeed never triggers a second dispatch (Req 9.7). The dispatch itself runs
        //    off the callback-ack path (background scope) so the payment callback returns promptly; the
        //    per-order `emailDispatched` claim makes at-most-one send explicit even across restarts.
        emailDispatchScope.launch {
            try {
                dispatchConfirmationEmail(updated)
            } catch (t: Throwable) {
                logger.error("unexpected error dispatching confirmation email for order {}", updated.code, t)
            }
        }
    }

    /**
     * Dispatch the confirmation email for a `PaymentSucceed` order via the dedicated
     * [AccessoryMailGateway.sendMailAccessoryOrder] RPC (design "Notification → Dispatch, idempotency
     * & retry"). Never the ticket `SendMailOrder` (Req 18.2).
     *
     * Flow:
     * 1. Atomically claim the single dispatch slot ([OrderDatabaseService.tryClaimEmailDispatch],
     *    `emailDispatched` false→true). If the claim is not won, another dispatch already ran (or is
     *    running) — no-op return (Req 9.7).
     * 2. Load the owning show (source of show name, pickup note, mail plugin/template). If it cannot be
     *    loaded, record an `EMAIL_DISPATCH_FAILED` entry, release the claim, and leave the order
     *    `PaymentSucceed` + resend-eligible.
     * 3. Send with a bounded retry budget of [EMAIL_DISPATCH_MAX_ATTEMPTS] (Req 9.6). On success stamp
     *    `emailDispatchedAt` and keep the claim.
     * 4. On exhaustion record an `EMAIL_DISPATCH_FAILED` reconciliation entry, keep the order
     *    `PaymentSucceed`, and RESET the claim so the order stays eligible for the private resend
     *    endpoint (Req 9.6, 15.2). Order status is never changed on this path.
     *
     * This method is also the single send path reused by the resend endpoint (task 12.4), which first
     * releases the claim so a fresh dispatch can proceed.
     */
    suspend fun dispatchConfirmationEmail(order: AccessoryOrderDocument) {
        // 1. Claim the single dispatch slot atomically (idempotency, Req 9.7).
        if (!orderDb.tryClaimEmailDispatch(order.id)) {
            logger.info("confirmation email for order {} already claimed/sent — no-op (Req 9.7)", order.code)
            return
        }

        // 2. Load the owning show for the native SendMailAccessoryOrderRequest fields.
        val show = catalogDb.loadShowById(order.accessoryShowId)
        if (show == null) {
            logger.error(
                "cannot dispatch confirmation email for order {}: owning show {} not found; releasing claim",
                order.code, order.accessoryShowId,
            )
            reconciliationDb.record(
                ReconKind.EMAIL_DISPATCH_FAILED,
                "confirmation email not sent for order ${order.code}: owning show ${order.accessoryShowId} not found",
                order.id,
            )
            orderDb.resetEmailDispatchClaim(order.id)
            return
        }

        // 3. Send via the dedicated SendMailAccessoryOrder RPC with a bounded retry budget (Req 9.6).
        var lastError: Throwable? = null
        for (attempt in 1..EMAIL_DISPATCH_MAX_ATTEMPTS) {
            try {
                mailGateway.sendMailAccessoryOrder(order, show)
                orderDb.markEmailDispatched(order.id)
                logger.info(
                    "dispatched confirmation email for order {} to {} on attempt {} (Req 9.4)",
                    order.code, order.customerEmail, attempt,
                )
                return
            } catch (t: Throwable) {
                lastError = t
                logger.warn(
                    "confirmation email attempt {}/{} failed for order {} (recipient={})",
                    attempt, EMAIL_DISPATCH_MAX_ATTEMPTS, order.code, order.customerEmail, t,
                )
                if (attempt < EMAIL_DISPATCH_MAX_ATTEMPTS) delay(EMAIL_DISPATCH_RETRY_DELAY_MS * attempt)
            }
        }

        // 4. Direct send exhausted (Req 9.6). Before giving up, schedule a durable JobRunr fallback so
        //    `service.notification` keeps retrying the send out-of-process (mirrors service.order's
        //    `resendEmailOrder`). The order stays PaymentSucceed either way; the claim is released in
        //    both branches so the order also stays eligible for the private resend endpoint (Req 15.2).
        val scheduled = jobrunrGateway.resendEmailAccessoryOrder(order, show)
        if (scheduled) {
            logger.warn(
                "confirmation email direct send exhausted after {} attempts for order {}; scheduled JobRunr fallback",
                EMAIL_DISPATCH_MAX_ATTEMPTS, order.code, lastError,
            )
            reconciliationDb.record(
                ReconKind.EMAIL_DISPATCH_FAILED,
                "confirmation email direct send failed after $EMAIL_DISPATCH_MAX_ATTEMPTS attempts for order " +
                    "${order.code}; recipient=${order.customerEmail} lastError=${lastError?.message}; " +
                    "scheduled JobRunr fallback (ResendMailAccessoryOrder)",
                order.id,
            )
            orderDb.resetEmailDispatchClaim(order.id)
            return
        }

        // JobRunr scheduling also failed (Req 9.6, 15.2): keep the order PaymentSucceed, record
        // reconciliation, and release the claim so the order stays resend-eligible.
        logger.error(
            "confirmation email failed after {} attempts for order {} and JobRunr fallback could not be scheduled; " +
                "keeping PaymentSucceed + releasing claim",
            EMAIL_DISPATCH_MAX_ATTEMPTS, order.code, lastError,
        )
        reconciliationDb.record(
            ReconKind.EMAIL_DISPATCH_FAILED,
            "confirmation email failed after $EMAIL_DISPATCH_MAX_ATTEMPTS attempts for order ${order.code} and " +
                "JobRunr fallback scheduling failed; recipient=${order.customerEmail} lastError=${lastError?.message}",
            order.id,
        )
        orderDb.resetEmailDispatchClaim(order.id)
    }

    /**
     * Shared payment-creation failure handler (Req 7.4): move the order to the `PaymentFailed`
     * terminal state via the guarded transition, then release its reservation (return reserved stock
     * to inventory + revert any reserved discount) exactly once.
     *
     * The order transition happens first so the order is terminal-safe even if the release throws;
     * the guarded transition (compare-and-set over pre-terminal states) makes it a no-op if the order
     * somehow already left a pre-terminal state. Both are best-effort — a failure to transition/release
     * is logged but never masks the original `502` surfaced to the caller.
     */
    private suspend fun failOrder(order: AccessoryOrderDocument, reason: String?) {
        logger.error(
            "payment creation failed for order {} ({}); failing order + releasing reservation",
            order.id, reason,
        )
        try {
            orderStateMachine.transition(order.id, PRE_TERMINAL, AccessoryOrderStatus.PaymentFailed)
        } catch (t: Throwable) {
            logger.error("failed to transition order {} to PaymentFailed after payment failure", order.id, t)
        }
        // Return the reserved stock to inventory and revert any reserved discount, exactly once.
        try {
            releaseReservation(order)
        } catch (t: Throwable) {
            logger.error("failed to release reservation for order {} after payment failure", order.id, t)
        }
    }

    /** Waiting / Closed shows are browsable but must not be purchased or discount-previewed. */
    private fun requireShowOnSale(show: AccessoryShowDocument) {
        if (show.status != AccessoryShowStatus.Open) {
            throw CheckoutException.ShowNotOnSale(show.status.name)
        }
    }
}
