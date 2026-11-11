package metaticket.order.calculate.promotion.meta_promo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Configuration for capping the maximum discount amount.
 *
 * Can be applied:
 * - Per promotion type (e.g., cap early bird discount at 100,000đ)
 * - To the total order discount (cap all combined discounts)
 *
 * @param maxDiscountPerType - map of promotion type to max discount amount (optional)
 *   Keys: "discountByDate", "discountByTicketAmount", "discountByPriceCategoryAmount", "promotionCode"
 * @param maxDiscountTotal - max total discount for the entire order (optional, null = no limit)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaPromoMaxDiscountAmountConfig constructor(
    val maxDiscountPerType: Map<String, Long>? = null,
    val maxDiscountTotal: Long? = null
)
