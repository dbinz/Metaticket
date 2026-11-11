package metaticket.accessory.service

import metaticket.accessory.pojo.db.AccessoryOrderDiscount
import metaticket.accessory.pojo.db.DiscountType

/**
 * Read-only preview projection for the discount-validate endpoint. Carries the campaign's
 * [type] and display [value] (percent, fixed VND, or winning-tier value) alongside the resolved
 * [amount], so the storefront can render a message like "giảm 10%" or "giảm 50.000đ".
 */
data class DiscountPreviewResult(
    val code: String,
    val type: DiscountType,
    val value: Long,
    val amount: Long,
)

/**
 * Validates, evaluates, and atomically reserves/reverts discount-code usage.
 *
 * - [evaluate] is a **pure** read-only calculation for a typed code (any type, including Tiered /
 *   EarlyBird). It never mutates usage (Req 4.7).
 * - There is no silent Auto-apply path: blank code → no discount. Customer must type a code.
 * - [reserve] / [revert] mutate `usedCount` under Mongo filters (`usageLimit == null` = unlimited).
 */
interface DiscountCodeService {

    /**
     * Pure calculation for a typed coupon / campaign code. Returns null when missing or not
     * applicable. Never mutates usage (Req 4.7).
     */
    suspend fun evaluate(code: String, showId: String, subtotal: Long): AccessoryOrderDiscount?

    /**
     * Like [evaluate] but returns a [DiscountPreviewResult] for display.
     */
    suspend fun evaluatePreview(code: String, showId: String, subtotal: Long): DiscountPreviewResult?

    /**
     * Resolves only the supplied typed [manualCode]. Blank → null (no Auto scan).
     */
    suspend fun resolveBest(showId: String, subtotal: Long, manualCode: String?): AccessoryOrderDiscount?

    /**
     * Read-only preview of [resolveBest] (typed code only).
     */
    suspend fun resolveBestPreview(showId: String, subtotal: Long, manualCode: String?): DiscountPreviewResult?

    /**
     * Evaluate + [reserve] the typed code. Exhausted/inactive → null / full price (Req 6.12).
     */
    suspend fun resolveAndReserveBest(showId: String, subtotal: Long, manualCode: String?): AccessoryOrderDiscount?

    /**
     * Atomically increments `usedCount` iff the code is still active and within its `usageLimit`.
     * Returns `false` when the code is exhausted/inactive.
     */
    suspend fun reserve(discountCodeId: String): Boolean

    /**
     * Atomically decrements `usedCount` (never below 0). Used on payment failure.
     */
    suspend fun revert(discountCodeId: String)
}
