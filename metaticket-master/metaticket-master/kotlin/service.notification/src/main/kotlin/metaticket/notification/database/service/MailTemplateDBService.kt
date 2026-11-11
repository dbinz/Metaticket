package metaticket.notification.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.plugin.mailtemplate.pojo.db.MailTemplateDocument
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */
class MailTemplateDBService(
    private val col: MongoCollection<MailTemplateDocument>
) {

    suspend fun loadAllMailTemplate(): List<MailTemplateDocument> {
        return withContext(Dispatchers.IO) { col.find<MailTemplateDocument>() }.toList()
    }

    suspend fun loadMailTemplate(id: String): MailTemplateDocument? {
        val filter = Filters.and(
            Filters.eq("_id", id),
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }
    suspend fun findById(id: String): MailTemplateDocument? {
        val filter = Filters.eq("_id", id)
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }

    suspend fun createMailTemplate(entity: MailTemplateDocument): String? {
        return withContext(Dispatchers.IO) { col.insertOne(entity) }.insertedId?.toString()
    }

    suspend fun updateMailTemplate(
        id: String,
        entity: MailTemplateDocument
    ): MailTemplateDocument? {
        val filter = Filters.and(
            Filters.eq("_id", id),
        )

        val options = FindOneAndReplaceOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndReplace(filter, entity, options) }
    }
}
