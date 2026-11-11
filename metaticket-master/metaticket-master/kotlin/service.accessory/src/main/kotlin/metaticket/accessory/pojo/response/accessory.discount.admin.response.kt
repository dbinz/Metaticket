package metaticket.accessory.pojo.response

import metaticket.accessory.pojo.db.DiscountCodeDocument
import metaticket.accessory.pojo.db.DiscountTier

/** Tier snapshot on admin discount APIs. */
data class AdminDiscountTierResponse(
    val minOrderAmount: Long,
    val type: String,
    val value: Long,
    val maxDiscountAmount: Long?,
)

/**
 * Admin list/detail row for a discount campaign (GET/POST private accessory discount APIs).
 * Dates are epoch millis; [id] is the raw Mongo ObjectId hex.
 */
data class AdminDiscountListItem(
    val id: String,
    val code: String,
    val accessoryShowId: String?,
    val type: String,
    val value: Long,
    val rewardType: String?,
    val maxDiscountAmount: Long?,
    val minOrderAmount: Long,
    val tiers: List<AdminDiscountTierResponse>,
    val applyMode: String,
    val usageLimit: Int?,
    val usedCount: Int,
    val startAt: Long?,
    val endAt: Long?,
    val active: Boolean,
)

/** Maps a [DiscountCodeDocument] to the admin portal DTO. */
fun DiscountCodeDocument.toAdminListItem() = AdminDiscountListItem(
    id = id,
    code = code,
    accessoryShowId = accessoryShowId,
    type = type.name,
    value = value,
    rewardType = rewardType?.name,
    maxDiscountAmount = maxDiscountAmount,
    minOrderAmount = minOrderAmount,
    tiers = tiers.map { it.toAdminResponse() },
    applyMode = applyMode.name,
    usageLimit = usageLimit,
    usedCount = usedCount,
    startAt = startAt?.time,
    endAt = endAt?.time,
    active = active,
)

/** One failed row from `POST /private/accessory/discount/create/batch`. */
data class AdminDiscountBatchFailure(
    val code: String?,
    val message: String,
)

/**
 * Partial-success response for batch discount create:
 * [created] inserted docs, [failures] rejected codes (validation / duplicate).
 */
data class AdminDiscountBatchCreateResponse(
    val created: List<AdminDiscountListItem>,
    val failures: List<AdminDiscountBatchFailure>,
)

private fun DiscountTier.toAdminResponse() = AdminDiscountTierResponse(
    minOrderAmount = minOrderAmount,
    type = type.name,
    value = value,
    maxDiscountAmount = maxDiscountAmount,
)
