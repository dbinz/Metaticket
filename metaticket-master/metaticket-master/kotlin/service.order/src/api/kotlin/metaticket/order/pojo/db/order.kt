package metaticket.order.pojo.db

data class DeliveryInfo constructor(
    var deliveryName: String,
    var deliveryPhone: String,
    var provinceName: String,
    var districtName: String,
    var wardName: String,
    var address: String,
)