package metaticket.accessory.pojo.request

import metaticket.accessory.pojo.db.AccessoryCityDocument
import metaticket.accessory.pojo.db.AccessoryFulfillmentSettings
import metaticket.accessory.pojo.db.AccessoryPickupPoint
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.AccessoryShowSession
import metaticket.accessory.pojo.db.AccessoryShowStatus
import metaticket.accessory.service.SlugCheck
import metaticket.accessory.service.SlugNormalizer
import metaticket.accessory.service.SlugValidator
import org.bson.types.ObjectId
import java.util.Date

/**
 * Admin upsert body for create/update accessory show
 * (`POST /private/accessory/show/create|update`).
 *
 * Session [datetime] is epoch millis. City is the portal City object shape.
 */
data class AdminAccessoryShowUpsertRequest(
    val code: String? = null,
    val codePrefix: String? = null,
    val name: String? = null,
    /** Optional Canonical_Slug for public `/san-pham/{slug}` links; blank clears on update. */
    val slug: String? = null,
    val description: String? = null,
    val bannerImageUrl: String? = null,
    val sessions: List<AdminAccessoryShowSessionRequest> = emptyList(),
    val status: String? = null,
    /** Epoch millis for planned sale open; null/0 clears. Meaningful when status is Waiting. */
    val saleOpenAt: Long? = null,
    val pickupNote: String? = null,
    /** Optional plain-text tip when [AdminFulfillmentSettingsRequest.enableDelivery] is on. */
    val deliveryNote: String? = null,
    val pgAccountCode: String? = null,
    val allowedPaymentMethods: List<String> = emptyList(),
    val mailPlugin: String? = null,
    val mailTemplateId: String? = null,
    val fulfillment: AdminFulfillmentSettingsRequest? = null,
)

/** Session row from the admin create/edit form. */
data class AdminAccessoryShowSessionRequest(
    val id: String? = null,
    val city: AdminAccessoryCityRequest? = null,
    val venue: String? = null,
    val datetime: Long? = null,
)

/** City object nested under a session (portal `CityModel`). */
data class AdminAccessoryCityRequest(
    val id: String? = null,
    val name: String? = null,
    val code: String? = null,
)

/** Fulfillment tab payload from the admin form. */
data class AdminFulfillmentSettingsRequest(
    val enablePickupAtShow: Boolean = false,
    val enableMetaTicketPickup: Boolean = false,
    val enableDelivery: Boolean = false,
    val pickupAtShowSessionIds: List<String> = emptyList(),
    val pickupPoints: List<AdminPickupPointRequest> = emptyList(),
)

/** One MetaTicket pickup point. */
data class AdminPickupPointRequest(
    val id: String? = null,
    val name: String? = null,
    val address: String? = null,
    val note: String? = null,
)

/** Body for `POST /private/accessory/show/status`. */
data class AdminAccessoryShowStatusRequest(
    val status: String? = null,
)

/**
 * Body for `POST /private/accessory/show/pin`.
 * [pinOrder] null/absent clears the pin; when set must be a positive integer.
 */
data class AdminAccessoryShowPinRequest(
    val pinOrder: Int? = null,
)

/** One accessory pin slot from the admin board confirm payload. */
data class AccessoryPinSlotInput(
    val pinOrder: Int,
    val showId: String? = null,
)

/** Apply a variable-length pin board (1..[ACCESSORY_PIN_SLOT_MAX] filled shows). */
data class ConfirmAccessoryPinsRequest(
    val slots: List<AccessoryPinSlotInput> = emptyList(),
)

/**
 * Collect validation errors for an admin upsert before it hits Mongo.
 * Empty list means the payload is acceptable to persist.
 */
