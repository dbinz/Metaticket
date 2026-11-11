package metaticket.order.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.pojo.db.Promotion
import org.bson.types.ObjectId

class PromotionDBService(
    private val promotionCol: MongoCollection<Promotion>,
): Logging {
    suspend fun createPromotion(entity: Promotion): String? {
        return withContext(Dispatchers.IO) { promotionCol.insertOne(entity).insertedId?.toString() }
    }

    suspend fun loadPromotions(showId: String, sessionId: String): List<Promotion> {
        val filter = Filters.and(
            Filters.eq("showId", ObjectId(showId)),
            Filters.eq("sessionId", ObjectId(sessionId))
        )
        return withContext(Dispatchers.IO) { promotionCol.find(filter).sort(Sorts.ascending(Promotion::id.name)).toList() }
    }

    suspend fun loadPromotion(id: String): Promotion? {
        val filter = Filters.eq("_id", ObjectId(id))
        return withContext(Dispatchers.IO) { promotionCol.find(filter) }.firstOrNull()
    }

    suspend fun deletePromotion(id: String) {
        val filter = Filters.eq("_id", ObjectId(id))
        withContext(Dispatchers.IO) { promotionCol.deleteOne(filter) }
    }
}
