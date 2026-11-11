package metaticket.admin.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.admin.pojo.db.ShowSessionDraftOrderCustomFieldDocument
import metaticket.admin.pojo.db.ShowSessionDraftPromotionDocument
import metaticket.order.pojo.db.CustomFieldTmpl
import org.bson.types.ObjectId

class ShowSessionDraftOrderCustomFieldDBService(
    private val showSessionDraftOrderCustomFieldCol : MongoCollection<ShowSessionDraftOrderCustomFieldDocument>,
) {
    suspend fun loadSessionsDraftOrderCustomFields(sessionDraftId: String): ShowSessionDraftOrderCustomFieldDocument? {
        val filter = Filters.and(
            Filters.eq(ShowSessionDraftOrderCustomFieldDocument::sessionDraftId.name, ObjectId(sessionDraftId)),
        )
        return withContext(Dispatchers.IO) { showSessionDraftOrderCustomFieldCol.find(filter).firstOrNull() }
    }

    suspend fun createShowSessionDraftOrderCustomFields(entity : ShowSessionDraftOrderCustomFieldDocument) : String {
        val result = withContext(Dispatchers.IO) { showSessionDraftOrderCustomFieldCol.insertOne(entity) }
        return result.insertedId!!.toString()
    }

    suspend fun updateShowSessionDraftOrderCustomFields(sessionDraftId: String, customFields: List<CustomFieldTmpl>): ShowSessionDraftOrderCustomFieldDocument? {
        val filter = Filters.eq("_id", ObjectId(sessionDraftId))

        val update = Updates.combine(
            Updates.set(ShowSessionDraftOrderCustomFieldDocument::customFields.name, customFields),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { showSessionDraftOrderCustomFieldCol.findOneAndUpdate(filter, update, options)}
    }

    suspend fun deleteBySessionDraftId(sessionDraftId: String): Long {
        if (!ObjectId.isValid(sessionDraftId)) return 0
        val filter = Filters.eq(ShowSessionDraftOrderCustomFieldDocument::sessionDraftId.name, ObjectId(sessionDraftId))
        return withContext(Dispatchers.IO) {
            showSessionDraftOrderCustomFieldCol.deleteMany(filter).deletedCount
        }
    }
}