package metaticket.order.calculate.promotion.meta_promo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaPromoDiscountByDateConfig constructor(
        val name: String,
        val date: Long,
        val discountPrice: Long?,
        val discountPercent: Long?,
        val promotionCodes: List<MetaPromoPromotionCodeConfig>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
class MetaPromoPromotionCodeConfig constructor(
        val name: String,
        val code: String,
        val discountPercent: Long?,
        val discountPrice: Long?,
        val needCheckQuantity: Boolean?,
        val originalQuantity: Int?
)