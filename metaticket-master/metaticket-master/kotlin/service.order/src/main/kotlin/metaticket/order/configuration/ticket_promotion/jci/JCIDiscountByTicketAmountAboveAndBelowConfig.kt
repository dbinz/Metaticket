package metaticket.order.configuration.ticket_promotion.jci

data class JCIDiscountByTicketAmountAboveAndBelowConfig(
        val ticketAmountBelow: Int,
        val ticketAmountAbove: Int?,
        val discountPerTicket: Long
)