package metaticket.order.calculate.promotion.meta_promo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaPromoDiscountByTicketAmountConfig constructor(
    val ticketAmountBelow: Int,
    val ticketAmountAbove: Int?,
    val discountPercent: Long?,
    val discountPrice: Long?,
    val id: String?,
    val needCheckQuantity: Boolean?,
    val originalQuantity: Int?,
    val quantityType: QuantityType?,
    val code: String? = null,
    val name: String? = null
)



enum class QuantityType {
    Ticket, Combo
}