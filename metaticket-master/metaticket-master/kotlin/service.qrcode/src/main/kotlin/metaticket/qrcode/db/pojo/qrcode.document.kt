package metaticket.qrcode.db.pojo

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

data class QRCodeDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    val orderId: String,

    val orderCode: String,
    val eventName: String = "",
    val cat: String,
    val sid: String,
    val no: Int = 1, // số thứ tự của vé trong đơn hàng

    val scanned: Int = 0,
    val disable: Boolean? = null,

    val lastUpdated: Date = Date(),
    val createdAt: Date = Date(),
)