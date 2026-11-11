package metaticket.order.configuration

data class OrderConfig constructor(
    val expireInMinute: Long,
    val scanExpireInMinute: Long,
)
