package metaticket.payment.response

import metaticket.payment.datastructure.PaymentGateway
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.datastructure.PaymentType


/**
 *
 * @author dongcatchay
 */

data class PaymentResponse constructor(
    var id: String,
    var paymentNo: String?,
    var payTime: Long?,
    var paymentType: PaymentType?,
    var status: PaymentStatus,
    var amount: Long,
    var pgAccountCode: String,
    var pgAccountName: String,
    var gateway: PaymentGateway,
)
