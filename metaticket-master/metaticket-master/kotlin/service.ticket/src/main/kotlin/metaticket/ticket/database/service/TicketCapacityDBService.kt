package metaticket.ticket.database.service

import com.mongodb.client.model.*
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticket.pojo.db.TicketCapacityDocument
import org.bson.types.ObjectId
import java.util.*


/**
 *
 * @author dongcatchay
 */
class TicketCapacityDBService(
    private val col : MongoCollection<TicketCapacityDocument>
) : Logging {
    suspend fun insertTicketCapacity(entity : TicketCapacityDocument) : String? {
        return withContext(Dispatchers.IO){ col.insertOne(entity).insertedId?.toString() }
    }

    suspend fun loadTicketCapacityById(id: String): TicketCapacityDocument? {
        val filter = Filters.eq("_id", ObjectId(id))
        return withContext(Dispatchers.IO) { col.find(filter).firstOrNull()}
    }

    suspend fun loadTicketCapacityByIds(ids: List<String>): List<TicketCapacityDocument> {
        val filter = Filters.`in`("_id", ids.map { ObjectId(it) })
        return withContext(Dispatchers.IO) { col.find(filter).toList() }
    }

    suspend fun loadAllTicketCapacity(showId: String, sessionId: String?=null, priceCat: String?=null): List<TicketCapacityDocument> {
        val filter = Filters.and(listOfNotNull(
            Filters.eq(TicketCapacityDocument::showId.name, ObjectId(showId)),
            sessionId?.let { Filters.eq(TicketCapacityDocument::sessionId.name, ObjectId(sessionId)) },
            priceCat?.let { Filters.eq(TicketCapacityDocument::priceCat.name, priceCat)},
        ))
        return withContext(Dispatchers.IO) { col.find(filter).toList() }
    }

    suspend fun addReservedCap(tcId: String, quantity: Int): TicketCapacityDocument? {
        val filter = Filters.eq("_id", ObjectId(tcId))

        val updates = Updates.combine(
            Updates.inc(TicketCapacityDocument::resCap.name, quantity),
            Updates.inc(TicketCapacityDocument::remCap.name, -quantity),
            Updates.inc(TicketCapacityDocument::version.name, 1),
            Updates.set(TicketCapacityDocument::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, updates, options) }
    }

    suspend fun addReservedCap(pairs: List<Pair<String, Int>>): Boolean {
        val updates = pairs.groupBy { (id: String, _ ) -> id }.map { (id: String, g) ->
            val totalQuantity = g.sumOf { ( _, quantity) -> quantity }
            val filter = Filters.eq("_id", ObjectId(id))
            val updates = Updates.combine(
                Updates.inc(TicketCapacityDocument::resCap.name, totalQuantity),
                Updates.inc(TicketCapacityDocument::remCap.name, -totalQuantity),
                Updates.inc(TicketCapacityDocument::version.name, 1),
                Updates.set(TicketCapacityDocument::lastModified.name, Date())
            )
            UpdateOneModel<TicketCapacityDocument>(filter, updates)
        }

        return withContext(Dispatchers.IO) { col.bulkWrite(updates).wasAcknowledged() }
    }

    /**
     * Same contract as [TicketDocDBService.tryAddReservedCap], for shared capacities.
     */
    suspend fun tryAddReservedCap(tcId: String, quantity: Int): Boolean {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(tcId)),
            Filters.gte(TicketCapacityDocument::remCap.name, quantity),
        )
        val updates = Updates.combine(
            Updates.inc(TicketCapacityDocument::resCap.name, quantity),
            Updates.inc(TicketCapacityDocument::remCap.name, -quantity),
            Updates.inc(TicketCapacityDocument::version.name, 1),
            Updates.set(TicketCapacityDocument::lastModified.name, Date())
        )
        return withContext(Dispatchers.IO) { col.updateOne(filter, updates) }.modifiedCount == 1L
    }

    /** Cùng quy tắc với [TicketDocDBService.changeCalendarQuota], cho sức chứa chung cả ngày. */
    suspend fun changeCapacity(tcId: String, delta: Int): Boolean {
        val filter = Filters.and(listOfNotNull(
            Filters.eq("_id", ObjectId(tcId)),
            if (delta < 0) Filters.eq(TicketCapacityDocument::resCap.name, 0) else null,
            if (delta < 0) Filters.gte(TicketCapacityDocument::remCap.name, -delta) else null,
        ))
        val updates = Updates.combine(
            Updates.inc(TicketCapacityDocument::totCap.name, delta),
            Updates.inc(TicketCapacityDocument::remCap.name, delta),
            Updates.inc(TicketCapacityDocument::version.name, 1),
            Updates.set(TicketCapacityDocument::lastModified.name, Date())
        )
        return withContext(Dispatchers.IO) { col.updateOne(filter, updates) }.modifiedCount == 1L
    }

    suspend fun deleteCapacityIfUnsold(tcId: String): Boolean {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(tcId)),
            Filters.eq(TicketCapacityDocument::resCap.name, 0),
        )
        return withContext(Dispatchers.IO) { col.deleteOne(filter) }.deletedCount == 1L
    }

    suspend fun replace(ticketCapacity: TicketCapacityDocument): UpdateResult {
        val filter = Filters.and(Filters.eq("_id", ObjectId(ticketCapacity.id)),)
        return withContext(Dispatchers.IO) { col.replaceOne(filter, ticketCapacity) }
    }
}
