package metaticket.qrcode.pojo

data class QRCodeTicketInfo(
    val orderCode: String,
    val cat: String,
    val sid: String,
    val ticketIdx: Int,
    val slotIdx: Int? = null,
)

data class FetchOrderQRCodeResponse(
    val disabled: List<String>,
    val enabled: List<String>,
)

data class DisableEnableQRCodeResponse constructor(
    val orderId: String,
    val countQRCode: Int,
)