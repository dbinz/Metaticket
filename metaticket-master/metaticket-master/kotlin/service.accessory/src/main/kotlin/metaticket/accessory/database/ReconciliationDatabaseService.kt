package metaticket.accessory.database

import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.accessory.logger
import metaticket.accessory.pojo.db.ReconKind
import metaticket.accessory.pojo.db.ReconciliationEntry

/**
 * Owns the operational reconciliation collection (`accessory-reconciliation`) — a small out-of-band
 * log of anomalies that need manual repair and are **not** on the customer path (design
 * "Reconciliation collection").
 *
 * Entries are append-only; the payment-status handler ([metaticket.accessory.service.AccessoryOrderService.onPaymentStatusChanged])
 * writes a [ReconKind.STOCK_SHORTFALL] row when a line's applicable stock is floored at zero (Req
 * 9.3), and later tasks add `DISCOUNT_REVERT_FAILED` / `EMAIL_DISPATCH_FAILED` / `UNROUTABLE_CALLBACK`
 * rows. Recording an entry must never fail the customer flow, so callers treat write failures as
 * best-effort.
 */
class ReconciliationDatabaseService(
    private val reconciliationCol: MongoCollection<ReconciliationEntry>,
) {

    /**
     * Append a reconciliation entry.
     *
     * @param kind    the anomaly kind (e.g. [ReconKind.STOCK_SHORTFALL]).
     * @param detail  human-readable context (sku/variant, code, paymentId, ...).
     * @param orderId the raw accessory order id this anomaly relates to, when known.
     */
    suspend fun record(kind: ReconKind, detail: String, orderId: String? = null) {
        val entry = ReconciliationEntry(kind = kind, orderId = orderId, detail = detail)
        withContext(Dispatchers.IO) { reconciliationCol.insertOne(entry) }
        logger.warn("recorded reconciliation entry kind={} orderId={} detail={}", kind, orderId, detail)
    }
}
