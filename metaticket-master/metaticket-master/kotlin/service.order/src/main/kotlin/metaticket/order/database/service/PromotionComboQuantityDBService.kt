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
import metaticket.order.pojo.db.PromotionComboQuantity
import org.bson.types.ObjectId
import java.util.*

class PromotionComboQuantityDBService (
    private val promotionComboQuantityCol: MongoCollection<PromotionComboQuantity>,
): Logging {

    suspend fun createPromotionComboQuantity(entity: PromotionComboQuantity): String? {
        return withContext(Dispatchers.IO) { promotionComboQuantityCol.insertOne(entity) }.insertedId?.asObjectId()?.value?.toString()
    }

    suspend fun loadPromotionComboQuantity(comboId: String): PromotionComboQuantity? {
        val filter = Filters.eq("_id", ObjectId(comboId))
        return withContext(Dispatchers.IO) { promotionComboQuantityCol.find(filter).firstOrNull() }
    }

    suspend fun decrementPromotionComboQuantity(id: String, quantity: Int): PromotionComboQuantity? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(id)),
            Filters.gte(PromotionComboQuantity::quantity.name, quantity)
        )

        val updates = Updates.combine(
            Updates.inc(PromotionComboQuantity::quantity.name, -quantity),
            Updates.set(PromotionComboQuantity::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { promotionComboQuantityCol.findOneAndUpdate(filter, updates, options) }
    }

    suspend fun incrementPromotionComboQuantity(id: String, quantity: Int): PromotionComboQuantity? {
        val filter = Filters.eq("_id", ObjectId(id))

        val updates = Updates.combine(
            Updates.inc(PromotionComboQuantity::quantity.name, quantity),
            Updates.set(PromotionComboQuantity::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { promotionComboQuantityCol.findOneAndUpdate(filter, updates, options) }
    }
}
