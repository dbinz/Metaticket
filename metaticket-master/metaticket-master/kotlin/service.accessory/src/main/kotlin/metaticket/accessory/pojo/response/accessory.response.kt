package metaticket.accessory.pojo.response

import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryFulfillmentSettings
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderLine
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.AccessoryShowSession
import metaticket.accessory.pojo.db.AccessoryVariant
import metaticket.accessory.pojo.db.FulfillmentType
import metaticket.accessory.pojo.db.optionMap
import metaticket.accessory.pojo.db.resolvedAttributes
import metaticket.accessory.security.PublicId

/**
 * Public read-endpoint response DTOs for the accessory storefront (task 12.2).
 *
 * Every identifier carried in a public response is a `Checksum`-encoded string produced by
 * [PublicId.encode]; a raw MongoDB `ObjectId` never leaves the service (Req 16.1, 16.2). These DTOs
 * intentionally omit internal/operational fields (`pgAccountCode`, mail plugin/template ids,
 * `version`, timestamps) that the storefront does not need.
 */

/**
 * A show session (venue/date/city) shown on the storefront banner; `date` is epoch millis.
 * [id] is the stable session identifier the storefront submits back at checkout to record which
 * session the customer bought for (session-selection feature).
 */
data class AccessoryShowSessionResponse(
    val id: String,                 // stable session id (echo back at checkout as showSessionId)
    val city: String,
    val venue: String,
    val date: Long,                 // epoch millis (client formats to dd.MM.yyyy)
)

/**
 * Summary of an accessory show (`GET /public/accessory/show/fetch/all` page content).
 * Products are not embedded in the listing — they are loaded per-show via the single-show fetch.
 * The `sessions` (venue/date/city) power the storefront banner.
 */
data class AccessoryShowSummaryResponse(
    val id: String,                 // Checksum-encoded accessory-show id
    val code: String,
    /** Canonical slug when set; empty string when absent (same posture as ticket show). */
    val slug: String = "",
    val name: String,
    val description: String?,
    val bannerImageUrl: String?,
    val sessions: List<AccessoryShowSessionResponse>,
    val pickupNote: String?,
    /** Optional show-level tip for home delivery (plain text). */
    val deliveryNote: String?,
    /** `Open` / `Waiting` / `Closed`. */
    val status: String,
    /** Planned sale-open epoch millis when Waiting; null when unset. */
    val saleOpenAt: Long? = null,
    /** Pin rank 1..5 when ghim; null when unpinned. */
    val pinOrder: Int? = null,
    /**
     * Lowest price (VND) among the show's active products, for the "Từ …đ" card label.
     * Null when the show has no active priced product — the storefront then omits the label.
     */
    val minPrice: Long? = null,
)

/**
 * Spring-like page for `GET /public/accessory/show/fetch/all?page=&size=`.
 * [number] is 0-based, matching homepage show highlight.
 */
data class AccessoryShowListPage(
    val content: List<AccessoryShowSummaryResponse>,
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
    val empty: Boolean,
    val numberOfElements: Int,
)

/**
 * A selectable variant of an accessory — one purchasable combination.
 * [options] holds keyed values (`attr1` / `attr2`). Empty ⇒ label-only selector.
 */
data class AccessoryVariantResponse(
    val key: String,
    val label: String,
    val stock: Int,                 // -1 = unlimited
    /** Keyed option values (`attr1` / `attr2`); empty when the variant has no dimensions. */
    val options: Map<String, String> = emptyMap(),
    /** Image for this combination; required when the product has variants. */
    val imageUrl: String? = null,
    val active: Boolean,            // false = this combination is withdrawn from sale (disabled on storefront)
)

/** Named product attribute dimension (max 2). */
data class AccessoryAttributeDefResponse(
    val key: String,                // "attr1" | "attr2"
    val name: String,               // display label, e.g. "Size", "Màu", "Chất liệu"
)

