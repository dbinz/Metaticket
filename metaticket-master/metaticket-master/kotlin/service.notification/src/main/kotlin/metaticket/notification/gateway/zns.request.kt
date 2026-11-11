package metaticket.notification.gateway

data class ZNSMessageResponseData constructor(
    val message_id: String,
)
data class ZNSMessageResponse constructor(
    val code: Int,
    val message: String,
    val data: ZNSMessageResponseData? = null,
)