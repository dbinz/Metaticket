package metaticket.order.configuration.ticket_promotion.jci

data class JCIDiscountByTicketAmountConfig(
        val ticketAmount: Int,
        val discountPerTicket: Long
)