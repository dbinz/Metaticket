package metaticket.payment.pojo.db

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import metaticket.payment.datastructure.PaymentGateway
import org.bson.BsonDocument
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonDiscriminator
import org.bson.codecs.pojo.annotations.BsonProperty

@BsonDiscriminator
@JsonTypeInfo(
    include = JsonTypeInfo.As.PROPERTY,
    use = JsonTypeInfo.Id.NAME,
    property="type",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = OnePayPaymentDetail::class),
    JsonSubTypes.Type(value = NinePayPaymentDetail::class),
    JsonSubTypes.Type(value = VNPayPaymentDetail::class),
)
abstract class PaymentDetail constructor() {
    abstract val gateway: PaymentGateway
    abstract val returnUrl: String
    abstract val redirectUrl: String
    abstract var response: BsonDocument?
}

@BsonDiscriminator
class OnePayPaymentDetail @BsonCreator constructor(
    @BsonProperty("redirectUrl")
    override val redirectUrl: String,
    @BsonProperty("returnUrl")
    override val returnUrl: String,
) : PaymentDetail() {
    override var gateway: PaymentGateway = PaymentGateway.OnePay
    override var response: BsonDocument? = null
}

@BsonDiscriminator
class NinePayPaymentDetail @BsonCreator constructor(
    @BsonProperty("returnUrl")
    override val returnUrl: String,
    @BsonProperty("redirectUrl")
    override val redirectUrl: String,
)  : PaymentDetail() {
    override val gateway: PaymentGateway = PaymentGateway.NinePay
    override var response: BsonDocument? = null
}

//enum class NinePayPaymentStatus {
//    Processing, //Giao dịch đang xử lý
//    WaitingForTest, //Giao dịch đang chờ kiểm tra (Giao dịch bị nghi ngờ vi phạm quy định về quản trị rủi ro của đối tác thanh toán)
//    Succeed, //Giao dịch thành công
//    Failed, //Giao dịch thất bại (các giao dịch bị từ chối bởi bank như không đủ số dư, lỗi thẻ ...)
//    Cancelled, //Giao dịch bị hủy (giao dịch quá hạn xử lý hoặc người dùng không hoàn thành giao dịch)
//    Denied, //Giao dịch bị từ chối (Giao dịch bị từ chối do vi phạm quy định về quản trị rủi ro của đói tác thanh toán)
//    GotMoney //Giao dịch đã nhận tiền (Chỉ áp dụng với phương thức thanh toán Chuyển khoản ngân hàng)
//}


@BsonDiscriminator(key = "paymentDetail", value = "VNPayPaymentDetail")
class VNPayPaymentDetail @BsonCreator constructor(
    @BsonProperty("redirectUrl")
    override val redirectUrl: String,
    @BsonProperty("returnUrl")
    override val returnUrl: String,
    @BsonProperty("vnpCreateDate")
    var vnpCreateDate: String
) : PaymentDetail() {
    override var gateway: PaymentGateway = PaymentGateway.VNPay
    override var response: BsonDocument? = null
}