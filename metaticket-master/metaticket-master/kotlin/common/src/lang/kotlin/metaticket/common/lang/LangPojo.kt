package metaticket.common.lang

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId

data class LangPojo(
    @BsonId
    @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),

    @BsonProperty("code1")
    val code1: String,

    @BsonProperty("code2")
    val code2: String,

    @BsonProperty("family")
    val family: String,

    @BsonProperty("name")
    val name: String,

    @BsonProperty("nativeName")
    val nativeName: String,

    @BsonProperty("wikiUrl")
    val wikiUrl: String,
    )
