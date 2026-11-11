package metaticket.order.datacollector.database.service

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.datacollector.pojo.db.OrderDataCollectorMapping
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */
class OrderDataCollectorMappingDBService(
    private val col: MongoCollection<OrderDataCollectorMapping>
): Logging {

    suspend fun getFieldMappingByShowAndSession(showId: String, sessionId: String): OrderDataCollectorMapping? {
        val filter = Filters.and(
            Filters.eq("showId", ObjectId(showId)),
            Filters.eq("sessionId", ObjectId(sessionId)),
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }
}
