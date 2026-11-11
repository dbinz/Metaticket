package metaticket.ticket.database.service

import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticket.pojo.db.CalendarDayStatus
import metaticket.ticket.pojo.db.TicketCalendarDayDocument
import org.bson.types.ObjectId
import java.util.*

class TicketCalendarDayDBService(
    private val col: MongoCollection<TicketCalendarDayDocument>
) : Logging {

    /** Unique (sessionId, date): hai admin cùng sinh lịch không tạo trùng ngày. */
    suspend fun ensureIndexes() {
        withContext(Dispatchers.IO) {
            col.createIndex(
                Indexes.ascending(TicketCalendarDayDocument::sessionId.name, TicketCalendarDayDocument::date.name),
                IndexOptions().unique(true),
            )
        }
    }

    /** @return false khi ngày đã tồn tại. */
    suspend fun insertIfAbsent(day: TicketCalendarDayDocument): Boolean {
        return try {
            withContext(Dispatchers.IO) { col.insertOne(day) }
            true
        } catch (e: MongoWriteException) {
            if (e.error.category == ErrorCategory.DUPLICATE_KEY) false else throw e
        }
    }

    suspend fun loadDay(sessionId: String, date: String): TicketCalendarDayDocument? {
        val filter = Filters.and(
            Filters.eq(TicketCalendarDayDocument::sessionId.name, ObjectId(sessionId)),
            Filters.eq(TicketCalendarDayDocument::date.name, date),
        )
        return withContext(Dispatchers.IO) { col.find(filter).firstOrNull() }
    }

    /** Ngày trong [fromDate, toDate] (chuỗi ISO so sánh được theo thứ tự từ điển). */
    suspend fun loadDays(sessionId: String, fromDate: String, toDate: String): List<TicketCalendarDayDocument> {
        val filter = Filters.and(
            Filters.eq(TicketCalendarDayDocument::sessionId.name, ObjectId(sessionId)),
            Filters.gte(TicketCalendarDayDocument::date.name, fromDate),
            Filters.lte(TicketCalendarDayDocument::date.name, toDate),
        )
        return withContext(Dispatchers.IO) { col.find(filter).toList() }
    }

    suspend fun loadAllCapacityIds(sessionId: String): List<String> {
        val filter = Filters.and(
            Filters.eq(TicketCalendarDayDocument::sessionId.name, ObjectId(sessionId)),
            Filters.ne(TicketCalendarDayDocument::capacityId.name, null),
        )
        return withContext(Dispatchers.IO) { col.find(filter).toList() }.mapNotNull { it.capacityId }
    }

    suspend fun updateStatus(sessionId: String, dates: Collection<String>, status: CalendarDayStatus): Long {
        if (dates.isEmpty()) return 0
        val filter = Filters.and(
            Filters.eq(TicketCalendarDayDocument::sessionId.name, ObjectId(sessionId)),
            Filters.`in`(TicketCalendarDayDocument::date.name, dates),
            Filters.ne(TicketCalendarDayDocument::status.name, status),
        )
        val updates = Updates.combine(
            Updates.set(TicketCalendarDayDocument::status.name, status),
            Updates.inc(TicketCalendarDayDocument::version.name, 1),
            Updates.set(TicketCalendarDayDocument::lastModified.name, Date()),
        )
        return withContext(Dispatchers.IO) { col.updateMany(filter, updates) }.modifiedCount
    }

    /** Gắn capacity cho ngày chưa có; false khi ngày đã có capacity khác (admin khác vừa gắn). */
    suspend fun attachCapacity(dayId: String, capacityId: String): Boolean {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(dayId)),
            Filters.eq(TicketCalendarDayDocument::capacityId.name, null),
        )
        val updates = Updates.combine(
            Updates.set(TicketCalendarDayDocument::capacityId.name, ObjectId(capacityId)),
            Updates.inc(TicketCalendarDayDocument::version.name, 1),
            Updates.set(TicketCalendarDayDocument::lastModified.name, Date()),
        )
        return withContext(Dispatchers.IO) { col.updateOne(filter, updates) }.modifiedCount == 1L
    }

    suspend fun deleteDay(dayId: String): Boolean {
        return withContext(Dispatchers.IO) { col.deleteOne(Filters.eq("_id", ObjectId(dayId))) }.deletedCount == 1L
    }
}