/** A single active accessory (product), including its variants when present. */
data class AccessoryResponse(
    val id: String,                 // Checksum-encoded accessory id
    val sku: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,          // primary image (grid card / cart line thumbnail)
    val imageUrls: List<String>,    // full gallery for the product-detail popup (never empty when an image exists)
    val price: Long,
    val currency: String,
    val stock: Int,                 // only meaningful for single-SKU products; -1 = unlimited
    val hasVariants: Boolean,
    /** Up to 2 named dimensions that drive independent selectors on the storefront. */
    val attributes: List<AccessoryAttributeDefResponse> = emptyList(),
    /** Min units of this product per checkout; null = no product minimum. */
    val minOrderQty: Int? = null,
    /** Max units of this product per checkout; null = no product maximum. */
    val maxOrderQty: Int? = null,
    /** Max units per customer for this product; null = no per-user cap. */
    val maxPerUserQty: Int? = null,
    /**
     * Units already counted toward [maxPerUserQty] for the authenticated customer
     * (Paid + open holds). Null when anonymous or when the product has no per-user cap.
     */
    val myPurchasedQty: Int? = null,
    val variants: List<AccessoryVariantResponse>,
)

/** Storefront pickup point for `MetaTicketPickup` (from the show's fulfillment config). */
data class AccessoryPickupPointResponse(
    val id: String,
    val name: String,
    val address: String,
    val note: String? = null,
)

/**
 * Which fulfillment modes the storefront may offer for this show (mirrors admin settings,
 * stripped of internal-only fields). Present on the detail endpoint.
 */
data class AccessoryFulfillmentResponse(
    val enablePickupAtShow: Boolean = true,
    val enableMetaTicketPickup: Boolean = false,
    val enableDelivery: Boolean = false,
    /** When non-empty and pickup-at-show is on, only these session ids are selectable. */
    val pickupAtShowSessionIds: List<String> = emptyList(),
    val pickupPoints: List<AccessoryPickupPointResponse> = emptyList(),
)

/**
 * A single open accessory show together with its active accessories (incl. variants), returned by
 * `GET /public/accessory/show/fetch?id=` (Req 1.3, 1.4).
 */
data class AccessoryShowDetailResponse(
    val id: String,                 // Checksum-encoded accessory-show id
    val code: String,
    /** Canonical slug when set; empty string when absent. */
    val slug: String = "",
    val name: String,
    val description: String?,
    val bannerImageUrl: String?,
    val sessions: List<AccessoryShowSessionResponse>,
    val pickupNote: String?,
    /** Optional show-level tip for home delivery (plain text). */
    val deliveryNote: String?,
    /** `Open` / `Waiting` / `Closed`. */
    val status: String,
    /** Planned sale-open epoch millis when Waiting; null when unset. */
    val saleOpenAt: Long? = null,
    // Payment methods offered for this show (PaymentType enum names). Empty => storefront shows all.
    val allowedPaymentMethods: List<String>,
    val fulfillment: AccessoryFulfillmentResponse,
    val accessories: List<AccessoryResponse>,
)

/**
 * Map a stored show session to its public response form.
 * Storefront still receives city as a display string and `date` epoch millis (homepage contract).
 */
fun AccessoryShowSession.toResponse() = AccessoryShowSessionResponse(
    id = sessionId(),
    city = city.displayName(),
    venue = venue,
    date = datetime.time,
)

/**
 * Map an accessory show document to its listing summary, encoding the public id.
 * [minPrice] is resolved by the caller in one batched query per page (see
 * [metaticket.accessory.database.CatalogDatabaseService.minPriceByShowIds]).
 */
fun AccessoryShowDocument.toSummaryResponse(minPrice: Long? = null) = AccessoryShowSummaryResponse(
    id = PublicId.encode(id),
    code = code,
    slug = slug ?: "",
    name = name,
    description = description,
    bannerImageUrl = bannerImageUrl,
    sessions = sessions.map { it.toResponse() },
    pickupNote = pickupNote,
    deliveryNote = deliveryNote,
    status = status.name,
    saleOpenAt = saleOpenAt?.time,
    pinOrder = pinOrder,
    minPrice = minPrice,
)

