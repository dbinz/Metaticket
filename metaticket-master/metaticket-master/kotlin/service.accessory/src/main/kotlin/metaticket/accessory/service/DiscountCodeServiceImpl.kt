package metaticket.accessory.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import metaticket.accessory.pojo.db.AccessoryOrderDiscount
import metaticket.accessory.pojo.db.DiscountCodeDocument
import metaticket.accessory.pojo.db.DiscountTier
import metaticket.accessory.pojo.db.DiscountType
import org.bson.Document
import org.bson.types.ObjectId
import java.util.Date
import java.util.Locale

/**
 * Default [DiscountCodeService] implementation backed by the `accessory-discount-code` collection.
 */
class DiscountCodeServiceImpl(
    private val discountCodeCol: MongoCollection<DiscountCodeDocument>,
) : DiscountCodeService {

    /**
     * Pure, read-only evaluation of a typed discount code (any [DiscountType], including Tiered /
     * EarlyBird). Returns null when missing or not applicable. Never mutates usage (Req 4.7).
     */
    override suspend fun evaluate(code: String, showId: String, subtotal: Long): AccessoryOrderDiscount? {
        if (subtotal <= 0L) return null

        val canonical = code.trim().uppercase(Locale.ROOT)
        if (canonical.isEmpty()) return null

        val doc = withContext(Dispatchers.IO) {
            discountCodeCol.find(Filters.eq(DiscountCodeDocument::code.name, canonical))
        }.firstOrNull() ?: return null

        return evaluatePure(doc, showId, subtotal, Date())
    }

    /** Read-only preview for a typed code; mirrors [evaluate] applicability. */
    override suspend fun evaluatePreview(code: String, showId: String, subtotal: Long): DiscountPreviewResult? {
        val evaluated = evaluate(code, showId, subtotal) ?: return null
        val doc = withContext(Dispatchers.IO) {
            discountCodeCol.find(Filters.eq("_id", ObjectId(evaluated.discountCodeId)))
        }.firstOrNull() ?: return null
        return toPreview(doc, evaluated, subtotal)
    }

    /**
     * Resolves only the supplied typed [manualCode] (no silent Auto campaigns). Blank → null.
     */
    override suspend fun resolveBest(showId: String, subtotal: Long, manualCode: String?): AccessoryOrderDiscount? {
        if (manualCode.isNullOrBlank()) return null
        return evaluate(manualCode, showId, subtotal)
    }

    /** Preview for [resolveBest] — typed code only. */
    override suspend fun resolveBestPreview(
        showId: String,
        subtotal: Long,
        manualCode: String?,
    ): DiscountPreviewResult? {
        if (manualCode.isNullOrBlank()) return null
        return evaluatePreview(manualCode, showId, subtotal)
    }

    /**
     * Evaluate + reserve the typed code. Exhausted / inactive → null (full price — Req 6.12).
     */
    override suspend fun resolveAndReserveBest(
        showId: String,
        subtotal: Long,
        manualCode: String?,
    ): AccessoryOrderDiscount? {
        val evaluated = resolveBest(showId, subtotal, manualCode) ?: return null
        return if (reserve(evaluated.discountCodeId)) evaluated else null
    }

    /**
     * Atomically reserves one unit of usage (Req 6.11, 12.1, 12.2, 12.4, 12.5).
     * `usageLimit == null` means unlimited.
     */
    override suspend fun reserve(discountCodeId: String): Boolean = withContext(Dispatchers.IO) {
        val res = discountCodeCol.updateOne(
            Filters.and(
                Filters.eq("_id", ObjectId(discountCodeId)),
                Filters.eq(DiscountCodeDocument::active.name, true),
                Filters.or(
                    Filters.eq(DiscountCodeDocument::usageLimit.name, null),
                    Filters.expr(Document("\$lt", listOf("\$usedCount", "\$usageLimit"))),
                ),
            ),
            Updates.inc(DiscountCodeDocument::usedCount.name, 1),
        )
        res.modifiedCount == 1L
    }

    /** Atomically reverts one unit of usage on payment failure (Req 10.2, 12.3). */
    override suspend fun revert(discountCodeId: String) {
        withContext(Dispatchers.IO) {
            discountCodeCol.updateOne(
                Filters.and(
                    Filters.eq("_id", ObjectId(discountCodeId)),
                    Filters.gt(DiscountCodeDocument::usedCount.name, 0),
                ),
                Updates.inc(DiscountCodeDocument::usedCount.name, -1),
            )
        }
    }

    /** Maps a resolved winner + source doc into the storefront preview DTO. */
    private fun toPreview(
        doc: DiscountCodeDocument,
        evaluated: AccessoryOrderDiscount,
        subtotal: Long,
    ): DiscountPreviewResult = DiscountPreviewResult(
        code = evaluated.code,
        type = evaluated.type,
        value = displayValue(doc, subtotal),
        amount = evaluated.amount,
    )

    companion object {

        /**
         * Applicability rules: active, window, show scope, min order (non-tiered), remaining usage,
         * and type-specific gates (EarlyBird reward / at least one Tiered tier).
         */
        fun isApplicable(
            doc: DiscountCodeDocument,
            showId: String,
            subtotal: Long,
            now: Date,
        ): Boolean {
            if (!doc.active) return false

            if (doc.startAt != null && now.before(doc.startAt)) return false
            if (doc.endAt != null && now.after(doc.endAt)) return false

            if (doc.accessoryShowId != null && doc.accessoryShowId != showId) return false

            val limit = doc.usageLimit
            if (limit != null && doc.usedCount >= limit) return false

            when (doc.type) {
                DiscountType.Percentage, DiscountType.FixedAmount, DiscountType.EarlyBird -> {
                    if (subtotal < doc.minOrderAmount) return false
                    if (doc.type == DiscountType.EarlyBird) {
                        val reward = doc.rewardType
                        if (reward != DiscountType.Percentage && reward != DiscountType.FixedAmount) return false
                    }
                }
                DiscountType.Tiered -> {
                    if (selectHighestEligibleTier(doc.tiers, subtotal) == null) return false
                }
            }

            return true
        }

        /** Highest [DiscountTier.minOrderAmount] still `<= subtotal`, or null if none. */
        fun selectHighestEligibleTier(tiers: List<DiscountTier>, subtotal: Long): DiscountTier? =
            tiers.filter { it.minOrderAmount <= subtotal }.maxByOrNull { it.minOrderAmount }

        /**
         * Amount computation:
         * - Percentage / EarlyBird(%): `min(subtotal * value / 100, maxDiscountAmount ?: ∞)`
         * - FixedAmount / EarlyBird(fixed): `min(value, subtotal)`
         * - Tiered: same formulas on the highest eligible tier only.
         */
        fun computeAmount(doc: DiscountCodeDocument, subtotal: Long): Long {
            return when (doc.type) {
                DiscountType.Percentage ->
                    computeRewardAmount(DiscountType.Percentage, doc.value, doc.maxDiscountAmount, subtotal)
                DiscountType.FixedAmount ->
                    computeRewardAmount(DiscountType.FixedAmount, doc.value, null, subtotal)
                DiscountType.EarlyBird -> {
                    val reward = doc.rewardType ?: return 0L
                    computeRewardAmount(reward, doc.value, doc.maxDiscountAmount, subtotal)
                }
                DiscountType.Tiered -> {
                    val tier = selectHighestEligibleTier(doc.tiers, subtotal) ?: return 0L
                    computeRewardAmount(tier.type, tier.value, tier.maxDiscountAmount, subtotal)
                }
            }
        }

        fun computeRewardAmount(
            type: DiscountType,
            value: Long,
            maxDiscountAmount: Long?,
            subtotal: Long,
        ): Long {
            val raw = when (type) {
                DiscountType.Percentage -> {
                    val percentAmount = subtotal * value / 100L
                    val cap = maxDiscountAmount ?: Long.MAX_VALUE
                    minOf(percentAmount, cap)
                }
                DiscountType.FixedAmount -> minOf(value, subtotal)
                DiscountType.Tiered, DiscountType.EarlyBird -> 0L
            }
            return raw.coerceIn(0L, subtotal)
        }

        /**
         * Pure form of evaluate: given an already-loaded [doc], return the resolved discount or
         * null when not applicable. Never mutates usage (Req 4.7).
         */
        fun evaluatePure(
            doc: DiscountCodeDocument,
            showId: String,
            subtotal: Long,
            now: Date,
        ): AccessoryOrderDiscount? {
            if (subtotal <= 0L) return null
            if (!isApplicable(doc, showId, subtotal, now)) return null
            val amount = computeAmount(doc, subtotal)
            if (amount <= 0L) return null
            return AccessoryOrderDiscount(
                code = doc.canonicalCode,
                type = doc.type,
                amount = amount,
                discountCodeId = doc.id,
            )
        }

        fun canReserve(active: Boolean, usageLimit: Int?, usedCount: Int): Boolean =
            active && (usageLimit == null || usedCount < usageLimit)

        fun canRevert(usedCount: Int): Boolean = usedCount > 0

        /** Display value for preview (winning tier value when Tiered). */
        fun displayValue(doc: DiscountCodeDocument, subtotal: Long): Long =
            when (doc.type) {
                DiscountType.Tiered -> selectHighestEligibleTier(doc.tiers, subtotal)?.value ?: 0L
                else -> doc.value
            }
    }
}
