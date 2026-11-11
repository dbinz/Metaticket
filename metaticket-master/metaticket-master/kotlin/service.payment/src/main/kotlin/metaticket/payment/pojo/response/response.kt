package metaticket.payment.pojo.response

import metaticket.common.checksum.Checksum
import metaticket.payment.configuration.Environment
import metaticket.payment.pojo.db.PGAccount
import metaticket.payment.pojo.db.PaymentDocument
import metaticket.payment.response.PaymentResponse

fun PaymentDocument.toResponse(pgAccount: PGAccount): PaymentResponse {
    return PaymentResponse(
        id = Checksum.encode(this.id, Environment.secretKey),
        paymentNo = this.paymentNo,
        payTime = this.payTime?.time,
        paymentType = this.paymentType,
        status = this.status,
        amount = this.amount,
        pgAccountCode = pgAccountCode,
        pgAccountName = pgAccount.name,
        gateway = pgAccount.gateway
    )
}

data class RedirectResponse constructor(
    /**
     * 0 -> thành công
     * 1 -> thất bại
     * 3 -> không làm gì cả, để popup tự đóng
     */
    val code: Int = 3,
    val message: String?
)

val redirectSuccessResponse = RedirectResponse(0, "Thanh toán thành công")
val redirectSuccessMsgResponse: (msg: String?) -> RedirectResponse = { RedirectResponse(0,it ?: "Thanh toán thành công") }
val redirectFailedResponse = RedirectResponse(1, "Than toán thất bại")
val redirectFailedMsgResponse: (msg: String?) -> RedirectResponse = { RedirectResponse(1,it ?: "Than toán thất bại") }
val redirectPendingResponse = RedirectResponse(3, "Đang kiểm tra thanh toán")
val redirectPendingMsgResponse: (msg: String?) -> RedirectResponse = { RedirectResponse(3,it ?: "Đang kiểm tra thanh toán") }

/**
 * Chú ý đây là chuỗi xác nhận của ĐVCNTT đã nhận được IPN, không phải xác nhận trạng thái giao dịch
 */
val onepayIPNResponse = "responsecode=1&desc=confirm-success"

data class VnPayResponse constructor(
    val RspCode: String,
    val Message: String? = null,
)

val vnpayInvalidSignatureResponse = VnPayResponse("97", "invalid signature")
val vnpayOrderNotFoundResponse = VnPayResponse("01", "Order not found")
val vnpayInvalidAmountResponse = VnPayResponse("04", "Invalid amount")
val vnpayOrderAlreadyConfirmedResponse = VnPayResponse("02", "Order already confirmed")
val vnpayConfirmSuccessResponse = VnPayResponse("00", "Confirm Success")
val vnpayUnknownErrorResponse = VnPayResponse("99", "Unknow error")