/** Maps persisted fulfillment settings to the public storefront DTO (modes off ⇒ empty lists). */
fun AccessoryFulfillmentSettings.toPublicResponse() = AccessoryFulfillmentResponse(
    enablePickupAtShow = enablePickupAtShow,
    enableMetaTicketPickup = enableMetaTicketPickup,
    enableDelivery = enableDelivery,
    pickupAtShowSessionIds = if (enablePickupAtShow) pickupAtShowSessionIds else emptyList(),
    pickupPoints = if (enableMetaTicketPickup) {
        pickupPoints.map { point ->
            AccessoryPickupPointResponse(
                id = point.id,
                name = point.name,
                address = point.address,
                note = point.note,
            )
        }
    } else {
        emptyList()
    },
)

/** Map an accessory show document + its active accessories to the single-show detail response. */
fun AccessoryShowDocument.toDetailResponse(accessories: List<AccessoryResponse>) = AccessoryShowDetailResponse(
    id = PublicId.encode(id),
    code = code,
    slug = slug ?: "",
    name = name,
    description = description,
    bannerImageUrl = bannerImageUrl,
    sessions = sessions.map { it.toResponse() },
    pickupNote = pickupNote,
    deliveryNote = deliveryNote,
    status = status.name,
    saleOpenAt = saleOpenAt?.time,
    allowedPaymentMethods = allowedPaymentMethods,
    fulfillment = resolvedFulfillment().toPublicResponse(),
    accessories = accessories,
)

/** Map a variant to its response form. */
fun AccessoryVariant.toResponse() = AccessoryVariantResponse(
    key = key,
    label = label,
    stock = stock,
    options = optionMap(),
    imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
    active = active,
)

/** Map an accessory document to its response form, encoding the public id. */
fun AccessoryDocument.toResponse(myPurchasedQty: Int? = null): AccessoryResponse {
    val variantGallery = variants.mapNotNull { v ->
        v.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    }.distinct()
    // Listing thumb is always product-level [imageUrl]; modal gallery comes from variants when present.
    val gallery = when {
        hasVariants -> variantGallery
        imageUrls.isNotEmpty() -> imageUrls
        else -> listOfNotNull(imageUrl)
    }
    return AccessoryResponse(
        id = PublicId.encode(id),
        sku = sku,
        name = name,
        description = description,
        imageUrl = imageUrl ?: imageUrls.firstOrNull(),
        imageUrls = gallery,
        price = price,
        currency = currency,
        stock = stock,
        hasVariants = hasVariants,
        attributes = resolvedAttributes().map { AccessoryAttributeDefResponse(it.key, it.name) },
        minOrderQty = minOrderQty,
        maxOrderQty = maxOrderQty,
        maxPerUserQty = maxPerUserQty,
        myPurchasedQty = if (maxPerUserQty != null) myPurchasedQty else null,
        variants = variants.map { it.toResponse() },
    )
}

// -----------------------------------------------------------------------------
// Order-view response DTOs (task 12.4 — GET /public/accessory/order/fetch?id=)
// -----------------------------------------------------------------------------

/**
 * A single order line as rendered in the order-review view (Req 13.3, 13.5).
 *
 * [display] is the pre-composed string combining the product name, the variant label (when the line
 * has a variant), and the quantity — e.g. `"Áo thun · Size M ×2"`. For a single-SKU line the variant
 * label is omitted, leaving `"<name> ×<qty>"` (Req 13.5). The structured fields are retained
 * alongside so the storefront can render its own layout if it prefers.
 */
data class AccessoryOrderLineView(
    val display: String,            // "Áo thun · Size M ×2" (variant label omitted for single-SKU)
    val name: String,
    val variantLabel: String?,      // null for single-SKU lines (Req 13.5)
    val quantity: Int,
    val unitPrice: Long,
    val lineTotal: Long,            // unitPrice * quantity
)

