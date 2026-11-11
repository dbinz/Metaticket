package metaticket.editor.seatplanner.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonDiscriminator

@JsonTypeInfo(
    use= JsonTypeInfo.Id.NAME,
    include=JsonTypeInfo.As.PROPERTY,
    property="type",
    visible = true
)
@BsonDiscriminator(key = "type")
sealed class PlanItem @BsonCreator constructor() {
    abstract var type : String
    abstract var id : Int
}

@JsonTypeInfo(
    use= JsonTypeInfo.Id.NAME,
    include=JsonTypeInfo.As.PROPERTY,
    property="type",
    visible = true
)
data class Seat(
    override var id : Int,
    var pos : Position,
    var sid : String,
    var rotation : Byte,    // rotation from 0 to 360
    var cat : String = "",
    var controlItem : Int = -1,  // whether the seat belong to a control item (e.g. seat line or other type of item)
    var displayName : String = "",       // display name, it could be different from sid, if not set, sid is shown
    override var type: String = "Seat"
) : PlanItem() {

}

@JsonTypeInfo(
    use= JsonTypeInfo.Id.NAME,
    include=JsonTypeInfo.As.PROPERTY,
    property="type",
    visible = true
)
data class Label(
    override var id : Int,
    // position of the label depending on the attachment point,
    // if it is a free label, its position is on the plan layout itself
    // otherwise, it is an offset from the attachment point
    // for example, if a label belongs to a seat line, its pos record
    // the offset from one of the points of the line
    var pos : Position,

    var name : String,

    override var type: String = "Label"
) : PlanItem() {

}

@JsonTypeInfo(
    use= JsonTypeInfo.Id.NAME,
    include=JsonTypeInfo.As.PROPERTY,
    property="type",
    visible = true
)
data class SeatLine(
    override var id : Int,
    var line : BezierCurve,
    var labels : List<Label>,
    var seats : List<Int> = emptyList(),
    var seatCal : SeatLineCalParam?,
    var lineName : String? = "",
    var seatNaming : SeatLineNaming?,
    var seatCat : String? = "",
    override var type: String = "SeatLine"
) : PlanItem() {
}

data class SeatLineNaming(
    var start : Int,
    var end : Int,
    var step : Int?
)

data class SeatLineCalParam(
    var start : Float,
    var end : Float,
    var capacity : Int
)

@JsonTypeInfo(
    use= JsonTypeInfo.Id.NAME,
    include=JsonTypeInfo.As.PROPERTY,
    property="type",
    visible = true
)
data class SeatZone(
    override var id : Int,
    var boundary : Boundary,
    var label : String,
    var lrot : Int?, // label rotation
    var lstyle : String?,    // label additional style
    var sid : String = "",
    var cat : String = "",
    var lpos : Position?,   // label position relative to the center of the control points
    override var type: String = "SeatZone"
) : PlanItem()