package metaticket.payment.pojo.request

import metaticket.payment.datastructure.PaymentType

data class StartPaymentRequest constructor(
    var paymentId: String,
    val paymentType: PaymentType,
)

data class InquiryPaymentRequest constructor(
    val paymentId: String,
)

data class QueryVnPayPaymentStatusRequest constructor(
    val vnp_RequestId: String?,
    val vnp_Version: String?,
    val vnp_BankTranNo: String?,
    val vnp_CardType: String?,
    val vnp_OrderInfo: String?,
    val vnp_PayDate: String?,
    val vnp_ResponseCode: Int?,
    val vnp_TmnCode: String?,
    val vnp_TransactionNo: String?,
    val vnp_TransactionStatus: String?,
    val vnp_TxnRef: String?,
    val vnp_SecureHash: String?,
)
