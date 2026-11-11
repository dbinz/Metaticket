package metaticket.order.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.pojo.db.CustomFieldTmpl
import metaticket.order.pojo.db.OrderCustomFieldMapping
import org.bson.types.ObjectId
import java.util.*


/**
 *
 * @author dongcatchay
 */
class OrderCustomFieldMappingDBService(
    private val col: MongoCollection<OrderCustomFieldMapping>
): Logging {

    suspend fun createCustomFieldMapping(entity: OrderCustomFieldMapping): String? {
        return withContext(Dispatchers.IO) { col.insertOne(entity) }.insertedId?.toString()
    }

    suspend fun updateCustomFieldMapping(showId: String, sessionId: String, customFields: List<CustomFieldTmpl>): OrderCustomFieldMapping? {
        val filter = Filters.and(
            Filters.eq(OrderCustomFieldMapping::showId.name, ObjectId(showId)),
            Filters.eq(OrderCustomFieldMapping::sessionId.name, ObjectId(sessionId)),
        )

        val update = Updates.combine(
            Updates.set(OrderCustomFieldMapping::customFields.name, customFields)
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
    }

    suspend fun getCustomFieldMappingByShowAndSession(showId: String, sessionId: String): OrderCustomFieldMapping? {
        val filter = Filters.and(
            Filters.eq(OrderCustomFieldMapping::showId.name, ObjectId(showId)),
            Filters.eq(OrderCustomFieldMapping::sessionId.name, ObjectId(sessionId)),
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }
}
