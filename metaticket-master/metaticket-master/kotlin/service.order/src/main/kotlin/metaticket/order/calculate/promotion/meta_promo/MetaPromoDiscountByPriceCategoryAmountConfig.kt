package metaticket.order.calculate.promotion.meta_promo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaPromoDiscountByPriceCategoryAmountConfig constructor(
    val conditions: List<MetaPromoPriceCatConditionConfig>,
    val discountPercent: Long?,
    val discountPrice: Long?,
    val applyDiscountOn: ApplyDiscountOnType? // "TotalOrder" or "MatchedItems"
)

enum class ApplyDiscountOnType {
    TotalOrder,
    MatchedItems
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaPromoPriceCatConditionConfig constructor(
    val priceCat: String,
    val minQuantity: Int,
    val maxQuantity: Int?
)