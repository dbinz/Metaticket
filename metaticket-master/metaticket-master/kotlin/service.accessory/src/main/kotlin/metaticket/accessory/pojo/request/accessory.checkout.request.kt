package metaticket.accessory.pojo.request

/**
 * Checkout request / result DTOs for `POST /accessory/checkout` (design "Core
 * Interfaces/Types (Low-Level)").
 *
 * The cart is a **frontend-only** concern: the storefront submits the *entire* cart in the request
 * body and the server validates and prices those lines directly from the payload — there is no
 * server-side cart and no cart reference. Unit prices are always re-derived from the catalog
 * (Req 6.9). Fulfillment mode + mode-specific fields are supplied by the client and re-checked
 * against the owning show's fulfillment settings at checkout.
 */

/**
 * A single submitted cart line.
 *
 * @property accessoryId the public, `Checksum`-encoded accessory id (raw `ObjectId`s never cross the
 *   public boundary — Req 16.1/16.2). Payload validation requires this to decode to a valid
 *   `ObjectId`; resolving it against the catalog is done at checkout (task 6.2).
 * @property variantKey the selected variant key; required iff the referenced accessory has variants
 *   and `null` for a single-SKU accessory. Whether the selection is *consistent with the catalog*
 *   (has-variants ⇒ key present and matching an existing variant) is re-derived at checkout (task
 *   6.2); payload validation only enforces the structural rule that a supplied key is non-blank.
 * @property quantity the number of units for this line; a whole number in `1..99` (Req 6.14, 16.3).
 */
data class CheckoutLine(
    val accessoryId: String,
    val variantKey: String? = null,
    val quantity: Int,
)

/**
 * Delivery destination submitted with [CheckoutInput] when `fulfillment == Delivery`.
 * Post-merger Vietnam address: `city` (tỉnh/TP), `ward` (phường/xã), `address` are required;
 * `note` is optional. No district field.
 */
data class DeliveryAddressInput(
    val address: String? = null,
    val ward: String? = null,
    val city: String? = null,
    val note: String? = null,
)

/**
 * The full checkout payload: the entire frontend cart, pickup/delivery contact details, and the
 * chosen fulfillment mode.
 *
 * @property lines the entire cart; must contain 1..50 lines (Req 6.1, 16.3).
 * @property customerName recipient full name, 1..100 characters, non-blank (Req 5.1, 5.3).
 * @property customerPhone recipient phone, 8..15 digits with an optional single leading `+`
 *   (Req 5.1, 5.5).
 * @property customerEmail recipient email, 1..254 characters, well-formed (Req 5.1, 5.4, 16.3).
 * @property discountCode an optional discount code to apply (evaluated at checkout, task 6.2).
 * @property fulfillment one of `PickupAtShow` / `MetaTicketPickup` / `Delivery`; defaults to
 *   `PickupAtShow` when omitted. Must be enabled on the owning show.
 * @property showSessionId the show session for PickupAtShow when the show has sessions.
 * @property pickupPointId the META Ticket pickup-point id when fulfillment is MetaTicketPickup.
 * @property deliveryAddress required when fulfillment is Delivery.
 */
data class CheckoutInput(
    val lines: List<CheckoutLine>,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    val discountCode: String? = null,
    val fulfillment: String? = null,
    val showSessionId: String? = null,
    val pickupPointId: String? = null,
    val deliveryAddress: DeliveryAddressInput? = null,
)

/**
 * The checkout success result. Only `Checksum`-encoded public identifiers are exposed for the
 * order/payment ids (Req 16.1), alongside the human-readable order code and the computed order
 * total that the success response must carry (Req 6.15, 7.3).
 *
 * @property encodedPaymentId the `Checksum`-encoded payment id (see [metaticket.accessory.security.PublicId]).
 * @property encodedOrderId the `Checksum`-encoded order id.
 * @property orderCode the created, human-readable Order_Code (e.g. `TF2026-0148`) (Req 6.15).
 * @property total the computed order total in whole VND (Req 6.15).
 */
data class CheckoutResult(
    val encodedPaymentId: String,
    val encodedOrderId: String,
    val orderCode: String,
    val total: Long,
)

/**
 * A server-derived pricing context for a submitted set of cart lines, produced by
 * [metaticket.accessory.service.AccessoryOrderService.deriveCartContext].
 *
 * It re-derives the owning accessory show and the subtotal **from the stored catalog** (never a
 * client-supplied price — Req 6.9), so the discount-preview endpoint can evaluate a code against a
 * trustworthy, server-computed subtotal (Req 4.1, 4.3) without re-implementing catalog resolution.
 *
 * @property showId the raw MongoDB id of the single `Open` accessory show all lines belong to.
 * @property subtotal the catalog-derived subtotal (`Σ unitPrice * quantity`) in whole VND.
 */
data class CartContext(
    val showId: String,
    val subtotal: Long,
)
