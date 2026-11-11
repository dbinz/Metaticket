package metaticket.order.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId


data class OrderCustomFieldMapping constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),
    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,

    val customFields: List<CustomFieldTmpl> = emptyList(),
)
