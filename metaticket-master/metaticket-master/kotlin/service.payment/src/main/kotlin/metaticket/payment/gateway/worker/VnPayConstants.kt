package metaticket.payment.gateway.worker

import java.text.SimpleDateFormat
import java.util.*

class VnPayConstants {
    companion object {
        /**Response code trả về, lúc check giao dịch thành cồng hay không, cần check chung với mã này*/
        const val VNPAY_RESPONSE_CODE_SUCCEED = "00"
        const val VNPAY_RESPONSE_CODE_NOT_FOUND = "91"

        /**Giao dịch thanh toán thành công*/
        const val VNPAY_TRANSACTION_STATUS_SUCCEED = "00"
        /**Giao dịch chưa hoàn tất*/
        const val VNPAY_TRANSACTION_NOT_COMPLETE_YET = "01"
        /**Giao dịch bị lỗi*/
        const val VNPAY_TRANSACTION_FAILED = "02"
        /**Giao dịch đảo (Khách hàng đã bị trừ tiền tại Ngân hàng nhưng GD chưa thành công ở VNPAY)*/
        const val VNPAY_TRANSACTION_INVERSE = "04"
        const val VNPAY_TRANSACTION_REFUND_PROCESSING = "05"
        /** VNPAY đã gửi yêu cầu hoàn tiền sang Ngân hàng (GD hoàn tiền)*/
        const val VNPAY_TRANSACTION_REFUND_REQUEST = "06"
        /**Giao dịch bị nghi ngờ gian lận*/
        const val VNPAY_TRANSACTION_ILLEGALLY = "07"
        /**GD Hoàn trả bị từ chối*/
        const val VNPAY_TRANSACTION_REJECT = "09"

        val vnpayDateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.forLanguageTag("Etc/GMT+7"))
        val vnpayParseDate: (String) -> Date = { s -> vnpayDateFormat.parse(s)}
    }
}
