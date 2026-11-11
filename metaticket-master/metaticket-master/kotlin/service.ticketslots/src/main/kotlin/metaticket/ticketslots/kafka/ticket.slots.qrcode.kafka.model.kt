package metaticket.ticketslots.kafka

data class GenerateTicketSlotQRCodeEvent constructor(
    val slotId: String,
    val stateIdx: Int,
)