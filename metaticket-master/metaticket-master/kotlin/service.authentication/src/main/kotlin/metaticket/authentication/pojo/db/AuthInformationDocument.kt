package metaticket.authentication.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

data class AuthInformationDocument(
    @BsonId
    @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    val email: String,
    val password: String,
    val loggedIn: Boolean = false,
    val lastModified: Date? = null
)