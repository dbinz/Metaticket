package metaticket.order.datacollector.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.datacollector.OrderDataCollectedStatus
import metaticket.order.datacollector.pojo.db.OrderDataCollector
import org.bson.BsonValue
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */
class OrderDataCollectorDBService(
    private val col: MongoCollection<OrderDataCollector>
): Logging {

    suspend fun saveCollector(data: OrderDataCollector): BsonValue? {
        return withContext(Dispatchers.IO) {
            col.insertOne(data)
        }.insertedId
    }

    suspend fun getDataById(id: String): OrderDataCollector? {
        val filter = Filters.eq("_id", ObjectId(id))
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }

    suspend fun assignCollectedIdAndStatus(id: String, status: OrderDataCollectedStatus): OrderDataCollector? {
        val filter = Filters.eq("_id", ObjectId(id))
        val update = Updates.combine(
            Updates.set(OrderDataCollector::dataCollectedId.name, ObjectId(id)),
            Updates.set(OrderDataCollector::dataCollectedStatus.name, status),
        )
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update) }
    }
}
