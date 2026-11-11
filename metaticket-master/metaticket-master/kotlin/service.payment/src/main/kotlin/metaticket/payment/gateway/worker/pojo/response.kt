package metaticket.payment.gateway.worker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class NinePayInquiryResponse(
        val payment_no: String?,
        val invoice_no: String?,
        val currency: String?,
        val amount: Int?,
        val description: String?,
        val method: String?,
        val card_brand: String?,
        val status: Int? = null,
        val error_code: String?,
        val failure_reason: String?,
        val created_at: Date?,
        val card_info: NinePayCardInfoResponse?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NinePayDecodeResultResponse(
    val amount: Int?,
    val amount_foreign: Int?,
    val amount_original: Int?,
    val amount_request: Int?,
    val bank: String?,
    val card_brand: String?,
    val card_info: NinePayCardInfoResponse?,
    val created_at: Date?,
    val currency: String?,
    val description: String?,
    val error_code: String?,
    val exc_rate: String?,
    val failure_reason: String?,
    val foreign_currency: String?,
    val invoice_no: String?,
    val lang: String?,
    val method: String?,
    val payment_no: String?,
    val status: Int?,
    val tenor: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NinePayCardInfoResponse(
        val card_name: String?,
        val hash_card: String?,
        val card_brand: String?,
        val card_number: String?,
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class VnPayQueryResponse(
    var vnp_Amount: Long?,
    var vnp_ResponseId: String?,
    var vnp_Command: String?,
    var vnp_TmnCode: String?,
    var vnp_TxnRef: String?,
    var vnp_OrderInfo: String?,
    var vnp_ResponseCode: String?,
    var vnp_Message: String?,
    var vnp_BankCode: String?,
    var vnp_PayDate: String?,
    var vnp_BankTranNo: String?,
    var vnp_TransactionType: String?,
    var vnp_TransactionStatus: String?,
    var vnp_PromotionCode: String?,
    var vnp_PromotionAmount: String?,
    var vnp_SecureHash: String?,
    var vnp_CardType: String?,
    var vnp_TransactionNo: String?,
)