/** The resolved discount applied to an order, as shown in the review view. */
data class AccessoryOrderDiscountView(
    val code: String,
    val amount: Long,               // resolved discount in VND
)

/**
 * The customer-facing review of a completed order, returned by
 * `GET /public/accessory/order/fetch?id=` (Req 13.3). Powers the "Hoàn tất" review link.
 *
 * `fulfillment` is one of PickupAtShow / MetaTicketPickup / Delivery; mode-specific fields are
 * null when unused. The order/payment ids are `Checksum`-encoded; a raw `ObjectId` never leaves
 * the service (Req 16.1, 16.2).
 */
data class AccessoryOrderDeliveryAddressView(
    val address: String,
    val ward: String,
    val city: String,
    val note: String? = null,
)

data class AccessoryOrderView(
    val id: String,                 // Checksum-encoded order id
    val code: String,
    val status: String,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    val fulfillment: String,        // PickupAtShow | MetaTicketPickup | Delivery
    // Human-readable receive instructions for Hoàn tất / mail (mode-specific).
    val fulfillmentSummary: String,
    // The show session these products were bought for (PickupAtShow); null otherwise.
    val showSessionId: String?,
    val showSessionLabel: String?,
    val pickupPointId: String? = null,
    val pickupPointName: String? = null,
    val pickupPointAddress: String? = null,
    val deliveryAddress: AccessoryOrderDeliveryAddressView? = null,
    val subtotal: Long,
    val discount: AccessoryOrderDiscountView?,
    val total: Long,
    val lines: List<AccessoryOrderLineView>,
)

/**
 * Newest unpaid hold (`Created` / `WaitingForPayment`) for the logged-in customer
 * (`GET /accessory/order/pending`). Enough for the storefront to resume checkout/payment.
 */
data class PendingAccessoryOrderResponse(
    val orderId: String,
    val paymentId: String?,
    val orderCode: String,
    val status: String,
    val showId: String,
    val showName: String,
    val total: Long,
    /** Absolute payment deadline as epoch millis (`createdAt + orderTtlMinutes`). */
    val expiresAt: Long,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
)

/** Envelope so clients always get HTTP 200 when authenticated (`order` is null when nothing to resume). */
data class PendingAccessoryOrderLookupResponse(
    val order: PendingAccessoryOrderResponse? = null,
)

/**
 * Customer storefront list/detail card for one **paid** accessory order
 * (`GET /accessory/order/fetch/mine`). Extends the review view with show branding + createdAt.
 */
data class CustomerAccessoryOrderItem(
    val id: String,                 // Checksum-encoded order id
    val code: String,
    val status: String,
    val showName: String,
    val bannerImageUrl: String?,
    val createdAt: Long,
    /** Buyer user id (`X-Customer-User-Id`); null for legacy orders. */
    val customerUserId: String?,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    val fulfillment: String,
    val fulfillmentSummary: String,
    val showSessionId: String?,
    val showSessionLabel: String?,
    val pickupPointId: String? = null,
    val pickupPointName: String? = null,
    val pickupPointAddress: String? = null,
    val deliveryAddress: AccessoryOrderDeliveryAddressView? = null,
    val subtotal: Long,
    val discount: AccessoryOrderDiscountView?,
    val total: Long,
    val lines: List<AccessoryOrderLineView>,
)

/**
 * Compose the per-line display string (Req 13.3): product name, an optional `" · <variant label>"`
 * segment (present only when the line has a variant — Req 13.5), then `" ×<quantity>"`.
 * Example: `"Áo thun · Size M ×2"`; single-SKU: `"Áo thun ×2"`.
 */
fun AccessoryOrderLine.toLineView(): AccessoryOrderLineView {
    val labelSegment = variantLabel?.let { " · $it" } ?: ""     // omitted for single-SKU (Req 13.5)
    return AccessoryOrderLineView(
        display = "$name$labelSegment ×$quantity",
        name = name,
        variantLabel = variantLabel,
        quantity = quantity,
        unitPrice = unitPrice,
        lineTotal = unitPrice * quantity,
    )
}

