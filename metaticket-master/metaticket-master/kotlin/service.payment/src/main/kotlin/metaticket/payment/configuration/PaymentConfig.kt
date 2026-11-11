package metaticket.payment.configuration

data class PaymentConfig(
    val baseReturnUrl: String,      // base url that gateway using to return after process payment
    val baseIpnUrl: String,         // base url that gateway using to notify when payment change state
    val hashSecret: String,     // hash secret to verify that return url is generated base on this one
    val expireInMinute: Int,    // payment status created will be expired after this time in minute
    val scanExpireInMinute: Int // time interval to scan created payment in minute
)
