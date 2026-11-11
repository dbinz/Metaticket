package metaticket.admin.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.admin.pojo.db.ShowSessionDraftDocument
import metaticket.admin.pojo.db.ShowSessionDraftPromotionDocument
import org.bson.types.ObjectId

class ShowSessionDraftPromotionDBService constructor(
    private val showSessionDraftPromotionCol : MongoCollection<ShowSessionDraftPromotionDocument>,
) {
    suspend fun loadSessionsDraftPromotions(sessionDraftId: String): List<ShowSessionDraftPromotionDocument> {
        val filter = Filters.and(
            Filters.eq(ShowSessionDraftPromotionDocument::sessionDraftId.name, ObjectId(sessionDraftId)),
        )
        return withContext(Dispatchers.IO) { showSessionDraftPromotionCol.find(filter) }.toList()
    }

    suspend fun createShowSessionDraftPromotion(entity : ShowSessionDraftPromotionDocument) : String {
        val result = withContext(Dispatchers.IO) { showSessionDraftPromotionCol.insertOne(entity) }
        return result.insertedId!!.toString()
    }

    suspend fun updateShowSessionDraftPromotion(sessionDraftId: String, name: String, data: String): ShowSessionDraftPromotionDocument? {
        val filter = Filters.eq("_id", ObjectId(sessionDraftId))

        val update = Updates.combine(
            Updates.set(ShowSessionDraftPromotionDocument::name.name, name),
            Updates.set(ShowSessionDraftPromotionDocument::data.name, data),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { showSessionDraftPromotionCol.findOneAndUpdate(filter, update, options)}
    }

    suspend fun deleteBySessionDraftId(sessionDraftId: String): Long {
        if (!ObjectId.isValid(sessionDraftId)) return 0
        val filter = Filters.eq(ShowSessionDraftPromotionDocument::sessionDraftId.name, ObjectId(sessionDraftId))
        return withContext(Dispatchers.IO) {
            showSessionDraftPromotionCol.deleteMany(filter).deletedCount
        }
    }
}