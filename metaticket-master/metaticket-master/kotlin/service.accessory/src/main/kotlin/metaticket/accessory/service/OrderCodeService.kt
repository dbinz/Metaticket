package metaticket.accessory.service

import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import metaticket.accessory.logger
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.OrderSeqDocument

/**
 * Raised when a unique order code cannot be allocated after the bounded number of retries against
 * the unique `code` index (design "Order Code Generation"). Checkout (task 6.2) maps this to a
 * `500` after exhausting retries.
 */
class OrderCodeAllocationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Generates unique, human-readable order codes of the form `<PREFIX><YEAR>-<SEQ4>`
 * (e.g. `TF2026-0148`) — design "Order Code Generation" (Req 6.10, 6.15).
 *
 * Uniqueness rests on two layers:
 * 1. An atomic per-bucket counter in the `accessory-order-seq` collection. Each `(prefix, year)`
 *    bucket is a single [OrderSeqDocument]; [nextOrderCode] advances it with one atomic
 *    `findOneAndUpdate($inc)`, so concurrent checkouts never observe the same sequence.
 * 2. The unique index on [AccessoryOrderDocument.code] as a safety net. If a race or a manual seed
 *    ever collides, the order insert fails with a duplicate-key error; [allocateWithRetry] re-runs
 *    [nextOrderCode] (bounded to [maxAttempts] attempts, default 3) before surfacing the failure.
 *
 * DI note: the [orderSeqCol] is injected via the constructor. Koin wiring is deferred to task 16.2;
 * this class only needs the typed `accessory-order-seq` collection to compile and operate.
 */
class OrderCodeService(
    private val orderSeqCol: MongoCollection<OrderSeqDocument>,
) {

    /**
     * Atomically allocate the next sequence for the `(prefix, year)` bucket and format the code.
     *
     * A single `findOneAndUpdate` with `$inc` (upsert, return AFTER) is one atomic MongoDB
     * operation, guaranteeing a strictly increasing, collision-free sequence per bucket even under
     * concurrent checkouts.
     *
     * @param prefix per-show code prefix (e.g. `"TF"`); defaults to `"AC"` upstream when unset.
     * @param year   4-digit calendar year at creation time.
     * @return the formatted order code, e.g. `TF2026-0148`.
     */
    suspend fun nextOrderCode(prefix: String, year: Int): String {
        val bucket = "$prefix$year"
        val doc = orderSeqCol.findOneAndUpdate(
            Filters.eq("_id", bucket),
            Updates.inc(OrderSeqDocument::seq.name, 1L),
            FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER),
        ) ?: throw OrderCodeAllocationException(
            "findOneAndUpdate returned no document for order-seq bucket '$bucket'",
        )
        return formatOrderCode(prefix, year, doc.seq)
    }

    /**
     * Allocate an order code and run [block] with it, retrying on a duplicate-key collision against
     * the unique `code` index. This wraps allocation + insert so callers (checkout, task 6.2) can
     * simply insert the order inside [block]; if the insert throws a duplicate-key error, a fresh
     * code is allocated and [block] is retried up to [maxAttempts] times.
     *
     * The atomic counter makes collisions vanishingly rare; the retry exists purely as the safety
     * net paired with the unique index. Non-duplicate-key write errors propagate immediately.
     *
     * @param prefix      per-show code prefix.
     * @param year        4-digit calendar year.
     * @param maxAttempts maximum allocation attempts (design bound: 3).
     * @param block       action given the freshly allocated code; typically inserts the order.
     * @throws OrderCodeAllocationException when every attempt collides on the unique `code` index.
     */
    suspend fun <T> allocateWithRetry(
        prefix: String,
        year: Int,
        maxAttempts: Int = MAX_ALLOCATION_ATTEMPTS,
        block: suspend (code: String) -> T,
    ): T {
        var lastDuplicate: MongoWriteException? = null
        for (attempt in 1..maxAttempts) {
            val code = nextOrderCode(prefix, year)
            try {
                return block(code)
            } catch (e: MongoWriteException) {
                if (!isDuplicateOrderCodeError(e)) throw e
                lastDuplicate = e
                logger.warn(
                    "duplicate order code '{}' on attempt {}/{}, retrying allocation",
                    code, attempt, maxAttempts,
                )
            }
        }
        throw OrderCodeAllocationException(
            "failed to allocate a unique order code for '$prefix$year' after $maxAttempts attempts",
            lastDuplicate,
        )
    }

    /**
     * Whether [e] is a duplicate-key violation on the unique [AccessoryOrderDocument.code] index.
     * Scoping to the `code` field ensures we only retry on order-code collisions and never mask an
     * unrelated unique-index violation.
     */
    private fun isDuplicateOrderCodeError(e: MongoWriteException): Boolean =
        e.error.category == ErrorCategory.DUPLICATE_KEY &&
            e.error.message.contains(AccessoryOrderDocument::code.name)

    companion object {
        /** Bounded retry count against the unique `code` index (design "Order Code Generation"). */
        const val MAX_ALLOCATION_ATTEMPTS = 3

        /**
         * Pure formatting of an order code as `<PREFIX><YEAR>-<SEQ4>` (e.g. `TF2026-0148`) from an
         * already-allocated sequence value. Separated from the atomic `$inc` so the collision-free
         * mapping (distinct `(prefix, year, seq)` → distinct code) is unit/property-testable without a
         * database (design "Testing Strategy" → P18). The sequence is zero-padded to at least 4
         * digits and grows naturally beyond 4 digits, so codes stay unique for any distinct `seq`.
         */
        fun formatOrderCode(prefix: String, year: Int, seq: Long): String =
            "$prefix$year-${seq.toString().padStart(4, '0')}"
    }
}
