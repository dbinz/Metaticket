package metaticket.editor.seatplanner.pojo

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlin.reflect.KClass

@JsonTypeInfo(
    use= JsonTypeInfo.Id.NAME,
    include= JsonTypeInfo.As.PROPERTY,
    property="type",
    visible = true
)
interface PrimitiveWithType {
    val type: String
}

sealed class Primitive

data class Position(val x : Float, val y : Float) : Primitive()


/**
 * Line
 */
@JsonTypeInfo(
    use= JsonTypeInfo.Id.NAME,
    include=JsonTypeInfo.As.PROPERTY,
    property="type",
    visible = true
)
data class BezierCurve(
    val p : List<Position>,
    override val type: String = "BezierCurve"
) : Primitive(), PrimitiveWithType {
}

/**
 * A boundary is a sequention trace from one point, going through
 * curve or points coming back to the original point
 */
@JsonTypeInfo(
    use= JsonTypeInfo.Id.NAME,
    include=JsonTypeInfo.As.PROPERTY,
    property="type",
    visible = true
)
data class Boundary(
    val e : List<BezierCurve>,
    override val type: String = "Boundary",
) : Primitive(), PrimitiveWithType {
}

fun PrimitiveJacksonModule() : SimpleModule {

    class KClassDeserializer : StdDeserializer<Primitive>(Primitive::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Primitive {
            if (p.text.contains("type")) {
                return p.readValueAs(PrimitiveWithType::class.java) as Primitive
            } else return p.readValueAs(Position::class.java)
        }
    }

    return KotlinModule.Builder().build()
        .addDeserializer(Primitive::class.java, KClassDeserializer())
}