/**
 * Compose a single customer-facing receive instruction for Hoàn tất / confirmation email.
 * [showPickupNote] is only used for PickupAtShow (from the owning show).
 */
fun AccessoryOrderDocument.fulfillmentSummary(showPickupNote: String? = null): String {
    return when (fulfillment) {
        FulfillmentType.PickupAtShow -> {
            val base = showPickupNote?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Nhận sản phẩm tại show. Vui lòng xuất trình mã đơn hàng khi nhận."
            val session = showSessionLabel?.trim()?.takeIf { it.isNotEmpty() }
            if (session != null) "$base (Buổi: $session)" else base
        }
        FulfillmentType.MetaTicketPickup -> {
            val name = pickupPointName?.trim().orEmpty()
            val addr = pickupPointAddress?.trim().orEmpty()
            when {
                name.isNotEmpty() && addr.isNotEmpty() -> "Nhận tại điểm META Ticket: $name — $addr"
                name.isNotEmpty() -> "Nhận tại điểm META Ticket: $name"
                addr.isNotEmpty() -> "Nhận tại điểm META Ticket — $addr"
                else -> "Nhận tại điểm Pickup META Ticket."
            }
        }
        FulfillmentType.Delivery -> {
            val addr = deliveryAddress
            if (addr == null) {
                "Đơn giao tận nơi."
            } else {
                buildString {
                    append("Giao tới: ${addr.address}, ${addr.ward}, ${addr.city}")
                    addr.note?.trim()?.takeIf { it.isNotEmpty() }?.let { append(". Ghi chú: $it") }
                }
            }
        }
    }
}

/** Map a stored accessory order to its review view, encoding the public id (Req 13.3, 14.5). */
fun AccessoryOrderDocument.toOrderView(showPickupNote: String? = null) = AccessoryOrderView(
    id = PublicId.encode(id),
    code = code,
    status = status.name,
    customerName = customerName,
    customerPhone = customerPhone,
    customerEmail = customerEmail,
    fulfillment = fulfillment.name,
    fulfillmentSummary = fulfillmentSummary(showPickupNote),
    showSessionId = showSessionId,
    showSessionLabel = showSessionLabel,
    pickupPointId = pickupPointId,
    pickupPointName = pickupPointName,
    pickupPointAddress = pickupPointAddress,
    deliveryAddress = deliveryAddress?.let {
        AccessoryOrderDeliveryAddressView(
            address = it.address,
            ward = it.ward,
            city = it.city,
            note = it.note,
        )
    },
    subtotal = subtotal,
    discount = discount?.let { AccessoryOrderDiscountView(code = it.code, amount = it.amount) },
    total = total,
    lines = lines.map { it.toLineView() },
)

/** Map a paid order + show branding for the customer "Quản lý đơn hàng" list/detail. */
fun AccessoryOrderDocument.toCustomerOrderItem(
    showName: String,
    bannerImageUrl: String?,
    showPickupNote: String? = null,
) = CustomerAccessoryOrderItem(
    id = PublicId.encode(id),
    code = code,
    status = status.name,
    showName = showName,
    bannerImageUrl = bannerImageUrl,
    createdAt = createdAt.time,
    customerUserId = customerUserId,
    customerName = customerName,
    customerPhone = customerPhone,
    customerEmail = customerEmail,
    fulfillment = fulfillment.name,
    fulfillmentSummary = fulfillmentSummary(showPickupNote),
    showSessionId = showSessionId,
    showSessionLabel = showSessionLabel,
    pickupPointId = pickupPointId,
    pickupPointName = pickupPointName,
    pickupPointAddress = pickupPointAddress,
    deliveryAddress = deliveryAddress?.let {
        AccessoryOrderDeliveryAddressView(
            address = it.address,
            ward = it.ward,
            city = it.city,
            note = it.note,
        )
    },
    subtotal = subtotal,
    discount = discount?.let { AccessoryOrderDiscountView(code = it.code, amount = it.amount) },
    total = total,
    lines = lines.map { it.toLineView() },
)
