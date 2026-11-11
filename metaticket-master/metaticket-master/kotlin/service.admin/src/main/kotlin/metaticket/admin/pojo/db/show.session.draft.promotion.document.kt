package metaticket.admin.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*


data class ShowSessionDraftPromotionDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    var sessionDraftId : String,

    var name: String,

    var data: String,

    val createdAt: Date = Date(),
)