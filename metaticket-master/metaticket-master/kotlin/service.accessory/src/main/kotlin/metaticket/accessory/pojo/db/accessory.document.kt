package metaticket.accessory.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

/**
 * Lifecycle status of an accessory show.
 * Only [Open] shows are exposed by the public storefront; [Waiting] (chờ mở bán) and [Closed]
 * are admin-only and hidden from customers.
 */
enum class AccessoryShowStatus { Open, Waiting, Closed }

/**
 * Catalog city on an accessory show session (same shape as ticket CityDocument-lite).
 */
data class AccessoryCityDocument(
    val id: String = "",
    val name: String = "",
    val code: String = "",
) {
    /** Prefer human name; fall back to code when name is blank. */
    fun displayName(): String = name.takeIf { it.isNotBlank() } ?: code
}

/** Per-show META Ticket / offline pickup location (admin-configured). */
data class AccessoryPickupPoint(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val note: String? = null,
)

/**
 * Which fulfillment modes customers may pick at checkout for this show.
 * Defaults keep phase-1 storefront behaviour (pickup-at-show only) when omitted on create.
 */
data class AccessoryFulfillmentSettings(
    val enablePickupAtShow: Boolean = true,
    val enableMetaTicketPickup: Boolean = false,
    val enableDelivery: Boolean = false,
    val pickupAtShowSessionIds: List<String> = emptyList(),
    val pickupPoints: List<AccessoryPickupPoint> = emptyList(),
)

/**
 * A show session an accessory show is tied to — where and when the associated event runs. Used to
 * render the storefront banner and to record which session a customer bought for at checkout.
 *
 * [id] is a stable per-session identifier. When blank, [sessionId] derives one from
 * city|venue|datetime so orders can still reference the session.
 */
data class AccessoryShowSession(
    val id: String = "",
    val city: AccessoryCityDocument = AccessoryCityDocument(),
    val venue: String = "",
    val datetime: Date = Date(),
) {
    /**
     * The stable identifier used to tie an order to this session. Uses the explicit [id] when set,
     * otherwise a deterministic value derived from the session's content.
     */
    fun sessionId(): String =
        id.takeIf { it.isNotBlank() } ?: "${city.displayName()}|$venue|${datetime.time}"

    /** A human-readable snapshot label for the session, e.g. "TP.HCM · Nhà thi đấu Rạch Miễu". */
    fun label(): String = "${city.displayName()} · $venue"
}

/**
 * An accessory show (collection `accessory-show`) — a merchandise event grouping a set of
 * accessories (e.g. band tees, posters, light sticks for a concert).
 *
 * Modelled exactly per the design "Data Models" section, including the per-show `codePrefix`
 * used by order-code generation (`<PREFIX><YEAR>-<SEQ4>`, e.g. `TF2026-0148`).
 *
 * Validation rules (see [validate]):
 * - `code` non-blank, backed by a unique index.
 * - `name` non-blank.
 * - `codePrefix` non-blank (defaults to "AC").
 * - Only `status == Open` shows are returned by public endpoints.
 * - `pickupNote` / `deliveryNote` are optional plain-text tips shown for pickup-at-show
 *   and home-delivery fulfillment respectively.
 * - Optional [slug] is a Canonical_Slug for public URLs (`/san-pham/{slug}`); null when unset.
 */
data class AccessoryShowDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),
    val code: String,                      // unique, human-readable
    val codePrefix: String = "AC",         // per-show order-code prefix, e.g. "TF"
    val name: String,
    /**
     * Optional Canonical_Slug (`[a-z0-9]+(-[a-z0-9]+)*`, ≤200). Partial unique index when present.
     * Null / absent means the show is only reachable by Checksum-encoded id.
     */
    val slug: String? = null,
    val description: String? = null,
    val bannerImageUrl: String? = null,
    // Sessions (venue/datetime/city object) shown on the storefront banner.
    val sessions: List<AccessoryShowSession> = emptyList(),
    val status: AccessoryShowStatus = AccessoryShowStatus.Open,
    /** Planned on-sale instant when [status] is [AccessoryShowStatus.Waiting]; optional. */
    val saleOpenAt: Date? = null,
    val pickupNote: String? = null,        // e.g. "Nhận sản phẩm tại quầy The Flob ở show"
    // Show-level tip for "Giao tận nơi" (not the customer's checkout address note).
    val deliveryNote: String? = null,
    val pgAccountCode: String? = null,     // which PG account to bill through; null = payment default
    // Payment methods offered for this show, as `metaticket.payment.datastructure.PaymentType`
    // enum names (e.g. "VietQr", "QrCode", "InternationalCard"). Forwarded to service.payment at
    // checkout so the payment-start gate only accepts these methods. Empty => the checkout default
    // set (all methods).
    val allowedPaymentMethods: List<String> = emptyList(),
    val mailPlugin: String = "PluginMailTemplateAccessoryOrder",
    val mailTemplateId: String? = null,    // notification template id
    // Nullable because Kotlin DataClassCodec passes null when the BSON field is absent
    // (Kotlin default arguments are not applied). Prefer writing fulfillment on every upsert.
    val fulfillment: AccessoryFulfillmentSettings? = null,
    // Admin pin order for storefront listing; null = not pinned. Lower positive rank is shown first.
    val pinOrder: Int? = null,
    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
) {
    /** Fulfillment settings, or phase-1 defaults when the field was not stored. */
    fun resolvedFulfillment(): AccessoryFulfillmentSettings =
        fulfillment ?: AccessoryFulfillmentSettings()
}

