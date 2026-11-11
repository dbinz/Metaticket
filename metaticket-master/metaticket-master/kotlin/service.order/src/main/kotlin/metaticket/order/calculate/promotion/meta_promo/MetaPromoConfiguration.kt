package metaticket.order.calculate.promotion.meta_promo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// Promotion configs are stored as JSON and parsed on every checkout. Ignoring unknown fields lets a
// rolled-back build read configs written by a newer one (same on every MetaPromo*Config class).
@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaPromoConfiguration constructor(
    val discountByDates: List<MetaPromoDiscountByDateConfig>?,
    val discountByTicketAmounts: List<MetaPromoDiscountByTicketAmountConfig>?,
    val discountByPriceCategoryAmounts: List<MetaPromoDiscountByPriceCategoryAmountConfig>?,
    val promotionCodes: List<MetaPromoPromotionCodeConfig>?,
    val maxDiscountAmount: MetaPromoMaxDiscountAmountConfig? = null
)