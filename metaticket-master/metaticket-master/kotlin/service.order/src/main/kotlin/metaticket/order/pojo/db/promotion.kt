package metaticket.order.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId

data class Promotion constructor(
    @BsonId
    @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    var showId: String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,

    val pluginName: String,

    val configuration: String,
)
