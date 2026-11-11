package metaticket.ticket.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.client.result.InsertOneResult
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticket.pojo.db.CategoryItemDocument
import metaticket.ticket.pojo.db.CategoryMappingDocument
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */
class CategoryMappingDBService constructor(
    private val col: MongoCollection<CategoryMappingDocument>,
): Logging {

    suspend fun insert(document: CategoryMappingDocument): InsertOneResult {
        return withContext(Dispatchers.IO) { col.insertOne(document) }
    }

    suspend fun updateCategoryMapping(showId: String, sessionId: String, cat: CategoryItemDocument): CategoryMappingDocument? {
        val filter = Filters.and(
            Filters.eq(CategoryMappingDocument::showId.name, ObjectId(showId)),
            Filters.eq(CategoryMappingDocument::sessionId.name, ObjectId(sessionId)),
        )
        val updates = Updates.set("${CategoryMappingDocument::catMapping.name}.${cat.category}", cat)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, updates) }
    }

    suspend fun addCategory(showId: String, sessionId: String, cat: CategoryItemDocument): CategoryMappingDocument? {
        val filter = Filters.and(
            Filters.eq(CategoryMappingDocument::showId.name, ObjectId(showId)),
            Filters.eq(CategoryMappingDocument::sessionId.name, ObjectId(sessionId)),
            Filters.not(Filters.exists("${CategoryMappingDocument::catMapping.name}.${cat.category}")),
        )
        val updates = Updates.set("${CategoryMappingDocument::catMapping.name}.${cat.category}", cat)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, updates) }
    }

    suspend fun findByShowAndSession(showId: String, sessionId: String): CategoryMappingDocument? {
        val filters = Filters.and(
            Filters.eq(CategoryMappingDocument::showId.name, ObjectId(showId)),
            Filters.eq(CategoryMappingDocument::sessionId.name, ObjectId(sessionId)),
        )
        return withContext(Dispatchers.IO) { col.find(filters) }.firstOrNull()
    }

    suspend fun deleteCategoryMapping(showId: String, sessionId: String, category: String): CategoryMappingDocument? {
        val filters = Filters.and(
            Filters.eq(CategoryMappingDocument::showId.name, ObjectId(showId)),
            Filters.eq(CategoryMappingDocument::sessionId.name, ObjectId(sessionId)),
        )
        val updates = Updates.unset("${CategoryMappingDocument::catMapping.name}.$category")
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filters, updates) }
    }

    suspend fun findByShowAndSessionCat(showId: String, sessionId: String, category: String): CategoryMappingDocument? {
        val filters = Filters.and(
            Filters.eq(CategoryMappingDocument::showId.name, ObjectId(showId)),
            Filters.eq(CategoryMappingDocument::sessionId.name, ObjectId(sessionId)),
            Filters.exists("${CategoryMappingDocument::catMapping.name}.$category"),
        )
        return withContext(Dispatchers.IO) { col.find(filters) }.firstOrNull()
    }
}
