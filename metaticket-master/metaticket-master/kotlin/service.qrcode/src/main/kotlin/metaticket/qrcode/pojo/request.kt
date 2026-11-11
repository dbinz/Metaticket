package metaticket.qrcode.pojo

data class ScanQRCodeRequest(
    val session: String,
    val privateCode: String,
    val verifyCode: String,
)

data class FetchOrderQRCodeRequest(
    val showId: String,
    val sessionId: String,
    val orderIds: List<String>? = null,
)

data class DisableOrderQRCodeRequest(
    val orderId: String,
)

data class EnableOrderQRCodeRequest(
    val orderId: String,
)