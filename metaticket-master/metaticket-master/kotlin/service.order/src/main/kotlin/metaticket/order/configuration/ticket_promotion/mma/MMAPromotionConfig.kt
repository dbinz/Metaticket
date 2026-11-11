package metaticket.order.configuration.ticket_promotion.mma

data class MMAPromotionConfig(val discountByDates: List<MMADiscountByDateConfig>, val discountByTicketAmounts: List<MMADiscountByTicketAmountConfig>)