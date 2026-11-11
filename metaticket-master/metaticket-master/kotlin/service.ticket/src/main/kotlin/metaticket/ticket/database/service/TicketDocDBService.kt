package metaticket.ticket.database.service

import com.mongodb.ErrorCategory
import com.mongodb.MongoBulkWriteException
import com.mongodb.client.model.*
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticket.datastructure.TicketStatus
import metaticket.ticket.pojo.db.CountTicketOfShowDocument
import metaticket.ticket.pojo.db.TicketDocument
import metaticket.ticket.pojo.request.UpdateTicketDocRequest
import org.bson.types.ObjectId
import java.util.*


/**
 *
 * @author dongcatchay
 */
class TicketDocDBService(
    private val col : MongoCollection<TicketDocument>
) : Logging {

    suspend fun insertManyTicketDocument(tickets : List<TicketDocument>) : Boolean {
        return withContext(Dispatchers.IO) { col.insertMany(tickets).wasAcknowledged() }
    }

    suspend fun updateTicketSlot(ticketIds: List<String>, slot: Int): UpdateResult {
        val combinedFilters = Filters.`in`("_id", ticketIds.map { ObjectId(it) })
        val updates = Updates.set(TicketDocument::ticketSlots.name, slot)

        return withContext(Dispatchers.IO) { col.updateMany(combinedFilters, updates) }
    }

    suspend fun updateTicketDocument(request: UpdateTicketDocRequest) : TicketDocument? {
        val filters = Filters.and(
            Filters.eq(TicketDocument::showId.name, ObjectId(request.showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(request.sessionId)),
            Filters.eq(TicketDocument::sid.name, request.sid),
        )
        val updates = Updates.combine(listOfNotNull(
            request.totCap
                ?.let { Updates.set(TicketDocument::totCap.name, it) }
                ?: Updates.unset(TicketDocument::totCap.name),
            request.capacityId
                ?.let { Updates.set(TicketDocument::capacityId.name, ObjectId(it)) }
                ?: Updates.unset(TicketDocument::capacityId.name),
            request.dname
                ?.let { Updates.set(TicketDocument::dname.name, it) }
                ?: Updates.unset(TicketDocument::dname.name),
            request.policy
                ?.let { Updates.set(TicketDocument::policy.name, it) }
                ?: Updates.unset(TicketDocument::policy.name),
            Updates.set(TicketDocument::priceCat.name, request.priceCat),
        ))

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filters, updates, options) }
    }

    suspend fun loadTicketDocById(id: String): TicketDocument? {
        val filter = Filters.eq("_id", ObjectId(id))
        return withContext(Dispatchers.IO) { col.find(filter).firstOrNull()}
    }

    suspend fun loadTicketDocByIds(ids: List<String>): List<TicketDocument> {
        val filter = Filters.`in`("_id", ids.map { ObjectId(it) })
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    suspend fun loadTicketDocBySids(showId: String, sessionId: String, sids: List<String>): List<TicketDocument> {
        val filter = Filters.and(
            Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
            Filters.`in`(TicketDocument::sid.name, sids),
            )
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    suspend fun loadAllTicketDoc(showId: String, sessionId: String): List<TicketDocument> {
        val filter = Filters.and(
            Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    suspend fun loadOpenTicketDoc(showId: String, sessionId: String): List<TicketDocument> {
        val filter = Filters.and(
            Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
            Filters.eq(TicketDocument::status.name, TicketStatus.Opened),
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    suspend fun loadTicketDocByTime(showId: String, sessionId: String, startT: Long, endT: Long?): List<TicketDocument> {
        val filter = Filters.and(
            Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
            Filters.gte(TicketDocument::lastModified.name, Date(startT)),
            Filters.lte(TicketDocument::lastModified.name, endT?.let { Date(it) } ?: Date())
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    suspend fun updateAllTicketStatus(showId: String, sessionId: String, status: TicketStatus): UpdateResult {
        val filter = Filters.and(
            Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
        )
        val updates = Updates.combine(
            Updates.set(TicketDocument::status.name, status),
            Updates.inc(TicketDocument::version.name, 1),
            Updates.set(TicketDocument::lastModified.name, Date())
        )
        return withContext(Dispatchers.IO) { col.updateMany(filter, updates) }
    }

    suspend fun addReservedCap(pairs: List<Pair<String, Int>>): Boolean {
        val updates = pairs.map { (ticketId: String, quantity: Int) ->
            val filter = Filters.eq("_id", ObjectId(ticketId))
            val updates = Updates.combine(
                Updates.inc(TicketDocument::resCap.name, quantity),
                Updates.inc(TicketDocument::remCap.name, -quantity),
                Updates.inc(TicketDocument::version.name, 1),
                Updates.set(TicketDocument::lastModified.name, Date())
            )
            UpdateOneModel<TicketDocument>(filter, updates)
        }

        return withContext(Dispatchers.IO) { col.bulkWrite(updates).wasAcknowledged() }
    }

    /**
     * Reserve capacity directly in Mongo, only when enough remains. Used when the Redis counter does not
     * exist (session not opened yet), so Mongo is the only source of truth and the check must be atomic here.
     * @return true if the capacity was taken.
     */
    suspend fun tryAddReservedCap(ticketId: String, quantity: Int): Boolean {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(ticketId)),
            Filters.gte(TicketDocument::remCap.name, quantity),
        )
        val updates = Updates.combine(
            Updates.inc(TicketDocument::resCap.name, quantity),
            Updates.inc(TicketDocument::remCap.name, -quantity),
            Updates.inc(TicketDocument::version.name, 1),
            Updates.set(TicketDocument::lastModified.name, Date())
        )
        return withContext(Dispatchers.IO) { col.updateOne(filter, updates) }.modifiedCount == 1L
    }

    suspend fun countTicket(showId: String, sessionId: String?=null): List<CountTicketOfShowDocument> {
        val filter = Aggregates.match(Filters.and(
            listOfNotNull(
                Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
                sessionId?.let { Filters.eq(TicketDocument::sessionId.name, ObjectId(it)) },
            )
        ))
        val group = Aggregates.group("\$${TicketDocument::sessionId.name}", Accumulators.sum("noTicket", 1))
        return withContext(Dispatchers.IO) { col.aggregate<CountTicketOfShowDocument>(listOf(filter, group)) }.toList()
    }

    suspend fun loadTicketDocByCat(showId: String, sessionId: String, priceCat: String): List<TicketDocument> {
        val filter = Filters.and(
            Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
            Filters.eq(TicketDocument::priceCat.name, priceCat),
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    suspend fun deleteTickets(ticketIds: List<String>): Long {
        val filters = Filters.`in`("_id", ticketIds.map { ObjectId(it) })
        return withContext(Dispatchers.IO) { col.deleteMany(filters) }.deletedCount
    }

    suspend fun updateTicketPriceAndPriceCat(ticketIds: List<String>, basePrice: Long, priceCat: String): Long {
        val filters = Filters.`in`("_id", ticketIds.map { ObjectId(it) })
        val updates = Updates.combine(
            Updates.set(TicketDocument::basePrice.name, basePrice),
            Updates.set(TicketDocument::priceCat.name, priceCat),
        )
        return withContext(Dispatchers.IO) { col.updateMany(filters, updates) }.modifiedCount
    }

    /** Đổi tên hạng vé mà giữ giá: vé theo lịch có giá riêng từng ngày, không được ghi đè bằng giá mặc định. */
    suspend fun updateTicketPriceCat(ticketIds: List<String>, priceCat: String): Long {
        val filters = Filters.`in`("_id", ticketIds.map { ObjectId(it) })
        val updates = Updates.combine(
            Updates.set(TicketDocument::priceCat.name, priceCat),
            Updates.inc(TicketDocument::version.name, 1),
            Updates.set(TicketDocument::lastModified.name, Date()),
        )
        return withContext(Dispatchers.IO) { col.updateMany(filters, updates) }.modifiedCount
    }

    /**
     * Index cho vé theo lịch. Unique (sessionId, calendarDate, sid) chỉ áp cho vé có calendarDate (partial), nên vé
     * suất thường không bị ảnh hưởng, còn hai lần sinh lịch đồng thời không tạo trùng vé của cùng ngày.
     */
    suspend fun ensureCalendarIndexes() {
        withContext(Dispatchers.IO) {
            col.createIndex(
                Indexes.ascending(TicketDocument::sessionId.name, TicketDocument::calendarDate.name, TicketDocument::sid.name),
                IndexOptions().unique(true).partialFilterExpression(Filters.exists(TicketDocument::calendarDate.name)),
            )
        }
    }

    /** Vé theo lịch trong [fromDate, toDate]; [sids] null = mọi loại vé. */
    suspend fun loadCalendarTickets(
        showId: String, sessionId: String, fromDate: String, toDate: String, sids: Collection<String>? = null,
    ): List<TicketDocument> {
        val filter = Filters.and(listOfNotNull(
            Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
            Filters.gte(TicketDocument::calendarDate.name, fromDate),
            Filters.lte(TicketDocument::calendarDate.name, toDate),
            sids?.let { Filters.`in`(TicketDocument::sid.name, it) },
        ))
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    suspend fun loadCalendarTicketsByTime(
        showId: String, sessionId: String, date: String, startT: Long, endT: Long?,
    ): List<TicketDocument> {
        val filter = Filters.and(
            Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
            Filters.eq(TicketDocument::calendarDate.name, date),
            Filters.gte(TicketDocument::lastModified.name, Date(startT)),
            Filters.lte(TicketDocument::lastModified.name, endT?.let { Date(it) } ?: Date())
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    /** Vé suất thường đang mở của session (bỏ vé theo lịch) — dùng khi nạp bộ đếm Redis lúc mở bán. */
    suspend fun loadOpenNonCalendarTicketDoc(showId: String, sessionId: String): List<TicketDocument> {
        val filter = Filters.and(
            Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
            Filters.eq(TicketDocument::status.name, TicketStatus.Opened),
            Filters.eq(TicketDocument::calendarDate.name, null),
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    /** @return số vé thêm được; vé trùng (đã có từ lần sinh khác) bị bỏ qua thay vì làm hỏng cả lô. */
    suspend fun insertCalendarTickets(tickets: List<TicketDocument>): Int {
        if (tickets.isEmpty()) return 0
        return try {
            withContext(Dispatchers.IO) {
                col.insertMany(tickets, InsertManyOptions().ordered(false))
            }.insertedIds.size
        } catch (e: MongoBulkWriteException) {
            if (e.writeErrors.any { ErrorCategory.fromErrorCode(it.code) != ErrorCategory.DUPLICATE_KEY }) throw e
            e.writeResult.insertedCount
        }
    }

    /**
     * Đổi giá cả lô trong một lệnh: chỉ vé chưa có vé bán/giữ (resCap = 0) — điều kiện nằm trong filter nên vẫn không chen
     * được giữa lúc kiểm tra và lúc ghi. @return số vé đã đổi.
     */
    suspend fun updateCalendarPricesIfUnsold(ticketIds: List<String>, basePrice: Long): Long {
        if (ticketIds.isEmpty()) return 0
        val filter = Filters.and(
            Filters.`in`("_id", ticketIds.map { ObjectId(it) }),
            Filters.eq(TicketDocument::resCap.name, 0),
            Filters.ne(TicketDocument::basePrice.name, basePrice),
        )
        val updates = Updates.combine(
            Updates.set(TicketDocument::basePrice.name, basePrice),
            Updates.inc(TicketDocument::version.name, 1),
            Updates.set(TicketDocument::lastModified.name, Date())
        )
        return withContext(Dispatchers.IO) { col.updateMany(filter, updates) }.modifiedCount
    }

    /** Vé trong [ticketIds] có giá khác [basePrice]; gọi sau [updateCalendarPricesIfUnsold] thì đó là các vé đã bán/giữ. */
    suspend fun loadCalendarTicketsPricedOtherThan(ticketIds: List<String>, basePrice: Long): List<TicketDocument> {
        if (ticketIds.isEmpty()) return emptyList()
        val filter = Filters.and(
            Filters.`in`("_id", ticketIds.map { ObjectId(it) }),
            Filters.ne(TicketDocument::basePrice.name, basePrice),
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    /**
     * Cộng/trừ quota. Giảm ([delta] < 0) chỉ khi chưa có vé bán/giữ và còn đủ chỗ; tăng luôn được.
     * Điều kiện nằm trong filter nên không chen được giữa lúc kiểm tra và lúc ghi.
     */
    suspend fun changeCalendarQuota(ticketId: String, delta: Int): Boolean {
        val filter = Filters.and(listOfNotNull(
            Filters.eq("_id", ObjectId(ticketId)),
            if (delta < 0) Filters.eq(TicketDocument::resCap.name, 0) else null,
            if (delta < 0) Filters.gte(TicketDocument::remCap.name, -delta) else null,
        ))
        val updates = Updates.combine(
            Updates.inc(TicketDocument::totCap.name, delta),
            Updates.inc(TicketDocument::remCap.name, delta),
            Updates.inc(TicketDocument::version.name, 1),
            Updates.set(TicketDocument::lastModified.name, Date())
        )
        return withContext(Dispatchers.IO) { col.updateOne(filter, updates) }.modifiedCount == 1L
    }

    suspend fun deleteCalendarTicketIfUnsold(ticketId: String): Boolean {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(ticketId)),
            Filters.eq(TicketDocument::resCap.name, 0),
        )
        return withContext(Dispatchers.IO) { col.deleteOne(filter) }.deletedCount == 1L
    }

    suspend fun countCalendarTicketsOfDate(sessionId: String, date: String): Long {
        val filter = Filters.and(
            Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
            Filters.eq(TicketDocument::calendarDate.name, date),
        )
        return withContext(Dispatchers.IO) { col.countDocuments(filter) }
    }

    /** Giá thấp nhất (> 0) của vé theo lịch từ [fromDate] trở đi — con số "Từ …đ" trên card storefront. */
    suspend fun calendarMinPrice(showId: String, sessionId: String, fromDate: String): Long? {
        val pipeline = listOf(
            Aggregates.match(Filters.and(
                Filters.eq(TicketDocument::showId.name, ObjectId(showId)),
                Filters.eq(TicketDocument::sessionId.name, ObjectId(sessionId)),
                Filters.gte(TicketDocument::calendarDate.name, fromDate),
                Filters.gt(TicketDocument::basePrice.name, 0),
            )),
            Aggregates.group(null, Accumulators.min("minPrice", "\$${TicketDocument::basePrice.name}")),
        )
        val result = withContext(Dispatchers.IO) { col.aggregate<org.bson.Document>(pipeline).firstOrNull() }
        return (result?.get("minPrice") as? Number)?.toLong()
    }
}
