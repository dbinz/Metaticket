package metaticket.ticketslots.model

enum class ZNSType { transferTicketSlot, notifyTicketSlot }

data class ZNSConfig constructor(
    val plugin: String,
    val templateId: String,
    val failover: Boolean,
    val expiredTimeInS: Long? = null,
    val maxOtpAttempts: Int? = null,
)