fun AdminAccessoryShowUpsertRequest.validationErrors(requireCode: Boolean): List<String> {
    val errors = mutableListOf<String>()
    if (requireCode && code.isNullOrBlank()) errors += "`code` is required"
    if (codePrefix.isNullOrBlank()) errors += "`codePrefix` is required"
    if (name.isNullOrBlank()) errors += "`name` is required"
    when (val check = SlugValidator.checkFormat(slug)) {
        is SlugCheck.Invalid -> errors += "invalid slug (${check.reason})"
        SlugCheck.Valid -> Unit
    }
    status?.let {
        if (runCatching { AccessoryShowStatus.valueOf(it) }.isFailure) {
            errors += "`status` must be Open, Waiting, or Closed"
        }
    }
    if (status == AccessoryShowStatus.Waiting.name) {
        saleOpenAt?.takeIf { it > 0L }?.let { at ->
            if (at <= System.currentTimeMillis()) {
                errors += "`saleOpenAt` must be in the future"
            }
        }
    }
    sessions.forEachIndexed { index, session ->
        val cityName = session.city?.name?.trim().orEmpty()
        if (cityName.isBlank() && session.city?.code.isNullOrBlank()) {
            errors += "sessions[$index].city name/code is required"
        }
        if (session.venue.isNullOrBlank()) errors += "sessions[$index].venue is required"
        if (session.datetime == null || session.datetime <= 0L) {
            errors += "sessions[$index].datetime is required"
        }
    }
    errors += (fulfillment ?: AdminFulfillmentSettingsRequest()).fulfillmentValidationErrors(sessions)
    return errors
}

/** Validates fulfillment flags against the session list on the same payload. */
fun AdminFulfillmentSettingsRequest.fulfillmentValidationErrors(
    sessions: List<AdminAccessoryShowSessionRequest>,
): List<String> {
    val errors = mutableListOf<String>()
    if (!enablePickupAtShow && !enableMetaTicketPickup && !enableDelivery) {
        errors += "at least one fulfillment mode must be enabled"
    }
    val sessionIds = sessions.map { it.resolvedSessionId() }.toSet()
    if (enablePickupAtShow) {
        if (pickupAtShowSessionIds.isEmpty()) {
            errors += "pickupAtShowSessionIds required when enablePickupAtShow"
        } else {
            pickupAtShowSessionIds.forEach { sid ->
                if (sid !in sessionIds) errors += "pickupAtShowSessionIds contains unknown session id: $sid"
            }
        }
    }
    if (enableMetaTicketPickup) {
        if (pickupPoints.isEmpty()) {
            errors += "pickupPoints required when enableMetaTicketPickup"
        } else {
            pickupPoints.forEachIndexed { index, point ->
                if (point.name.isNullOrBlank()) errors += "pickupPoints[$index].name is required"
                if (point.address.isNullOrBlank()) errors += "pickupPoints[$index].address is required"
            }
        }
    }
    return errors
}

/** Converts optional epoch millis into a stored sale-open instant (0/null → unset). */
private fun Long?.toSaleOpenDate(): Date? = this?.takeIf { it > 0L }?.let { Date(it) }

/** Canonical slug from admin input, or null when blank / absent. */
private fun AdminAccessoryShowUpsertRequest.canonicalSlugOrNull(): String? =
    slug?.trim()?.takeIf { it.isNotEmpty() }?.let { SlugNormalizer.normalize(it) }

