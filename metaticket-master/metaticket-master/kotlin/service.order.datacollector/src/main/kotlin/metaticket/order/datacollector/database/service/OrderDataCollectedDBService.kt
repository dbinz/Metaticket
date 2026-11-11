package metaticket.order.datacollector.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.datacollector.pojo.db.OrderDataCollected
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */
class OrderDataCollectedDBService(
    private val col: MongoCollection<OrderDataCollected>
): Logging {

    suspend fun saveData(data: OrderDataCollected): Boolean {
        val filters = Filters.eq("_id", ObjectId(data.id))
        return withContext(Dispatchers.IO) {
            col.replaceOne(filters, data, ReplaceOptions().upsert(true)).wasAcknowledged()
        }
    }

    suspend fun getDataById(id: String): OrderDataCollected? {
        val filter = Filters.eq("_id", ObjectId(id))
        return withContext(Dispatchers.IO) { col.find(filter).firstOrNull() }
    }

}
