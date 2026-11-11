package metaticket.payment.pojo.db

import metaticket.payment.datastructure.OrderType
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.datastructure.PaymentType
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*


data class PaymentDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    val orderId: String,

    val pgAccountCode: String,
    val orderCode: String,
    // Additive callback-routing discriminator (backward compatible). Stored as a nullable
    // String holding an [metaticket.payment.datastructure.OrderType] name ("TICKET"/"ACCESSORY").
    // Existing rows omit this field and therefore decode to the "TICKET" default, so every
    // pre-existing/ticket payment continues to route to service.order unchanged (Req 8.3, 8.4, 18.4).
    val orderType: String? = OrderType.TICKET.name,
    val refreshStatusJobId: UUID,
    val amount: Long,
    val paymentMethods: List<PaymentType>?,
    val paymentNo: String ? = null,
    val paymentType: PaymentType? = null,
    val payTime: Date ? = null,
    var status: PaymentStatus = PaymentStatus.Created,
    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
    val paymentDetail: PaymentDetail? = null
)
