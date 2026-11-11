package metaticket.order.configuration.ticket_promotion.jci

import metaticket.order.configuration.ticket_promotion.PromotionCodeConfig


data class JCIPromotionConfig(
        val discountByDates: List<JCIDiscountByDateConfig>,
        val discountByTicketAmount: JCIDiscountByTicketAmountConfig,
        val promotionCodes: List<PromotionCodeConfig>
)