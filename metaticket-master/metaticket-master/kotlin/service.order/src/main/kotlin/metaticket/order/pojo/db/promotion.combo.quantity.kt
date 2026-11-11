package metaticket.order.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

data class PromotionComboQuantity constructor(
    @BsonId
    @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    var showId: String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,

    val originalQuantity: Int,

    val quantity: Int,

    val lastModified: Date = Date(),
)
