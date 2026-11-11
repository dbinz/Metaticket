package metaticket.payment.gateway.worker

class NinePayConstants {
    companion object {
        const val NINEPAY_STATUS_CREATED = 1
        const val NINEPAY_STATUS_PROCESSING = 2
        const val NINEPAY_STATUS_PREVIEWING = 3
        const val NINEPAY_STATUS_SUCCESS_NOT_PUBLIC = 4
        const val NINEPAY_STATUS_SUCCEED = 5
        const val NINEPAY_STATUS_FAILED = 6
        const val NINEPAY_STATUS_REFUND = 7
        const val NINEPAY_STATUS_CANCELED = 8
        const val NINEPAY_STATUS_REJECT = 9
        const val NINEPAY_STATUS_KEEP_MONEY = 12
        const val NINEPAY_STATUS_ERROR = 14
        const val NINEPAY_STATUS_RECEIVED_MONEY = 16
    }
}
