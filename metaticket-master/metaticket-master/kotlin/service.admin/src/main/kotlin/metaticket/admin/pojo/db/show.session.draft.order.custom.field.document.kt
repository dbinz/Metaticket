package metaticket.admin.pojo.db

import metaticket.order.pojo.db.CustomFieldTmpl
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*


data class ShowSessionDraftOrderCustomFieldDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    var sessionDraftId : String,

    val customFields: List<CustomFieldTmpl> = emptyList(),

    val createdAt: Date = Date(),
)