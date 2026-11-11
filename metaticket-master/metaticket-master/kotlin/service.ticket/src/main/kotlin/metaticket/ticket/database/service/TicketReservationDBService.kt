package metaticket.ticket.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.pojo.db.TicketReservationDocument
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import org.bson.types.ObjectId
import java.util.*


/**
 *
 * @author dongcatchay
 */
class TicketReservationDBService(
    private val col : MongoCollection<TicketReservationDocument>
) : Logging {

    suspend fun insertReservation(entity: TicketReservationDocument): String? {
        val result = col.insertOne(entity)
        return withContext(Dispatchers.IO) { result.insertedId?.toString() }
    }

    suspend fun updateStatus(reservationId: String, oldStatus: TicketReservationStatus, newStatus: TicketReservationStatus, before: Boolean = false): TicketReservationDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(reservationId)),
            Filters.eq(TicketReservationDocument::status.name, oldStatus)
        )

        val updates = Updates.combine(
            Updates.set(TicketReservationDocument::status.name, newStatus),
            Updates.inc(TicketReservationDocument::version.name, 1),
            Updates.set(TicketReservationDocument::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(if (before) ReturnDocument.BEFORE else ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, updates, options) }
    }

    suspend fun loadReservationById(id: String): TicketReservationDocument? {
        val filter = Filters.eq("_id", ObjectId(id))
        return withContext(Dispatchers.IO) { col.find(filter).firstOrNull() }
    }

    suspend fun loadListReservation(ids: List<String>): List<TicketReservationDocument> {
        val filter = Filters.`in`("_id", ids.map { ObjectId(it) })
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    suspend fun loadReservationByTicket(tkId: String, status: List<TicketReservationStatus>): List<TicketReservationDocument> {
        val filter = Filters.and( listOfNotNull(
            if (status.isNotEmpty()) Filters.`in`(TicketReservationDocument::status.name, status) else null,
            Filters.eq("${TicketReservationDocument::details.name}.${TicketReservationDetailsDocument::ticketId.name}", ObjectId(tkId))
        ))
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    /**
     * Load ticket reservation that state is in order paying or success by ticket category
     */
    suspend fun loadReservationOrderingByTicketCat(showId: String, sessionId: String, category: String): List<TicketReservationDocument> {
        val filter = Filters.and( listOfNotNull(
            Filters.eq(TicketReservationDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketReservationDocument::sessionId.name, ObjectId(sessionId)),
            Filters.eq("${TicketReservationDocument::details.name}.${TicketReservationDetailsDocument::priceCat.name}", category),
            Filters.`in`(TicketReservationDocument::status.name, listOf(TicketReservationStatus.OrderPaying, TicketReservationStatus.OrderSuccessful)),
        ))
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    suspend fun loadReservationByStatusAndUpdatedTime(showId: String, sessionId: String, status: List<TicketReservationStatus>, updatedTime: Long): List<TicketReservationDocument> {
        val filters = Filters.and(
            Filters.eq(TicketReservationDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketReservationDocument::sessionId.name, ObjectId(sessionId)),
            Filters.`in`(TicketReservationDocument::status.name, status),
            Filters.lte(TicketReservationDocument::lastModified.name, Date(updatedTime))
        )
        return withContext(Dispatchers.IO) { col.find(filters) }.toList()
    }
}
