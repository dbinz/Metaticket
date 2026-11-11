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
import metaticket.order.pojo.db.PromotionCodeQuantity
import org.bson.types.ObjectId
import java.util.*

class PromotionCodeQuantityDBService(
    private val promotionCodeQuantityCol: MongoCollection<PromotionCodeQuantity>,
): Logging {

    suspend fun createPromotionCodeQuantity(entity: PromotionCodeQuantity): String? {
        return withContext(Dispatchers.IO) { promotionCodeQuantityCol.insertOne(entity) }.insertedId?.toString()
    }

    suspend fun updatePromotionCodeQuantity() {
        val filter = Filters.and(
            Filters.eq(PromotionCodeQuantity::showId.name, ObjectId("6595773df323909fe23b954e")),
            Filters.eq(PromotionCodeQuantity::sessionId.name, ObjectId("65957746f323909fe23b954f")),
        )

        val updates = Updates.combine(
            Updates.set(PromotionCodeQuantity::showId.name, ObjectId("65b8a2b6f2b83f739ea354ea")),
            Updates.set(PromotionCodeQuantity::sessionId.name, ObjectId("65b9b67147f39535886a495d"))
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { promotionCodeQuantityCol.updateMany(filter, updates) }
    }

    suspend fun loadPromotionCodeQuantity(showId: String, sessionId: String, code: String): PromotionCodeQuantity? {
        val filter = Filters.and(
            Filters.eq(PromotionCodeQuantity::showId.name, ObjectId(showId)),
            Filters.eq(PromotionCodeQuantity::sessionId.name, ObjectId(sessionId)),
            Filters.eq(PromotionCodeQuantity::code.name, code),
        )
        return withContext(Dispatchers.IO) { promotionCodeQuantityCol.find(filter).firstOrNull() }
    }

    suspend fun incrementPromotionCodeQuantity(id: String): PromotionCodeQuantity? {
        val filter = Filters.eq("_id", ObjectId(id))

        val updates = Updates.combine(
            Updates.inc(PromotionCodeQuantity::quantity.name, 1),
            Updates.set(PromotionCodeQuantity::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { promotionCodeQuantityCol.findOneAndUpdate(filter, updates, options) }
    }

    suspend fun decrementPromotionCodeQuantity(id: String): PromotionCodeQuantity? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(id)),
            Filters.gt(PromotionCodeQuantity::quantity.name, 0)
        )

        val updates = Updates.combine(
            Updates.inc(PromotionCodeQuantity::quantity.name, -1),
            Updates.set(PromotionCodeQuantity::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { promotionCodeQuantityCol.findOneAndUpdate(filter, updates, options) }
    }
}
