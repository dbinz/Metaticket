package metaticket.accessory.pojo.response

import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryPickupPoint
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.AccessoryShowSession
import metaticket.accessory.pojo.db.AccessoryVariant
import metaticket.accessory.pojo.db.AccessoryFulfillmentSettings
import metaticket.accessory.pojo.db.optionMap
import metaticket.accessory.pojo.db.resolvedAttributes

/**
 * Admin list/detail row for an accessory show
 * (`GET /private/accessory/show/fetch/all`, fetch-by-id, create/update/status responses).
 *
 * Unlike public DTOs, [id] is the raw Mongo `ObjectId` hex (portal admin does not use PublicId
 * checksum encoding).
 */
data class AdminAccessoryShowListItem(
    val id: String,
    val code: String,
    val codePrefix: String,
    val name: String,
    /** Canonical public slug when set; null when the show has none. */
    val slug: String?,
    val description: String?,
    val bannerImageUrl: String?,
    val status: String,
    /** Planned sale-open epoch millis when status is Waiting; null when unset. */
    val saleOpenAt: Long? = null,
    val sessions: List<AdminAccessoryShowSessionResponse>,
    val pickupNote: String?,
    val deliveryNote: String?,
    val pgAccountCode: String?,
    val allowedPaymentMethods: List<String>,
    val mailPlugin: String,
    val mailTemplateId: String?,
    val fulfillment: AdminFulfillmentSettingsResponse,
    val pinOrder: Int?,
    val productCount: Int,
    val createdAt: Long,
    val lastModified: Long,
)

/** Session snapshot on the admin surface (`datetime` = epoch millis; city is an object). */
data class AdminAccessoryShowSessionResponse(
    val id: String,
    val city: AdminAccessoryCityResponse,
    val venue: String,
    val datetime: Long,
)

/** City object returned to the admin portal (matches ticket CityModel shape). */
data class AdminAccessoryCityResponse(
    val id: String,
    val name: String,
    val code: String,
)

/** Fulfillment modes exposed to the admin portal. */
data class AdminFulfillmentSettingsResponse(
    val enablePickupAtShow: Boolean = true,
    val enableMetaTicketPickup: Boolean = false,
    val enableDelivery: Boolean = false,
    val pickupAtShowSessionIds: List<String> = emptyList(),
    val pickupPoints: List<AdminPickupPointResponse> = emptyList(),
)

/** Per-show META Ticket / offline pickup location (admin). */
data class AdminPickupPointResponse(
    val id: String,
    val name: String,
    val address: String,
    val note: String? = null,
)

/** Maps a catalog session to the admin session DTO. */
fun AccessoryShowSession.toAdminResponse() = AdminAccessoryShowSessionResponse(
    id = sessionId(),
    city = AdminAccessoryCityResponse(
        id = city.id,
        name = city.name,
        code = city.code,
    ),
    venue = venue,
    datetime = datetime.time,
)

/** Maps persisted fulfillment settings to the admin response DTO. */
fun AccessoryFulfillmentSettings.toAdminResponse() = AdminFulfillmentSettingsResponse(
    enablePickupAtShow = enablePickupAtShow,
    enableMetaTicketPickup = enableMetaTicketPickup,
    enableDelivery = enableDelivery,
    pickupAtShowSessionIds = if (enablePickupAtShow) pickupAtShowSessionIds else emptyList(),
    pickupPoints = if (enableMetaTicketPickup) pickupPoints.map { it.toAdminResponse() } else emptyList(),
)

/** Maps one pickup point document to the admin DTO. */
fun AccessoryPickupPoint.toAdminResponse() = AdminPickupPointResponse(
    id = id,
    name = name,
    address = address,
    note = note,
)

/** Maps a show document + product count into an admin list/detail row. */
fun AccessoryShowDocument.toAdminListItem(productCount: Int): AdminAccessoryShowListItem {
    val sessionResponses = sessions.map { it.toAdminResponse() }
    return AdminAccessoryShowListItem(
        id = id,
        code = code,
        codePrefix = codePrefix,
        name = name,
        slug = slug,
        description = description,
        bannerImageUrl = bannerImageUrl,
        status = status.name,
        saleOpenAt = saleOpenAt?.time,
        sessions = sessionResponses,
        pickupNote = pickupNote,
        deliveryNote = deliveryNote,
        pgAccountCode = pgAccountCode,
        allowedPaymentMethods = allowedPaymentMethods,
        mailPlugin = mailPlugin,
        mailTemplateId = mailTemplateId,
        fulfillment = resolvedFulfillment().toAdminResponse(),
        pinOrder = pinOrder,
        productCount = productCount,
        createdAt = createdAt.time,
        lastModified = lastModified.time,
    )
}

