package metaticket.order.configuration.ticket_promotion.mma

data class MMADiscountByTicketAmountConfig(val ticketAmountBelow: Int, val ticketAmountAbove: Int?, val discountPercent: Long)