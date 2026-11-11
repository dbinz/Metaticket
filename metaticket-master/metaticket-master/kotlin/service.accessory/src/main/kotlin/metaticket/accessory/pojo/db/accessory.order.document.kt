package metaticket.accessory.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

/**
 * Lifecycle status of an accessory order.
 *
 * Transition rules (allowed one-step advances plus expiry from any pre-terminal state) are
 * enforced by the order state machine (task 5); this enum only declares the states.
 *
 * Terminal states: [PaymentSucceed], [PaymentFailed], [PaymentCancelled], [Expired].
 */
enum class AccessoryOrderStatus {
    Created, WaitingForPayment, PaymentProcessing,
    PaymentSucceed, PaymentFailed, PaymentCancelled, Expired
}

/**
 * How the customer receives purchased accessories. Modes must be enabled on the owning show's
 * fulfillment settings at checkout; only one mode is stored per order.
 */
enum class FulfillmentType { PickupAtShow, MetaTicketPickup, Delivery }

/**
 * Delivery destination snapshotted onto the order at checkout (when [FulfillmentType.Delivery]).
 * Free-text Vietnam address components — not validated against a province catalog.
 */
data class AccessoryOrderDeliveryAddress(
    val address: String,
    val ward: String,
    val city: String, // Tỉnh/Thành phố (post-merger address)
    val note: String? = null,
)

/**
 * Kind of a discount campaign / applied discount snapshot.
 *
 * - [Percentage] / [FixedAmount]: classic coupon (usually manual code entry).
 * - [Tiered]: auto-applied; highest eligible spend tier wins.
 * - [EarlyBird]: auto-applied within a show time window; reward is % or fixed via `rewardType`.
 *
 * NOTE: Shared with the discount-code model — do not redefine there.
 */
enum class DiscountType { Percentage, FixedAmount, Tiered, EarlyBird }

/**
 * A resolved discount snapshot embedded in an [AccessoryOrderDocument].
 * `amount` is the concrete VND value applied to this specific order at checkout.
 * [type] is the campaign type that won (not the nested tier reward type).
 */
data class AccessoryOrderDiscount(
    val code: String,
    val type: DiscountType,
    val amount: Long,                      // resolved discount in VND
    @BsonRepresentation(BsonType.OBJECT_ID) val discountCodeId: String,
)

/**
 * A single purchased line within an [AccessoryOrderDocument]. Catalog-derived fields
 * (`name`, `variantLabel`, `unitPrice`) are snapshotted at checkout so the order stays stable even
 * if the catalog later changes.
 *
 * Validation rules (see [validationErrors]):
 * - `quantity > 0`.
 * - `unitPrice >= 0`.
 * - `variantKey`/`variantLabel` are both non-null or both null (paired): populated iff the referenced
 *   accessory has variants, null for single-SKU. The "iff accessory has variants" half of the rule is
 *   enforced at checkout where the catalog is available; at the document level only the pairing is enforced.
 */
data class AccessoryOrderLine(
    @BsonRepresentation(BsonType.OBJECT_ID) val accessoryId: String,
    val sku: String,
    val name: String,
    val variantKey: String? = null,        // selected variant key; null iff accessory has no variants
    val variantLabel: String? = null,      // snapshot label, e.g. "Size M" (for display)
    val unitPrice: Long,                   // snapshot at checkout
    val quantity: Int,                     // > 0
)

/**
 * An accessory order (collection `accessory-order`) — a pickup-at-show purchase of one or more
 * accessory lines. Modelled exactly per the design "Data Models → AccessoryOrder" section.
 *
 * Guard flags (design "Discount Reservation & Revert" and "Notification → Dispatch"):
 * - `discountReverted` — atomically flipped once to guarantee an at-most-once discount revert per order
 *   (only meaningful when `discount != null`).
 * - `emailDispatched` / `emailDispatchedAt` — the confirmation-email exactly-once dispatch guard and
 *   its completion timestamp.
 *
 * Validation rules (see [validationErrors]):
 * - `code` non-blank (backed by a unique index).
 * - `lines` non-empty.
 * - `fulfillment` is a known [FulfillmentType]; Delivery requires [deliveryAddress]; MetaTicketPickup
 *   requires pickup-point snapshot fields; PickupAtShow must not carry delivery/pickup-point data.
 * - Each line: `quantity > 0`; `variantKey`/`variantLabel` paired (both null or both non-null).
 * - `subtotal == Σ line.unitPrice * line.quantity`.
 * - `total == max(0, subtotal - (discount?.amount ?: 0))`.
 */
data class AccessoryOrderDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),
    @BsonRepresentation(BsonType.OBJECT_ID)
    val accessoryShowId: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    var paymentId: String? = null,         // opaque id from payment service
    var code: String,                      // unique order code (e.g. "TF2026-0148")
    var status: AccessoryOrderStatus = AccessoryOrderStatus.Created,
    val fulfillment: FulfillmentType = FulfillmentType.PickupAtShow,
    // Which show session the customer bought these products for (PickupAtShow + session-selection).
    // Null when the mode is not PickupAtShow, or when the owning show has no sessions.
    val showSessionId: String? = null,
    val showSessionLabel: String? = null,
    // MetaTicketPickup snapshot — null for other modes.
    val pickupPointId: String? = null,
    val pickupPointName: String? = null,
    val pickupPointAddress: String? = null,
    // Delivery destination — null for other modes.
    val deliveryAddress: AccessoryOrderDeliveryAddress? = null,
    val lines: List<AccessoryOrderLine>,
    val subtotal: Long,                    // sum(line.unitPrice * qty)
    val discount: AccessoryOrderDiscount? = null,
    var total: Long,                       // subtotal - discount.amount, >= 0
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    // The authenticated customer who placed the order (gateway `X-Customer-User-Id`). Used for
    // object-level authorization: only the owner may cancel their own pre-payment order. Nullable
    // for backward compatibility with orders created before ownership was recorded (such legacy
    // orders can only be released by the TTL sweep, never by the customer-facing cancel).
    val customerUserId: String? = null,
    // Exactly-once discount revert guard (design "Discount Reservation & Revert").
    var discountReverted: Boolean = false,
    // Exactly-once stock-release guard. Stock is reserved (decremented) at order creation
    // (reserve-on-create), so it must be released (incremented back) exactly once when the order
    // reaches a non-success terminal state — payment failure, cancellation, or TTL expiry. This flag
    // is flipped false→true atomically by the winning release path so a re-delivered callback or a
    // race between the fail/cancel/expiry paths can never double-release inventory.
    var stockReleased: Boolean = false,
    // Exactly-once confirmation-email dispatch guard (design "Notification → Dispatch").
    var emailDispatched: Boolean = false,
    var emailDispatchedAt: Date? = null,
    // When the customer was handed to the payment gateway (WaitingForPayment → PaymentProcessing).
    // From then on the order settles by the gateway outcome (IPN / inquiry), not the platform TTL;
    // this timestamp only drives the stale-PaymentProcessing safety net. Null on older orders.
    val paymentStartedAt: Date? = null,
    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)
