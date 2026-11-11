package metaticket.accessory.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

/**
 * Kind of operational anomaly recorded in the reconciliation collection.
 *
 * - [DISCOUNT_REVERT_FAILED] — a discount-code revert could not be completed after retries.
 * - [STOCK_SHORTFALL] — stock decrement floored at zero (sold more than available).
 * - [EMAIL_DISPATCH_FAILED] — the confirmation email could not be dispatched after retries.
 * - [UNROUTABLE_CALLBACK] — a payment status callback carried an unknown/absent `orderType`.
 */
enum class ReconKind { DISCOUNT_REVERT_FAILED, STOCK_SHORTFALL, EMAIL_DISPATCH_FAILED, UNROUTABLE_CALLBACK }

/**
 * A reconciliation record (collection `accessory-reconciliation`) capturing an anomaly that needs
 * out-of-band repair. It is **not** on the customer path. Modelled exactly per the design
 * "Reconciliation collection".
 */
data class ReconciliationEntry(
    @BsonId var id: String = ObjectId().toHexString(),
    val kind: ReconKind,                       // DISCOUNT_REVERT_FAILED | STOCK_SHORTFALL | EMAIL_DISPATCH_FAILED | UNROUTABLE_CALLBACK
    @BsonRepresentation(BsonType.OBJECT_ID) val orderId: String? = null,
    val detail: String,                        // human-readable context (sku/variant, code, paymentId, ...)
    val resolved: Boolean = false,
    val createdAt: Date = Date(),
)
