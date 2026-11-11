package metaticket.accessory.pojo.request

import metaticket.accessory.pojo.db.DiscountApplyMode
import metaticket.accessory.pojo.db.DiscountCodeDocument
import metaticket.accessory.pojo.db.DiscountTier
import metaticket.accessory.pojo.db.DiscountType
import java.util.Date
import java.util.Locale

/** One tier on admin create/update payloads. */
data class AdminDiscountTierRequest(
    val minOrderAmount: Long = 0,
    val type: String? = null,
    val value: Long = 0,
    val maxDiscountAmount: Long? = null,
)

/**
 * Body for `POST /private/accessory/discount/create|update`.
 * [code] required on create; immutable on update. [usedCount] is never accepted from the client.
 */
data class AdminDiscountUpsertRequest(
    val code: String? = null,
    val accessoryShowId: String? = null,
    val type: String? = null,
    val value: Long = 0,
    val rewardType: String? = null,
    val maxDiscountAmount: Long? = null,
    val minOrderAmount: Long = 0,
    val tiers: List<AdminDiscountTierRequest> = emptyList(),
    val applyMode: String? = null,
    val usageLimit: Int? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val active: Boolean = true,
)

/** Body for `POST /private/accessory/discount/active`. */
data class AdminDiscountActiveRequest(
    val active: Boolean? = null,
)

/**
 * Body for `POST /private/accessory/discount/create/batch`.
 * Each item is validated like a single create; max [MAX_BATCH_SIZE] codes per request.
 */
data class AdminDiscountBatchCreateRequest(
    val items: List<AdminDiscountUpsertRequest> = emptyList(),
) {
    companion object {
        const val MAX_BATCH_SIZE = 200
    }
}

/** Parses [DiscountType] from admin string payload. */
fun parseDiscountType(raw: String?): DiscountType? =
    raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { DiscountType.valueOf(it) }.getOrNull() }

/** Parses [DiscountApplyMode] from admin string payload. */
fun parseDiscountApplyMode(raw: String?): DiscountApplyMode? =
    raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { DiscountApplyMode.valueOf(it) }.getOrNull() }

/** Lightweight request checks before document validation. */
fun AdminDiscountUpsertRequest.adminFieldErrors(requireCode: Boolean): List<String> {
    val errors = mutableListOf<String>()
    if (requireCode && code.isNullOrBlank()) {
        errors += "discount `code` is required"
    }
    if (accessoryShowId.isNullOrBlank()) {
        errors += "discount `accessoryShowId` is required"
    }
    if (parseDiscountType(type) == null) {
        errors += "discount `type` must be Percentage|FixedAmount|Tiered|EarlyBird"
    }
    return errors
}

/** Maps a create body into a new [DiscountCodeDocument] (canonical uppercase code). */
fun AdminDiscountUpsertRequest.toNewDiscountDocument(): DiscountCodeDocument {
    val discountType = parseDiscountType(type)!!
    val apply = resolveApplyMode(discountType)
    return DiscountCodeDocument(
        code = code!!.trim().uppercase(Locale.ROOT),
        accessoryShowId = accessoryShowId!!.trim(),
        type = discountType,
        value = value,
        rewardType = parseDiscountType(rewardType),
        maxDiscountAmount = maxDiscountAmount,
        minOrderAmount = minOrderAmount,
        tiers = tiers.map { it.toTier() },
        applyMode = apply,
        usageLimit = usageLimit,
        usedCount = 0,
        startAt = startAt?.let { Date(it) },
        endAt = endAt?.let { Date(it) },
        active = active,
    )
}

/**
 * Maps an update body onto [existing] — preserves id, code, and usedCount.
 */
fun AdminDiscountUpsertRequest.toUpdatedDiscountDocument(existing: DiscountCodeDocument): DiscountCodeDocument {
    val discountType = parseDiscountType(type)!!
    val apply = resolveApplyMode(discountType)
    return existing.copy(
        accessoryShowId = accessoryShowId!!.trim(),
        type = discountType,
        value = value,
        rewardType = parseDiscountType(rewardType),
        maxDiscountAmount = maxDiscountAmount,
        minOrderAmount = minOrderAmount,
        tiers = tiers.map { it.toTier() },
        applyMode = apply,
        usageLimit = usageLimit,
        startAt = startAt?.let { Date(it) },
        endAt = endAt?.let { Date(it) },
        active = active,
        usedCount = existing.usedCount,
        code = existing.code,
        id = existing.id,
        version = existing.version,
    )
}

private fun AdminDiscountUpsertRequest.resolveApplyMode(discountType: DiscountType): DiscountApplyMode =
    // All campaign types are code-entry only — never auto-apply without the customer pressing Apply.
    parseDiscountApplyMode(applyMode) ?: DiscountApplyMode.Manual

private fun AdminDiscountTierRequest.toTier(): DiscountTier = DiscountTier(
    minOrderAmount = minOrderAmount,
    type = parseDiscountType(type) ?: DiscountType.FixedAmount,
    value = value,
    maxDiscountAmount = maxDiscountAmount,
)
