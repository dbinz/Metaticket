package metaticket.accessory.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.AccessoryOrderStatus.Created
import metaticket.accessory.pojo.db.AccessoryOrderStatus.Expired
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentCancelled
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentFailed
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentProcessing
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentSucceed
import metaticket.accessory.pojo.db.AccessoryOrderStatus.WaitingForPayment
import org.bson.types.ObjectId
import java.util.Date

/**
 * Pre-terminal order states — an order can still legally advance (or expire) from any of these
 * (design "Order State Machine (implementation)", Req 11.1/11.2).
 */
val PRE_TERMINAL: Set<AccessoryOrderStatus> = setOf(Created, WaitingForPayment, PaymentProcessing)

/**
 * Order states eligible for TTL expiry — a subset of [PRE_TERMINAL] excluding [PaymentProcessing].
 *
 * An order in `PaymentProcessing` has an in-flight payment at the gateway whose final outcome
 * (`Succeed`/`Failed`/`Cancelled`) is still pending, so it must NOT be swept to `Expired` out from
 * under a payment that may yet complete — only `Created` (payment not started) and
 * `WaitingForPayment` (payment created but not progressed) are expired by the sweep. Payment
 * callbacks still transition from the full [PRE_TERMINAL] set, so a `PaymentProcessing` order settles
 * through its real payment outcome rather than by expiry.
 */
val EXPIRABLE_STATES: Set<AccessoryOrderStatus> = setOf(Created, WaitingForPayment)

/**
 * Terminal order states — once reached, an order has no outgoing edges (Req 11.3). Any callback
 * arriving for an order already in one of these states is a no-op / conflicting-terminal error.
 */
val TERMINAL: Set<AccessoryOrderStatus> = setOf(PaymentSucceed, PaymentFailed, PaymentCancelled, Expired)

/**
 * Allowed one-step advances plus expiry from any pre-terminal state (Req 11.1, 11.2).
 *
 * The pre-terminal → `PaymentSucceed/PaymentFailed/PaymentCancelled` edges (not only from
 * `PaymentProcessing`) match the requirements' "pre-terminal order" wording (Req 9.1, 10.1) and the
 * state diagram, covering gateways that report a final status without an interim
 * `PaymentProcessing` callback. Terminal states are absent from the map, so they have no outgoing
 * edges (Req 11.3).
 */
private val ALLOWED: Map<AccessoryOrderStatus, Set<AccessoryOrderStatus>> = mapOf(
    Created to setOf(WaitingForPayment, PaymentSucceed, PaymentFailed, PaymentCancelled, Expired),
    WaitingForPayment to setOf(PaymentProcessing, PaymentSucceed, PaymentFailed, PaymentCancelled, Expired),
    PaymentProcessing to setOf(PaymentSucceed, PaymentFailed, PaymentCancelled, Expired),
    // terminal states have no outgoing edges (Req 11.3)
)

/**
 * True iff advancing an order from [from] to [to] is a legal edge in the transition table.
 * Rejects all illegal moves and any transition out of a terminal state (Req 11.3, 11.4).
 */
fun canTransition(from: AccessoryOrderStatus, to: AccessoryOrderStatus): Boolean =
    to in (ALLOWED[from] ?: emptySet())

/**
 * Enforces the accessory order lifecycle (Requirements 9–11) with a single transition table plus a
 * conditional (compare-and-set) MongoDB update, so that concurrent payment callbacks and the expiry
 * sweep cannot corrupt state. This is the concrete implementation behind Property 7 (terminal
 * monotonicity).
 *
 * The [transition] guard is idempotent: a re-delivered callback for an order already in the target
 * (or a conflicting terminal) state matches nothing and returns `null` (Req 9.7, 10.3).
 */
class OrderStateMachine(
    private val orderCol: MongoCollection<AccessoryOrderDocument>,
) {

    /**
     * Atomically advance an order from any of [from] to [to], compare-and-set on the current status.
     *
     * Returns the updated document iff the current status was one of [from]; a `null` result means
     * "no-op": the order was already in the target/terminal state, or the current status is not a
     * legal source for this event. The filter (matching `_id` and `status ∈ from`) rejects illegal
     * moves at the DB level (Req 11.3/11.4) and makes repeated callbacks idempotent (Req 9.7/10.3).
     *
     * - `Succeed` uses `from = PRE_TERMINAL`; a re-delivered `Succeed` for an order already in
     *   `PaymentSucceed` matches nothing → `null` → no-op (Req 9.7).
     * - A `Succeed`/`Failed`/`Cancelled` arriving for an order already in a different, conflicting
     *   terminal state also matches nothing → `null`; the caller records an error and leaves state
     *   unchanged (Req 9.5, 10.6).
     * - Expiry (the scheduled JobRunr sweep over pre-terminal orders past their TTL) calls
     *   `transition(from = PRE_TERMINAL, to = Expired)` (Req 11.2).
     */
    suspend fun transition(
        orderId: String,
        from: Set<AccessoryOrderStatus>,
        to: AccessoryOrderStatus,
    ): AccessoryOrderDocument? =
        orderCol.findOneAndUpdate(
            Filters.and(
                Filters.eq("_id", ObjectId(orderId)),
                Filters.`in`(AccessoryOrderDocument::status.name, from.map { it.name }),
            ),
            Updates.combine(
                Updates.set(AccessoryOrderDocument::status.name, to.name),
                Updates.set(AccessoryOrderDocument::lastModified.name, Date()),
                Updates.inc(AccessoryOrderDocument::version.name, 1),
            ),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )

    /**
     * Compare-and-set `WaitingForPayment → PaymentProcessing` when the customer is handed to the
     * payment gateway, stamping [AccessoryOrderDocument.paymentStartedAt]. Leaving
     * [EXPIRABLE_STATES] is what stops the platform TTL sweep from expiring an order whose payment is
     * in flight. Returns `null` when the order is not in `WaitingForPayment` (already processing, or
     * already expired/cancelled — the CAS is what makes the race against the sweep safe).
     */
    suspend fun markPaymentProcessing(orderId: String): AccessoryOrderDocument? {
        val now = Date()
        return orderCol.findOneAndUpdate(
            Filters.and(
                Filters.eq("_id", ObjectId(orderId)),
                Filters.eq(AccessoryOrderDocument::status.name, WaitingForPayment.name),
            ),
            Updates.combine(
                Updates.set(AccessoryOrderDocument::status.name, PaymentProcessing.name),
                Updates.set(AccessoryOrderDocument::paymentStartedAt.name, now),
                Updates.set(AccessoryOrderDocument::lastModified.name, now),
                Updates.inc(AccessoryOrderDocument::version.name, 1),
            ),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )
    }
}
