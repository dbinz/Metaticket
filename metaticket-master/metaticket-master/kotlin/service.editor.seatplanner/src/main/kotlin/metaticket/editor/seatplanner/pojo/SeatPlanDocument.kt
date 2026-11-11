package metaticket.editor.seatplanner.pojo

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId

data class SeatPlanDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),
    var displayName : String = "",
    var styleSheet : String = "",
    var baseImage : ByteArray? = null,
    var baseImgUrl : String = "",
    var defs : String = "", // the definition for the props. These can be used to create the chair
    var catNames : List<String> = emptyList(),
    var items : List<PlanItem> = emptyList(),
    var version: Long = 0

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SeatPlanDocument

        if (!baseImage.contentEquals(other.baseImage)) return false

        return true
    }

    override fun hashCode(): Int {
        return baseImage.contentHashCode()
    }
}

data class FetchSeatPlanCategoryResult(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String,
    var displayName : String = "",
    var catNames : List<String> = emptyList(),
    var baseImgUrl : String = "",
)