/**
 * A selectable option of an accessory. Minimal: differs by stock/label and up to 2 attribute values.
 * In phase 1 variants carry no independent price — the accessory `price` applies to every variant.
 */
data class AccessoryVariant constructor(
    val key: String,                        // stable id within the accessory, e.g. "M-BLACK"
    val label: String,                      // combined display, e.g. "Size M · Đen"
    var stock: Int,                         // remaining units for this variant; -1 = unlimited
    /** Value for product attribute slot 1 ([AccessoryDocument.attr1Name]); null when unused. */
    val attr1: String? = null,
    /** Value for product attribute slot 2 ([AccessoryDocument.attr2Name]); null when unused. */
    val attr2: String? = null,
    /** Required image for this combination; drives the storefront gallery when variants exist. */
    val imageUrl: String? = null,
    // Whether this specific combination is available for purchase. Mirrors the accessory-level
    // `active` flag: an inactive variant is hidden/disabled on the storefront and rejected at
    // checkout. Defaults to `true` so variants seeded before this field stay purchasable.
    val active: Boolean = true,
)

/**
 * A purchasable product (collection `accessory`) belonging to an accessory show, either single-SKU
 * or carrying a list of variants.
 *
 * Validation rules (see [validate]):
 * - `price > 0`.
 * - `(accessoryShowId, sku)` unique compound index.
 * - Public listing returns only `active == true` items.
 * - Single-SKU (`variants` empty): `stock >= -1` (`-1` = unlimited).
 * - Variant (`variants` non-empty): each `variant.key` is non-blank and unique within the accessory;
 *   each `variant.stock >= -1`; each `variant.imageUrl` is non-blank; top-level `stock`/`imageUrls`
 *   are ignored for stock/gallery (gallery is derived from variant images).
 * - At most two named attribute dimensions ([attr1Name]/[attr2Name]); values live on variants as
 *   [AccessoryVariant.attr1]/[AccessoryVariant.attr2].
 */
data class AccessoryDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),
    @BsonRepresentation(BsonType.OBJECT_ID)
    val accessoryShowId: String,           // FK → accessory-show
    val sku: String,                       // unique within show
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,          // listing thumbnail (always used on storefront cards)
    // Full product gallery for single-SKU popup. When the product has variants this is empty —
    // storefront builds the popup gallery from variant.imageUrl instead.
    val imageUrls: List<String> = emptyList(),
    val price: Long,                        // VND, > 0 (single price; variants share it in phase 1)
    val currency: String = "VND",
    var stock: Int,                         // used ONLY when variants is empty; -1 = unlimited
    val variants: List<AccessoryVariant> = emptyList(), // empty = single SKU; non-empty = pick one
    /** Display name for attribute slot 1. Cleared when no variant uses [AccessoryVariant.attr1]. */
    val attr1Name: String? = null,
    /** Display name for attribute slot 2. Cleared when no variant uses [AccessoryVariant.attr2]. */
    val attr2Name: String? = null,
    /**
     * Minimum units of this product required in one checkout (sum across variants).
     * Null = no product-level minimum (global checkout min of 1 per line still applies).
     */
    val minOrderQty: Int? = null,
    /**
     * Maximum units of this product allowed in one checkout (sum across variants).
     * Null = no product-level maximum (global checkout max still applies).
     */
    val maxOrderQty: Int? = null,
    /**
     * Lifetime max units this product may be sold to one customer (Paid + open holds).
     * Null = no per-user cap.
     */
    val maxPerUserQty: Int? = null,
    val active: Boolean = true,
    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
) {
    /** True when this accessory offers selectable variants; false for a single-SKU product. */
    val hasVariants: Boolean get() = variants.isNotEmpty()
}
