package metaticket.ticketslots.model

data class FetchTicketSlotsByShowRequest(val showId : String, val sessionId : String, )

data class SendTransferOTPRequest(var slotId: String)

data class VerifyOTPRequest(var slotId: String, val otp: String)

data class TransferTicketSlotRequest(
    var slotId: String,
    val currentStateIdx: Int,
    val version: Int,
    val newOwnerPhone: String,
    val newOwnerName: String,
)

data class UpdateSelectedTicketSlotsRequest(val ticketSlotIds: List<String>)

// Xác minh Captcha ngay tại bước khởi tạo session
data class InitSessionRequest(val captchaToken: String?)