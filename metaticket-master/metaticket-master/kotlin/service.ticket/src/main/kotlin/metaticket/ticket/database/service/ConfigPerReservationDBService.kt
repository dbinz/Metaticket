package metaticket.ticket.database.service

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticket.pojo.db.ConfigPerReservationDocument
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */
class ConfigPerReservationDBService(
    private val col: MongoCollection<ConfigPerReservationDocument>
): Logging {

    suspend fun insertConfig(entity: ConfigPerReservationDocument): String? {
        val result = col.insertOne(entity)
        return withContext(Dispatchers.IO) { result.insertedId?.toString() }
    }

    suspend fun loadConfigById(id: String): ConfigPerReservationDocument? {
        val filter = Filters.eq("_id", ObjectId(id))
        return withContext(Dispatchers.IO) { col.find(filter).firstOrNull()}
    }

    suspend fun loadConfigByShowAndSession(showId: String, sessionId: String): ConfigPerReservationDocument? {
        val filter = Filters.and(
            Filters.eq("showId", ObjectId(showId)),
            Filters.or(Filters.exists("sessionId", false), Filters.eq("sessionId", ObjectId(sessionId))),
        )
        return withContext(Dispatchers.IO) {
            val list = col.find(filter)
            list.firstOrNull { it.sessionId == sessionId }?:list.firstOrNull()
        }
    }
}
