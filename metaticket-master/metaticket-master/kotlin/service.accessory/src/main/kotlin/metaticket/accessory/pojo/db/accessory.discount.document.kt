package metaticket.accessory.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

/**
 * How a campaign reaches the customer at checkout.
 *
 * All types currently use [Manual]: customer enters [DiscountCodeDocument.code] and presses Apply.
 * [Auto] is retained for backward-compatible documents but is not scanned by checkout/preview.
 */
enum class DiscountApplyMode { Manual, Auto }

/**
 * One spend threshold inside a [DiscountType.Tiered] campaign.
 *
 * [type] must be [DiscountType.Percentage] or [DiscountType.FixedAmount].
 * The highest [minOrderAmount] that the cart still meets is the only tier applied.
 */
data class DiscountTier(
    val minOrderAmount: Long,
    val type: DiscountType,
    val value: Long,
    val maxDiscountAmount: Long? = null,
)

/**
 * A discount campaign (collection `accessory-discount-code`) — percentage, fixed-amount, tiered, or
 * early-bird. Modelled per the design "Data Models → DiscountCode", extended for auto promos.
 *
 * Codes are stored in an **uppercase canonical form** and matched case-insensitively; a
 * case-insensitive unique index on [code] (see `DiscountDatabaseService`) guarantees no two codes
 * collide regardless of casing. Use [canonicalCode] when persisting/looking up a code.
 *
 * Validation rules (see [validationErrors]):
 * - `code` non-blank (backed by a case-insensitive unique index; stored uppercase-canonical).
 * - `Percentage`: `1 <= value <= 100`.
 * - `FixedAmount`: `value > 0`.
 * - `EarlyBird`: `rewardType` is Percentage|FixedAmount with matching value rules; show + window required; Auto.
 * - `Tiered`: non-empty [tiers], each tier Percentage|FixedAmount; Auto; highest eligible tier wins.
 * - `maxDiscountAmount` (when present) must be `> 0` (only meaningful for percentage rewards).
 * - `minOrderAmount >= 0` (coupon / early-bird floor; tiers use their own thresholds).
 * - `usageLimit` (when present) must be `> 0`; `null` = unlimited applications.
 * - `usedCount >= 0`.
 * - `startAt`/`endAt` window (when both present) must satisfy `startAt <= endAt`.
 */
data class DiscountCodeDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),
    val code: String,                      // unique (case-insensitive), stored uppercase (e.g. "THEFLOB10")
    @BsonRepresentation(BsonType.OBJECT_ID)
    val accessoryShowId: String? = null,   // null = valid for all accessory shows (EarlyBird must set show)
    val type: DiscountType,
    val value: Long = 0,                   // percent (1..100) or fixed VND (>0); unused for Tiered
    /** Reward shape for [DiscountType.EarlyBird] only (`Percentage` | `FixedAmount`). */
    val rewardType: DiscountType? = null,
    val maxDiscountAmount: Long? = null,   // cap for percentage rewards
    val minOrderAmount: Long = 0,          // min subtotal to qualify (coupons / EarlyBird)
    /** Ordered by caller; evaluation picks highest eligible [DiscountTier.minOrderAmount]. */
    val tiers: List<DiscountTier> = emptyList(),
    val applyMode: DiscountApplyMode = DiscountApplyMode.Manual,
    val usageLimit: Int? = null,           // null = unlimited
    var usedCount: Int = 0,                // atomic counter
    val startAt: Date? = null,
    val endAt: Date? = null,
    val active: Boolean = true,
    val version: Int = 1,
) {
    /** The uppercase canonical form of [code], used for storage and case-insensitive lookup. */
    val canonicalCode: String get() = code.trim().uppercase(Locale.ROOT)
}
