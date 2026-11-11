package metaticket.show.database.service

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.show.pojo.db.CityDocument
import org.bson.types.ObjectId

class CityDBService(
    private val col : MongoCollection<CityDocument>,
) {
    suspend fun loadAllCity(): List<CityDocument> {
        return withContext(Dispatchers.IO) { col.find<CityDocument>() }.toList()
    }

    suspend fun loadCity(cityId: String): CityDocument? {
        val filter = Filters.eq("_id", ObjectId(cityId))
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }
}