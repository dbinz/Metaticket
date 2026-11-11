package metaticket.editor.policydesc.pojo.db.policydesc

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

data class PolicyDescItem(
    val id: Int,
    val sid: String,
    val cat: String = "",
    var displayName : String = "",       // display name, it could be different from sid, if not set, sid is shown
    val type: String = "SeatZone",
)

data class PolicyDescDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),
    val styleSheet : String = "",
    val content: String = "",
    val displayName: String,
    var catNames : List<String> = emptyList(),
    val items: List<PolicyDescItem> = listOf(),
    val version: Int = 1,
    val lastModified: Date = Date(),
)

data class FetchSeatPlanCategoryResult(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String,
    var displayName : String = "",
    var catNames : List<String> = emptyList(),
)
