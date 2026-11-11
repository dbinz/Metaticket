package metaticket.order.configuration.ticket_promotion.jci

data class JCIDiscountByDateConfig(
        val name: String,
        val date: String,
        val discount: Long,
        val promotionCodes: List<JCIPromotionCodeConfig>?,
        val discountByTicketAmount: List<JCIDiscountByTicketAmountAboveAndBelowConfig>?
)