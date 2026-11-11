package metaticket.accessory.database

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.types.ObjectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.accessory.logger
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.service.EXPIRABLE_STATES
import java.util.Date

/**
 * Owns the accessory order collection (`accessory-order`) and declares its MongoDB indexes,
 * following the same convention as [CatalogDatabaseService] (indexes created in an `init` block
 * when the service is instantiated at start-up).
 *
 * Indexes (design "AccessoryOrder → Validation Rules" and "Order-code generation"):
 * - Unique index on `AccessoryOrderDocument.code`. Guarantees no two orders share an order code;
 *   a race or manual seed collision surfaces as a duplicate-key error so checkout can retry
 *   `nextOrderCode`.
 */
class OrderDatabaseService(
    private val accessoryOrderCol: MongoCollection<AccessoryOrderDocument>,
) {

    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            val codeIdx = accessoryOrderCol.createIndex(
                Indexes.ascending(AccessoryOrderDocument::code.name),
                IndexOptions().unique(true)
            )
            logger.info("created accessory-order code index {}", codeIdx)
            // Admin list by show (newest first).
            val showCreatedIdx = accessoryOrderCol.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending(AccessoryOrderDocument::accessoryShowId.name),
                    Indexes.descending(AccessoryOrderDocument::createdAt.name),
                ),
            )
            logger.info("created accessory-order show+createdAt index {}", showCreatedIdx)
            // Customer "my paid orders" list (newest first).
            val customerStatusCreatedIdx = accessoryOrderCol.createIndex(
                Indexes.compoundIndex(
                    Indexes.ascending(AccessoryOrderDocument::customerUserId.name),
                    Indexes.ascending(AccessoryOrderDocument::status.name),
                    Indexes.descending(AccessoryOrderDocument::createdAt.name),
                ),
            )
            logger.info(
                "created accessory-order customerUserId+status+createdAt index {}",
                customerStatusCreatedIdx,
            )
        }
    }

    /**
     * Loads [AccessoryOrderStatus.PaymentSucceed] orders owned by [customerUserId], newest first.
     * Powers the customer storefront "Quản lý đơn hàng" page.
     */
    suspend fun findPaidOrdersByCustomerUserId(customerUserId: String): List<AccessoryOrderDocument> =
        withContext(Dispatchers.IO) {
            if (customerUserId.isBlank()) return@withContext emptyList()
            accessoryOrderCol.find(
                Filters.and(
                    Filters.eq(AccessoryOrderDocument::customerUserId.name, customerUserId),
                    Filters.eq(AccessoryOrderDocument::status.name, AccessoryOrderStatus.PaymentSucceed.name),
                ),
            )
                .sort(Sorts.descending(AccessoryOrderDocument::createdAt.name))
                .toList()
        }

    /**
     * Newest unpaid hold ([Created] / [WaitingForPayment]) for [customerUserId], or null.
     * Powers `GET /accessory/order/pending` (post-login resume on the product page).
     */
    suspend fun findNewestPendingOrderByCustomerUserId(customerUserId: String): AccessoryOrderDocument? =
        withContext(Dispatchers.IO) {
            if (customerUserId.isBlank()) return@withContext null
            accessoryOrderCol.find(
                Filters.and(
                    Filters.eq(AccessoryOrderDocument::customerUserId.name, customerUserId),
                    Filters.`in`(
                        AccessoryOrderDocument::status.name,
                        listOf(
                            AccessoryOrderStatus.Created.name,
                            AccessoryOrderStatus.WaitingForPayment.name,
                        ),
                    ),
                ),
            )
                .sort(Sorts.descending(AccessoryOrderDocument::createdAt.name))
                .limit(1)
                .firstOrNull()
        }

    /**
     * Sums units of each [accessoryIds] already counted toward a customer's per-user cap:
     * [PaymentSucceed] plus open holds ([Created] / [WaitingForPayment] / [PaymentProcessing] — the
     * latter is a hold whose payment is in flight at the gateway).
     * Keys are raw Mongo accessory ids; missing ids are omitted (treated as 0 by callers).
     */
    suspend fun sumCountedQtyByAccessoryForCustomer(
        customerUserId: String,
        accessoryIds: Collection<String>,
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        if (customerUserId.isBlank() || accessoryIds.isEmpty()) return@withContext emptyMap()
        val idSet = accessoryIds.filter { ObjectId.isValid(it) }.toSet()
        if (idSet.isEmpty()) return@withContext emptyMap()
        val orders = accessoryOrderCol.find(
            Filters.and(
                Filters.eq(AccessoryOrderDocument::customerUserId.name, customerUserId),
                Filters.`in`(
                    AccessoryOrderDocument::status.name,
                    listOf(
                        AccessoryOrderStatus.PaymentSucceed.name,
                        AccessoryOrderStatus.Created.name,
                        AccessoryOrderStatus.WaitingForPayment.name,
                        AccessoryOrderStatus.PaymentProcessing.name,
                    ),
                ),
            ),
        ).toList()
        val totals = mutableMapOf<String, Int>()
        for (order in orders) {
            for (line in order.lines) {
                if (line.accessoryId in idSet) {
                    totals[line.accessoryId] = (totals[line.accessoryId] ?: 0) + line.quantity
                }
            }
        }
        totals
    }

    /**
     * Loads orders for one accessory show, newest first.
     * Optional [startAt]/[endAt] filter on `createdAt` (inclusive start, exclusive end when both set).
     * [showId] must be a valid ObjectId hex; otherwise returns an empty list.
     */
    suspend fun findOrdersByShowId(
        showId: String,
        startAt: Date? = null,
        endAt: Date? = null,
    ): List<AccessoryOrderDocument> = withContext(Dispatchers.IO) {
        if (!ObjectId.isValid(showId)) return@withContext emptyList()
        val filters = mutableListOf(
            Filters.eq(AccessoryOrderDocument::accessoryShowId.name, ObjectId(showId)),
        )
        if (startAt != null) {
            filters += Filters.gte(AccessoryOrderDocument::createdAt.name, startAt)
        }
        if (endAt != null) {
            filters += Filters.lt(AccessoryOrderDocument::createdAt.name, endAt)
        }
        accessoryOrderCol.find(Filters.and(filters))
            .sort(Sorts.descending(AccessoryOrderDocument::createdAt.name))
            .toList()
    }

    /**
     * Insert exactly one [AccessoryOrderDocument] and return the inserted document (Req 6.10).
     *
     * The `code` is expected to already be a freshly allocated, unique order code
     * (see [metaticket.accessory.service.OrderCodeService.allocateWithRetry]). A collision on the
     * unique `code` index surfaces as a `MongoWriteException` (duplicate-key), which the caller's
     * `allocateWithRetry` wrapper catches to re-allocate and retry — no code here swallows it.
     */
    suspend fun insertOrder(order: AccessoryOrderDocument): AccessoryOrderDocument {
        accessoryOrderCol.insertOne(order)
        return order
    }

    /**
     * Persist the opaque `paymentId` returned by `service.payment` onto the order (Req 7.2).
     *
     * Called by checkout (task 6.3) immediately after a successful `createPayment`, before the
     * guarded `Created → WaitingForPayment` transition. The `paymentId` is the raw payment id
     * (an `ObjectId` hex string produced by `service.payment`); it is stored on the
     * `@BsonRepresentation(OBJECT_ID)` `paymentId` field and only ever leaves the service
     * `Checksum`-encoded.
     */
    suspend fun setPaymentId(orderId: String, paymentId: String) {
        accessoryOrderCol.updateOne(
            Filters.eq("_id", ObjectId(orderId)),
            Updates.combine(
                Updates.set(AccessoryOrderDocument::paymentId.name, ObjectId(paymentId)),
                Updates.set(AccessoryOrderDocument::lastModified.name, Date()),
            ),
        )
    }

    /**
     * Load a single accessory order by its raw MongoDB id, or `null` when none exists.
     *
     * Backs [metaticket.accessory.service.AccessoryOrderService.onPaymentStatusChanged] (task 7):
     * the payment callback carries the opaque order id, and the handler loads the order (for its
     * `lines`, `discount`, and current `status`) before performing the guarded status transition and
     * the subsequent stock decrement / discount revert / email dispatch. `orderId` is the raw
     * `ObjectId` hex string that the accessory service assigned at checkout.
     */
    suspend fun loadOrder(orderId: String): AccessoryOrderDocument? =
        accessoryOrderCol.find(Filters.eq("_id", ObjectId(orderId))).firstOrNull()

    /**
     * Atomically claim the exactly-once discount revert for an order (design "Discount Reservation &
     * Revert → Exactly-once revert per order").
     *
     * Flips `discountReverted` from `false` to `true` in a single conditional update and returns
     * whether *this* call performed the flip. Only the caller that observes `true` is allowed to run
     * the actual [DiscountCodeService.revert]; every concurrent or re-delivered fail/cancel callback
     * observes `false` and must skip the revert, guaranteeing a discount code is decremented at most
     * once per order (Req 10.2, 12.3).
     *
     * The flip is claimed *before* the revert runs (mirroring the design): if the subsequent revert
     * fails after its retries, the terminal order state is preserved and a `DISCOUNT_REVERT_FAILED`
     * reconciliation entry captures it for out-of-band repair (Req 10.5) rather than re-opening the
     * claim.
     */
    suspend fun tryClaimRevert(orderId: String): Boolean =
        accessoryOrderCol.updateOne(
            Filters.and(
                Filters.eq("_id", ObjectId(orderId)),
                Filters.eq(AccessoryOrderDocument::discountReverted.name, false),
            ),
            Updates.combine(
                Updates.set(AccessoryOrderDocument::discountReverted.name, true),
                Updates.set(AccessoryOrderDocument::lastModified.name, Date()),
            ),
        ).modifiedCount == 1L

    /**
     * Atomically claim the exactly-once **stock release** for an order (reserve-on-create model).
     *
     * Flips `stockReleased` from `false` to `true` in a single conditional update and returns whether
     * *this* call performed the flip. Only the caller that observes `true` releases the reserved units
     * back to inventory; every concurrent or re-delivered fail/cancel/expiry path observes `false` and
     * skips, guaranteeing reserved stock is returned at most once per order.
     */
    suspend fun tryClaimStockRelease(orderId: String): Boolean =
        accessoryOrderCol.updateOne(
            Filters.and(
                Filters.eq("_id", ObjectId(orderId)),
                Filters.eq(AccessoryOrderDocument::stockReleased.name, false),
            ),
            Updates.combine(
                Updates.set(AccessoryOrderDocument::stockReleased.name, true),
                Updates.set(AccessoryOrderDocument::lastModified.name, Date()),
            ),
        ).modifiedCount == 1L

    /**
     * Atomically claim the exactly-once confirmation-email dispatch for an order (design
     * "Notification → Dispatch, idempotency & retry").
     *
     * Flips `emailDispatched` from `false` to `true` in a single conditional update and returns
     * whether *this* call performed the flip. Only the caller that observes `true` is allowed to send
     * the confirmation email; every concurrent or re-delivered `Succeed` callback (and any resend that
     * has not first released the claim) observes `false` and must skip, guaranteeing at most one email
     * per dispatch attempt (Req 9.4, 9.7). The claim is taken *before* the send runs; if the send is
     * later exhausted it is released via [resetEmailDispatchClaim] so the order stays resend-eligible.
     */
    suspend fun tryClaimEmailDispatch(orderId: String): Boolean =
        accessoryOrderCol.updateOne(
            Filters.and(
                Filters.eq("_id", ObjectId(orderId)),
                Filters.eq(AccessoryOrderDocument::emailDispatched.name, false),
            ),
            Updates.combine(
                Updates.set(AccessoryOrderDocument::emailDispatched.name, true),
                Updates.set(AccessoryOrderDocument::lastModified.name, Date()),
            ),
        ).modifiedCount == 1L

    /**
     * Stamp the confirmation-email completion timestamp (`emailDispatchedAt`) after a successful send
     * (design "Notification → Dispatch"). Leaves the `emailDispatched` claim set to `true` so the
     * email is never re-sent unless a resend explicitly releases the claim first (Req 9.4).
     */
    suspend fun markEmailDispatched(orderId: String) {
        val now = Date()
        accessoryOrderCol.updateOne(
            Filters.eq("_id", ObjectId(orderId)),
            Updates.combine(
                Updates.set(AccessoryOrderDocument::emailDispatchedAt.name, now),
                Updates.set(AccessoryOrderDocument::lastModified.name, now),
            ),
        )
    }

    /**
     * Release the confirmation-email dispatch claim by resetting `emailDispatched` to `false` (design
     * "Notification → Dispatch, idempotency & retry"). Called when a dispatch is exhausted after its
     * retry budget: the order stays `PaymentSucceed` and becomes eligible for a later resend via the
     * private resend endpoint (Req 9.6, 15.2). Never touches order status.
     */
    suspend fun resetEmailDispatchClaim(orderId: String) {
        accessoryOrderCol.updateOne(
            Filters.eq("_id", ObjectId(orderId)),
            Updates.combine(
                Updates.set(AccessoryOrderDocument::emailDispatched.name, false),
                Updates.set(AccessoryOrderDocument::lastModified.name, Date()),
            ),
        )
    }

    /**
     * Paid units per show (`PaymentSucceed` line quantities). Used to rank Open/Closed storefront lists.
     */
    suspend fun sumPaidQtyByShowId(): Map<String, Int> = withContext(Dispatchers.IO) {
        val sums = mutableMapOf<String, Int>()
        accessoryOrderCol.find(
            Filters.eq(AccessoryOrderDocument::status.name, AccessoryOrderStatus.PaymentSucceed.name),
        ).toList().forEach { order ->
            val qty = order.lines.sumOf { it.quantity }
            if (qty > 0) {
                sums[order.accessoryShowId] = (sums[order.accessoryShowId] ?: 0) + qty
            }
        }
        sums
    }

    /**
     * Load expirable orders (`status ∈ EXPIRABLE_STATES`, i.e. `Created` / `WaitingForPayment`)
     * whose `createdAt` is strictly older than [cutoff] — the candidates for the expiry sweep
     * (Req 11.2).
     *
     * `PaymentProcessing` is deliberately excluded: an order with an in-flight payment must settle
     * through its real payment outcome, not by TTL expiry. The state machine's compare-and-set guard
     * additionally tolerates any order that concurrently leaves an expirable state between this read
     * and the transition, so a stale row here is harmless (its transition simply matches nothing and
     * is a no-op).
     */
    suspend fun findExpirableOrdersOlderThan(cutoff: Date): List<AccessoryOrderDocument> =
        accessoryOrderCol.find(
            Filters.and(
                Filters.`in`(AccessoryOrderDocument::status.name, EXPIRABLE_STATES.map { it.name }),
                Filters.lt(AccessoryOrderDocument::createdAt.name, cutoff),
            )
        ).toList()

    /**
     * `PaymentProcessing` orders handed to the gateway before [cutoff] — candidates for the
     * stale-processing safety net (the gateway never reported a final outcome).
     */
    suspend fun findStaleProcessingOrdersStartedBefore(cutoff: Date): List<AccessoryOrderDocument> =
        accessoryOrderCol.find(
            Filters.and(
                Filters.eq(AccessoryOrderDocument::status.name, AccessoryOrderStatus.PaymentProcessing.name),
                Filters.lt(AccessoryOrderDocument::paymentStartedAt.name, cutoff),
            )
        ).toList()
}