/** Maps admin upsert JSON into a new [AccessoryShowDocument] (create path). */
fun AdminAccessoryShowUpsertRequest.toNewDocument(): AccessoryShowDocument {
    val now = Date()
    val sessionDocs = sessions.map { it.toDocument() }
    return AccessoryShowDocument(
        id = ObjectId().toHexString(),
        code = code!!.trim(),
        codePrefix = codePrefix!!.trim(),
        name = name!!.trim(),
        slug = canonicalSlugOrNull(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        bannerImageUrl = bannerImageUrl?.trim()?.takeIf { it.isNotEmpty() },
        sessions = sessionDocs,
        status = status?.let { AccessoryShowStatus.valueOf(it) } ?: AccessoryShowStatus.Open,
        saleOpenAt = saleOpenAt.toSaleOpenDate(),
        pickupNote = pickupNote?.trim()?.takeIf { it.isNotEmpty() },
        deliveryNote = deliveryNote?.trim()?.takeIf { it.isNotEmpty() },
        pgAccountCode = pgAccountCode?.trim()?.takeIf { it.isNotEmpty() },
        allowedPaymentMethods = allowedPaymentMethods.map { it.trim() }.filter { it.isNotEmpty() },
        mailPlugin = mailPlugin?.trim()?.takeIf { it.isNotEmpty() } ?: "PluginMailTemplateAccessoryOrder",
        mailTemplateId = mailTemplateId?.trim()?.takeIf { it.isNotEmpty() },
        fulfillment = (fulfillment ?: AdminFulfillmentSettingsRequest()).toDocument(sessionDocs),
        version = 1,
        createdAt = now,
        lastModified = now,
    )
}

/**
 * Maps admin upsert JSON onto an existing document (update path).
 * Preserves id/createdAt; bumps version; keeps [existing].code (code is immutable after create).
 * Blank [slug] clears the stored slug.
 */
fun AdminAccessoryShowUpsertRequest.toUpdatedDocument(existing: AccessoryShowDocument): AccessoryShowDocument {
    val sessionDocs = sessions.map { it.toDocument() }
    return existing.copy(
        codePrefix = codePrefix!!.trim(),
        name = name!!.trim(),
        slug = canonicalSlugOrNull(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        bannerImageUrl = bannerImageUrl?.trim()?.takeIf { it.isNotEmpty() },
        sessions = sessionDocs,
        status = status?.let { AccessoryShowStatus.valueOf(it) } ?: existing.status,
        saleOpenAt = saleOpenAt.toSaleOpenDate(),
        pickupNote = pickupNote?.trim()?.takeIf { it.isNotEmpty() },
        deliveryNote = deliveryNote?.trim()?.takeIf { it.isNotEmpty() },
        pgAccountCode = pgAccountCode?.trim()?.takeIf { it.isNotEmpty() },
        allowedPaymentMethods = allowedPaymentMethods.map { it.trim() }.filter { it.isNotEmpty() },
        mailPlugin = mailPlugin?.trim()?.takeIf { it.isNotEmpty() } ?: existing.mailPlugin,
        mailTemplateId = mailTemplateId?.trim()?.takeIf { it.isNotEmpty() },
        fulfillment = (fulfillment ?: AdminFulfillmentSettingsRequest()).toDocument(sessionDocs),
        version = existing.version + 1,
        lastModified = Date(),
    )
}

/** Converts one admin session row into the catalog document session. */
fun AdminAccessoryShowSessionRequest.toDocument(): AccessoryShowSession {
    val cityDoc = AccessoryCityDocument(
        id = city?.id?.trim().orEmpty(),
        name = city?.name?.trim().orEmpty(),
        code = city?.code?.trim().orEmpty(),
    )
    val explicitId = id?.trim().orEmpty()
    val venueValue = venue?.trim().orEmpty()
    val whenMillis = datetime ?: 0L
    return AccessoryShowSession(
        id = explicitId.ifBlank {
            "${cityDoc.displayName()}|$venueValue|$whenMillis"
        },
        city = cityDoc,
        venue = venueValue,
        datetime = Date(whenMillis),
    )
}

/** Stable id used when validating pickupAtShowSessionIds against the request sessions. */
fun AdminAccessoryShowSessionRequest.resolvedSessionId(): String {
    val explicit = id?.trim().orEmpty()
    if (explicit.isNotEmpty()) return explicit
    val cityDoc = AccessoryCityDocument(
        id = city?.id?.trim().orEmpty(),
        name = city?.name?.trim().orEmpty(),
        code = city?.code?.trim().orEmpty(),
    )
    return "${cityDoc.displayName()}|${venue?.trim().orEmpty()}|${datetime ?: 0L}"
}

/** Maps fulfillment request → persisted settings; clears unused lists when a mode is off. */
fun AdminFulfillmentSettingsRequest.toDocument(
    sessionDocs: List<AccessoryShowSession>,
): AccessoryFulfillmentSettings {
    val sessionIds = sessionDocs.map { it.sessionId() }.toSet()
    return AccessoryFulfillmentSettings(
        enablePickupAtShow = enablePickupAtShow,
        enableMetaTicketPickup = enableMetaTicketPickup,
        enableDelivery = enableDelivery,
        pickupAtShowSessionIds = if (enablePickupAtShow) {
            pickupAtShowSessionIds.map { it.trim() }.filter { it in sessionIds }
        } else {
            emptyList()
        },
        pickupPoints = if (enableMetaTicketPickup) {
            pickupPoints.map { point ->
                AccessoryPickupPoint(
                    id = point.id?.trim()?.takeIf { it.isNotEmpty() } ?: ObjectId().toHexString(),
                    name = point.name!!.trim(),
                    address = point.address!!.trim(),
                    note = point.note?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        } else {
            emptyList()
        },
    )
}
