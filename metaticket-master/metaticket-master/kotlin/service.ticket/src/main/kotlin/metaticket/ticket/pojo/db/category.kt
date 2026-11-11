package metaticket.ticket.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.Date


/**
 *
 * @author dongcatchay
 */

data class CategoryMappingDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),
    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val mapId: String? = null,

    val kindMap: String,
    val catMapping: Map<String, CategoryItemDocument>,

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)

data class CategoryItemDocument(
    val category: String, // category on seat map, it is difference with ticket category
    val displayName: String, //  this is ticket category, it's being show on the ticket
    val price: Long,
    val color: String?,
    val openDate: Date?,
    val closeDate: Date?,
)
