package metaticket.customer.user.model

data class ZNSConfig constructor(
    val plugin: String,
    val templateId: String,
    val failover: Boolean,
)

data class MailOTPConfig constructor(
    val plugin: String,
    val templateId: String,
)
