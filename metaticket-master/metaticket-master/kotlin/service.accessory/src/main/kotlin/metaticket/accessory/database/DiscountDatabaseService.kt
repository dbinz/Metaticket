package metaticket.accessory.database

import com.mongodb.client.model.Collation
import com.mongodb.client.model.CollationStrength
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.accessory.logger
import metaticket.accessory.pojo.db.DiscountCodeDocument
import org.bson.types.ObjectId

/**
 * Owns the discount-code collection (`accessory-discount-code`) — indexes plus admin CRUD helpers.
 */
class DiscountDatabaseService(
    private val discountCodeCol: MongoCollection<DiscountCodeDocument>,
) {

    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            val caseInsensitive = Collation.builder()
                .locale("en")
                .collationStrength(CollationStrength.SECONDARY)
                .build()
            val codeIdx = discountCodeCol.createIndex(
                Indexes.ascending(DiscountCodeDocument::code.name),
                IndexOptions().unique(true).collation(caseInsensitive),
            )
            logger.info("created accessory-discount-code code index {}", codeIdx)

            val showIdx = discountCodeCol.createIndex(
                Indexes.ascending(DiscountCodeDocument::accessoryShowId.name),
            )
            logger.info("created accessory-discount-code show index {}", showIdx)
        }
    }

    /** Lists campaigns scoped to one show (raw ObjectId hex). */
    suspend fun listByShowId(showId: String): List<DiscountCodeDocument> = withContext(Dispatchers.IO) {
        if (!ObjectId.isValid(showId)) return@withContext emptyList()
        // Field is BSON ObjectId (@BsonRepresentation) — must filter with ObjectId, not hex string.
        discountCodeCol.find(Filters.eq(DiscountCodeDocument::accessoryShowId.name, ObjectId(showId)))
            .toList()
            .sortedByDescending { it.code }
    }

    /** Loads one campaign by raw id, or null. */
    suspend fun findById(id: String): DiscountCodeDocument? = withContext(Dispatchers.IO) {
        if (!ObjectId.isValid(id)) return@withContext null
        discountCodeCol.find(Filters.eq("_id", ObjectId(id))).firstOrNull()
    }

    /** Case-insensitive lookup by canonical uppercase code. */
    suspend fun findByCode(code: String): DiscountCodeDocument? = withContext(Dispatchers.IO) {
        val canonical = code.trim().uppercase(java.util.Locale.ROOT)
        if (canonical.isEmpty()) return@withContext null
        discountCodeCol.find(Filters.eq(DiscountCodeDocument::code.name, canonical)).firstOrNull()
    }

    /** Bulk lookup by canonical uppercase codes (empty input → empty list). */
    suspend fun findByCodes(codes: Collection<String>): List<DiscountCodeDocument> = withContext(Dispatchers.IO) {
        val canonical = codes.map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()
        if (canonical.isEmpty()) return@withContext emptyList()
        discountCodeCol.find(Filters.`in`(DiscountCodeDocument::code.name, canonical)).toList()
    }

    /** Inserts a validated document (caller must validate). */
    suspend fun insert(doc: DiscountCodeDocument): DiscountCodeDocument = withContext(Dispatchers.IO) {
        discountCodeCol.insertOne(doc)
        doc
    }

    /** Full replace by id; returns null when missing. Preserves `_id`. */
    suspend fun replace(doc: DiscountCodeDocument): DiscountCodeDocument? = withContext(Dispatchers.IO) {
        val res = discountCodeCol.replaceOne(
            Filters.eq("_id", ObjectId(doc.id)),
            doc,
            ReplaceOptions().upsert(false),
        )
        if (res.matchedCount == 0L) null else doc
    }

    /** Sets `active`; returns updated doc or null. */
    suspend fun setActive(id: String, active: Boolean): DiscountCodeDocument? = withContext(Dispatchers.IO) {
        if (!ObjectId.isValid(id)) return@withContext null
        val res = discountCodeCol.updateOne(
            Filters.eq("_id", ObjectId(id)),
            Updates.set(DiscountCodeDocument::active.name, active),
        )
        if (res.matchedCount == 0L) null else findById(id)
    }

    /** Hard-deletes by id; returns true when a document was removed. */
    suspend fun deleteById(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!ObjectId.isValid(id)) return@withContext false
        discountCodeCol.deleteOne(Filters.eq("_id", ObjectId(id))).deletedCount == 1L
    }
}