const val ACCESSORY_PIN_SLOT_MAX = 5

/** Compact accessory-show card for the pin board / picker. */
data class AccessoryPinShowSummary(
    val id: String,
    val name: String,
    val code: String,
    val status: String,
    val bannerImageUrl: String,
    val pinOrder: Int?,
)

/** Occupied pin slot; vacant ranks are omitted so the board length is flexible. */
data class AccessoryPinSlotResponse(
    val pinOrder: Int,
    val show: AccessoryPinShowSummary?,
)

data class AccessoryPinBoardResponse(
    val slots: List<AccessoryPinSlotResponse>,
    val maxSlots: Int = ACCESSORY_PIN_SLOT_MAX,
)

data class AccessoryPinCandidatePage(
    val content: List<AccessoryPinShowSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

/** Maps a show onto the accessory pin-board card. */
fun AccessoryShowDocument.toPinSummary(): AccessoryPinShowSummary = AccessoryPinShowSummary(
    id = id,
    name = name,
    code = code,
    status = status.name,
    bannerImageUrl = bannerImageUrl.orEmpty(),
    pinOrder = pinOrder,
)

/** Admin product row (`GET /private/accessory/fetch/all?showId=`). Raw ObjectIds. */
data class AdminAccessoryListItem(
    val id: String,
    val accessoryShowId: String,
    val sku: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val imageUrls: List<String>,
    val price: Long,
    val currency: String,
    val stock: Int,
    val hasVariants: Boolean,
    val variantCount: Int,
    val active: Boolean,
    /** Up to 2 named dimensions (attr1/attr2). Empty for single-SKU or label-only variants. */
    val attributes: List<AdminAccessoryAttributeDefResponse> = emptyList(),
    val attr1Name: String? = null,
    val attr2Name: String? = null,
    val minOrderQty: Int? = null,
    val maxOrderQty: Int? = null,
    val maxPerUserQty: Int? = null,
    val variants: List<AdminAccessoryVariantResponse>,
    val createdAt: Long,
    val lastModified: Long,
)

/** Named attribute dimension on an admin product. */
data class AdminAccessoryAttributeDefResponse(
    val key: String,
    val name: String,
)

/** Variant snapshot on the admin product list. */
data class AdminAccessoryVariantResponse(
    val key: String,
    val label: String,
    val stock: Int,
    val attr1: String?,
    val attr2: String?,
    val imageUrl: String?,
    val options: Map<String, String> = emptyMap(),
    val active: Boolean,
)

/** Maps a catalog accessory + variants into an admin list row. */
fun AccessoryDocument.toAdminListItem() = AdminAccessoryListItem(
    id = id,
    accessoryShowId = accessoryShowId,
    sku = sku,
    name = name,
    description = description,
    imageUrl = imageUrl,
    imageUrls = imageUrls,
    price = price,
    currency = currency,
    stock = stock,
    hasVariants = hasVariants,
    variantCount = variants.size,
    active = active,
    attributes = resolvedAttributes().map { AdminAccessoryAttributeDefResponse(it.key, it.name) },
    attr1Name = attr1Name,
    attr2Name = attr2Name,
    minOrderQty = minOrderQty,
    maxOrderQty = maxOrderQty,
    maxPerUserQty = maxPerUserQty,
    variants = variants.map { it.toAdminResponse() },
    createdAt = createdAt.time,
    lastModified = lastModified.time,
)

/** Maps one variant for the admin product list. */
fun AccessoryVariant.toAdminResponse() = AdminAccessoryVariantResponse(
    key = key,
    label = label,
    stock = stock,
    attr1 = attr1,
    attr2 = attr2,
    imageUrl = imageUrl,
    options = optionMap(),
    active = active,
)
