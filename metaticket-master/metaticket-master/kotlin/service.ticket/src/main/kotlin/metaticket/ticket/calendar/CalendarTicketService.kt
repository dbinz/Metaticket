package metaticket.ticket.calendar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.show.datastructure.CalendarRules
import metaticket.show.datastructure.SessionType
import metaticket.show.datastructure.ShowSessionStatus
import metaticket.show.grpc.ShowMessage
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.database.service.CategoryMappingDBService
import metaticket.ticket.database.service.TicketCalendarDayDBService
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.datastructure.SeatType
import metaticket.ticket.datastructure.TicketStatus
import metaticket.ticket.pojo.db.CalendarDayStatus
import metaticket.ticket.pojo.db.TicketCalendarDayDocument
import metaticket.ticket.pojo.db.TicketCapacityDocument
import metaticket.ticket.pojo.db.TicketDocument
import metaticket.ticket.pojo.request.BulkUpdateCalendarRequest
import metaticket.ticket.pojo.request.DeleteCalendarRequest
import metaticket.ticket.pojo.request.GenerateCalendarRequest
import metaticket.ticket.pojo.response.*
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.redis.UpdateRedisFailedConstants
import org.bson.types.ObjectId
import java.time.Instant
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CalendarException(val status: Int, message: String) : RuntimeException(message) {
    companion object {
        fun badRequest(message: String) = CalendarException(400, message)
        fun notFound(message: String) = CalendarException(404, message)
        fun forbidden(message: String) = CalendarException(403, message)
    }
}

fun ShowMessage.GetSessionResponse.isCalendar(): Boolean =
    hasSessionType() && sessionType == SessionType.CALENDAR.name

/**
 * Lịch bán của session vé theo lịch: mỗi (ngày, loại vé) là một [TicketDocument] có calendarDate, nên giữ chỗ, giá,
 * đơn hàng, ticket slot dùng lại nguyên luồng theo ticketId. Quy tắc sửa: ô đã có vé bán/giữ (resCap > 0) không đổi
 * giá, không giảm quota, không xóa — chỉ tăng quota hoặc ngừng bán ngày.
 */
class CalendarTicketService(
    private val showSessionCache: ShowSessionCache,
    private val ticketDocDB: TicketDocDBService,
    private val ticketCapacityDB: TicketCapacityDBService,
    private val calendarDayDB: TicketCalendarDayDBService,
    private val categoryMappingDB: CategoryMappingDBService,
    private val redisClient: RedisAsyncClient,
) : Logging {

    private companion object {
        const val INSERT_CHUNK = 1000
        const val PRICE_CHUNK = 1000
        const val NULL_VALUE_MESSAGE = "Dữ liệu có giá trị trống (null)"
        val MAX_PRICE_TEXT: String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(CalendarPlanner.MAX_PRICE)
    }

    private sealed interface ChangeResult {
        data object Unchanged : ChangeResult
        data object Changed : ChangeResult
        data class Skipped(val reason: CalendarSkipReason) : ChangeResult
    }

    suspend fun generate(req: GenerateCalendarRequest, opUserId: String): GenerateCalendarResponse {
        if (req.hasNullValue()) throw CalendarException.badRequest(NULL_VALUE_MESSAGE)
        val session = loadCalendarSession(req.showId, req.sessionId)
        val from = parseDate(req.fromDate)
        val to = parseDate(req.toDate)
        val (firstDate, lastDate) = sessionDateRange(session)
        CalendarPlanner.validateRange(from, to, firstDate, lastDate)?.let { throw CalendarException.badRequest(it) }
        CalendarPlanner.validateDaysOfWeek(req.daysOfWeek)?.let { throw CalendarException.badRequest(it) }
        val daysOfWeek = req.daysOfWeek.toSet()
        validateTypes(req, daysOfWeek)

        val categories = categoryMappingDB.findByShowAndSession(req.showId, req.sessionId)?.catMapping.orEmpty()
        val types = req.types.map { type ->
            val category = categories[type.category]
                ?: throw CalendarException.badRequest(
                    "Không tìm thấy hạng vé ${CalendarPlanner.echoInput(type.category)} trong cấu hình hạng vé của suất"
                )
            type to category.displayName
        }

        val dates = CalendarPlanner.expandDates(from, to, daysOfWeek, req.excludeDates.map { parseDate(it) }.toSet())
        if (dates.isEmpty()) throw CalendarException.badRequest("Không có ngày nào khớp lịch đã chọn")

        val existingTicketDocs = ticketDocDB.loadCalendarTickets(req.showId, req.sessionId, from.toString(), to.toString())
        val existingTickets = existingTicketDocs.map { it.calendarDate to it.sid }.toSet()
        val existingTicketsByDate = existingTicketDocs.groupBy { it.calendarDate }
        val existingDays = calendarDayDB.loadDays(req.sessionId, from.toString(), to.toString()).associateBy { it.date }
        val ticketStatus = if (session.status == ShowSessionStatus.Open.name) TicketStatus.Opened else TicketStatus.Created

        val skipped = mutableListOf<CalendarSkip>()
        val newTickets = mutableListOf<TicketDocument>()
        var createdDays = 0
        for (date in dates) {
            val dateStr = date.toString()
            val existingDay = existingDays[dateStr]
            if (existingDay == null) {
                if (createDay(req, date)) createdDays++
            } else {
                attachMissingCapacity(req, existingDay, date, existingTicketsByDate[dateStr].orEmpty())?.let { skipped += it }
            }
            types.forEach { (type, priceCat) ->
                if ((dateStr to type.category) in existingTickets) {
                    skipped += CalendarSkip(dateStr, type.category, CalendarSkipReason.EXISTS)
                    return@forEach
                }
                val quota = type.quota.resolve(date)
                newTickets += TicketDocument(
                    showId = req.showId,
                    sessionId = req.sessionId,
                    sid = type.category,
                    dname = type.dname?.trim()?.ifEmpty { null },
                    seatType = SeatType.SeatZone,
                    status = ticketStatus,
                    totCap = quota,
                    remCap = quota,
                    resCap = 0,
                    priceCat = priceCat,
                    basePrice = type.price.resolve(date),
                    policy = type.policy?.trim()?.ifEmpty { null },
                    ticketSlots = 1,
                    calendarDate = dateStr,
                )
            }
        }

        // Vé trùng do hai lần sinh đồng thời bị unique index chặn và chỉ làm giảm số đếm, không làm hỏng cả lô.
        val createdTickets = newTickets.chunked(INSERT_CHUNK).sumOf { ticketDocDB.insertCalendarTickets(it) }
        logger.info("calendar generate, user {}, show {} session {} {}..{}: {} days, {} tickets, {} skipped",
            opUserId, req.showId, req.sessionId, from, to, createdDays, createdTickets, skipped.size)
        return GenerateCalendarResponse(createdDays, createdTickets, skipped)
    }

    suspend fun bulkUpdate(req: BulkUpdateCalendarRequest, opUserId: String): BulkUpdateCalendarResponse {
        if (req.price == null && req.quota == null && req.status == null && req.dailyCapacity == null) {
            throw CalendarException.badRequest("Chưa chọn thay đổi nào")
        }
        if (req.price != null && (req.price < 0 || req.price > CalendarPlanner.MAX_PRICE)) {
            throw CalendarException.badRequest("Giá vé phải từ 0 đến ${MAX_PRICE_TEXT}đ")
        }
        if (req.quota != null && req.quota <= 0) throw CalendarException.badRequest("Số lượng vé phải lớn hơn 0")
        if (req.dailyCapacity != null && req.dailyCapacity <= 0) throw CalendarException.badRequest("Sức chứa phải lớn hơn 0")

        val session = loadCalendarSession(req.showId, req.sessionId)
        val dates = selectDates(session, req.fromDate, req.toDate, req.daysOfWeek, req.dates)
        val today = CalendarRules.today()
        val skipped = mutableListOf<CalendarSkip>()
        var updated = 0

        val days = calendarDayDB.loadDays(req.sessionId, req.fromDate, req.toDate).filter { it.date in dates }
        val tickets = ticketDocDB.loadCalendarTickets(req.showId, req.sessionId, req.fromDate, req.toDate, req.categories)
            .filter { it.calendarDate in dates }

        if (req.status != null) {
            updated += calendarDayDB.updateStatus(req.sessionId, days.map { it.date }, req.status).toInt()
        }

        // Giá/quota/sức chứa của ngày đã qua không còn ý nghĩa, bỏ qua để báo cáo không lẫn.
        val (pastTickets, futureTickets) = tickets.partition { isPast(it.calendarDate, today) }
        if (req.price != null || req.quota != null) {
            pastTickets.forEach { skipped += CalendarSkip(it.calendarDate.orEmpty(), it.sid, CalendarSkipReason.PAST_DATE) }
        }
        if (req.price != null) updated += changePrices(futureTickets, req.price, skipped)
        if (req.quota != null) {
            val results = mapBounded("bulk quota", futureTickets, { it.id }) { changeTicketQuota(it, req.quota) }
            futureTickets.zip(results).forEach { (ticket, result) ->
                when (result) {
                    ChangeResult.Unchanged -> {}
                    ChangeResult.Changed -> updated++
                    is ChangeResult.Skipped -> skipped += CalendarSkip(ticket.calendarDate.orEmpty(), ticket.sid, result.reason)
                }
            }
        }

        if (req.dailyCapacity != null) {
            // Sức chứa chung giới hạn mọi hạng vé của ngày, nên "ngày đã bán" phải xét cả hạng bị lọc khỏi request.
            val ticketsOfDays = if (req.categories == null) tickets else {
                ticketDocDB.loadCalendarTickets(req.showId, req.sessionId, req.fromDate, req.toDate).filter { it.calendarDate in dates }
            }
            val ticketsByDate = ticketsOfDays.groupBy { it.calendarDate }
            val (pastDays, futureDays) = days.partition { isPast(it.date, today) }
            pastDays.forEach { skipped += CalendarSkip(it.date, null, CalendarSkipReason.PAST_DATE) }
            val results = mapBounded("bulk daily capacity", futureDays, { it.id }) {
                changeDayCapacity(it, req.dailyCapacity, ticketsByDate[it.date].orEmpty())
            }
            futureDays.zip(results).forEach { (day, result) ->
                when (result) {
                    ChangeResult.Unchanged -> {}
                    ChangeResult.Changed -> updated++
                    is ChangeResult.Skipped -> skipped += CalendarSkip(day.date, null, result.reason)
                }
            }
        }

        val scope = "range=${req.fromDate}..${req.toDate}, dow=${req.daysOfWeek ?: "all"}, dates=${req.dates?.size ?: "all"}, cats=${req.categories?.size ?: "all"}, price=${req.price}, quota=${req.quota}, status=${req.status}, cap=${req.dailyCapacity}"
        logger.info("calendar bulk update, user {}, show {} session {} [{}]: updated {}, skipped {}",
            opUserId, req.showId, req.sessionId, scope, updated, skipped.size)
        return BulkUpdateCalendarResponse(updated, skipped)
    }

    suspend fun delete(req: DeleteCalendarRequest, opUserId: String): DeleteCalendarResponse {
        val session = loadCalendarSession(req.showId, req.sessionId)
        val dates = selectDates(session, req.fromDate, req.toDate, req.daysOfWeek, req.dates)
        val skipped = mutableListOf<CalendarSkip>()
        var deletedTickets = 0

        val tickets = ticketDocDB.loadCalendarTickets(req.showId, req.sessionId, req.fromDate, req.toDate, req.categories)
            .filter { it.calendarDate in dates }
        val ticketResults = mapBounded("delete tickets", tickets, { it.id }) { deleteTicketIfUntouched(it) }
        tickets.zip(ticketResults).forEach { (ticket, reason) ->
            if (reason == null) deletedTickets++ else skipped += CalendarSkip(ticket.calendarDate.orEmpty(), ticket.sid, reason)
        }

        val days = calendarDayDB.loadDays(req.sessionId, req.fromDate, req.toDate).filter { it.date in dates }
        val deletedDays = mapBounded("delete days", days, { it.id }) { deleteDayIfEmpty(req.sessionId, it) }.count { it }

        val scope = "range=${req.fromDate}..${req.toDate}, dow=${req.daysOfWeek ?: "all"}, dates=${req.dates?.size ?: "all"}, cats=${req.categories?.size ?: "all"}"
        logger.info("calendar delete, user {}, show {} session {} [{}]: {} tickets, {} days, {} skipped",
            opUserId, req.showId, req.sessionId, scope, deletedTickets, deletedDays, skipped.size)
        return DeleteCalendarResponse(deletedTickets, deletedDays, skipped)
    }

    suspend fun adminMonth(showId: String, sessionId: String, month: String): List<CalendarAdminDay> {
        loadCalendarSession(showId, sessionId)
        val (from, to) = monthRange(month)
        val days = calendarDayDB.loadDays(sessionId, from, to)
        val ticketsByDate = ticketDocDB.loadCalendarTickets(showId, sessionId, from, to).groupBy { it.calendarDate }
        val capacities = loadCapacities(days)

        return days.sortedBy { it.date }.map { day ->
            CalendarAdminDay(
                date = day.date,
                status = day.status,
                capacity = day.capacityId?.let { capacities[it] }?.let {
                    CalendarAdminCapacity(it.id, it.totCap, it.remCap, it.resCap)
                },
                tickets = ticketsByDate[day.date].orEmpty().sortedBy { it.sid }.map { it.toAdminTicket() },
            )
        }
    }

    suspend fun publicMonth(showId: String, sessionId: String, month: String, now: Instant = Instant.now()): List<CalendarPublicDay> {
        val session = loadOpenCalendarSession(showId, sessionId)
        val (from, to) = monthRange(month)
        val days = calendarDayDB.loadDays(sessionId, from, to)
        val ticketsByDate = ticketDocDB.loadCalendarTickets(showId, sessionId, from, to).groupBy { it.calendarDate }
        val capacities = loadCapacities(days)

        return days.sortedBy { it.date }.map { day ->
            val tickets = ticketsByDate[day.date].orEmpty()
            val capacity = day.capacityId?.let { capacities[it] }
            val status = when {
                day.status == CalendarDayStatus.CLOSED -> CalendarPublicDayStatus.CLOSED
                CalendarReservationRules.unsellableReason(day, session, now) != null -> CalendarPublicDayStatus.UNAVAILABLE
                tickets.none { (it.remCap ?: 0) > 0 } || (capacity != null && capacity.remCap <= 0) -> CalendarPublicDayStatus.SOLD_OUT
                else -> CalendarPublicDayStatus.AVAILABLE
            }
            // Giá "từ" của ngày: bỏ vé 0đ như metasearch, chỉ trả 0 khi cả ngày chỉ có vé miễn phí.
            val prices = tickets.filter { (it.remCap ?: 0) > 0 }.ifEmpty { tickets }.map { it.basePrice }
            CalendarPublicDay(day.date, status, prices.filter { it > 0 }.minOrNull() ?: prices.minOrNull())
        }
    }

    /**
     * Vé của một ngày cho trang mua vé. Ngày không bán được (đóng, quá hạn, ngoài cửa sổ bán) trả remCap = 0 để
     * giao diện khóa chọn; giữ chỗ vẫn tự kiểm lại nên đây chỉ là hiển thị. Có sức chứa chung thì số còn lại của
     * từng vé không vượt quá số còn lại của ngày.
     */
    suspend fun ticketsForSale(
        showId: String, sessionId: String, date: String, changedSince: Long? = null, until: Long? = null,
        now: Instant = Instant.now(),
    ): List<TicketDocument> {
        val session = loadOpenCalendarSession(showId, sessionId)
        val parsed = parseDate(date).toString()
        val tickets = if (changedSince != null) {
            ticketDocDB.loadCalendarTicketsByTime(showId, sessionId, parsed, changedSince, until)
        } else {
            ticketDocDB.loadCalendarTickets(showId, sessionId, parsed, parsed)
        }
        if (tickets.isEmpty()) return tickets

        val day = calendarDayDB.loadDay(sessionId, parsed)
        if (CalendarReservationRules.unsellableReason(day, session, now) != null) {
            return tickets.onEach { it.remCap = 0 }
        }
        val capacity = day?.capacityId?.let { ticketCapacityDB.loadTicketCapacityById(it) } ?: return tickets
        return tickets.onEach { it.remCap = minOf(it.remCap ?: 0, capacity.remCap) }
    }

    suspend fun minPrice(showId: String, sessionId: String, fromDate: String): Long? =
        ticketDocDB.calendarMinPrice(showId, sessionId, fromDate)

    private fun validateTypes(req: GenerateCalendarRequest, daysOfWeek: Set<Int>) {
        if (req.types.isEmpty()) throw CalendarException.badRequest("Chọn ít nhất một loại vé")
        val duplicated = req.types.groupBy { it.category }.filterValues { it.size > 1 }.keys
        if (duplicated.isNotEmpty()) {
            throw CalendarException.badRequest("Loại vé bị lặp: ${CalendarPlanner.echoInput(duplicated.joinToString())}")
        }
        req.types.forEach { type ->
            val category = CalendarPlanner.echoInput(type.category)
            CalendarPlanner.validateByDayOfWeek("Giá $category", type.price, daysOfWeek)?.let { throw CalendarException.badRequest(it) }
            CalendarPlanner.validateByDayOfWeek("Số lượng $category", type.quota, daysOfWeek)?.let { throw CalendarException.badRequest(it) }
            if (type.price.allValues().any { it < 0 || it > CalendarPlanner.MAX_PRICE }) {
                throw CalendarException.badRequest("Giá $category phải từ 0 đến ${MAX_PRICE_TEXT}đ")
            }
            if (type.quota.allValues().any { it <= 0 }) throw CalendarException.badRequest("Số lượng $category phải lớn hơn 0")
        }
        req.dailyCapacity?.let { capacity ->
            CalendarPlanner.validateByDayOfWeek("Sức chứa ngày", capacity, daysOfWeek)?.let { throw CalendarException.badRequest(it) }
            if (capacity.allValues().any { it <= 0 }) throw CalendarException.badRequest("Sức chứa ngày phải lớn hơn 0")
        }
    }

    /** Tạo ngày (kèm sức chứa chung nếu có). @return false khi ngày đã có (lần sinh khác vừa tạo). */
    private suspend fun createDay(req: GenerateCalendarRequest, date: LocalDate): Boolean {
        val day = TicketCalendarDayDocument(showId = req.showId, sessionId = req.sessionId, date = date.toString())
        if (!calendarDayDB.insertIfAbsent(day)) return false
        val dailyCapacity = req.dailyCapacity?.resolve(date) ?: return true
        val capacity = TicketCapacityDocument(
            showId = req.showId,
            sessionId = req.sessionId,
            description = "Sức chứa ngày $date",
            totCap = dailyCapacity,
            remCap = dailyCapacity,
            resCap = 0,
            calendarDate = date.toString(),
        )
        ticketCapacityDB.insertTicketCapacity(capacity)
        if (!calendarDayDB.attachCapacity(day.id, capacity.id)) ticketCapacityDB.deleteCapacityIfUnsold(capacity.id)
        return true
    }

    /**
     * Ngày đã có mà chưa có sức chứa chung (lần sinh trước không đặt, hoặc tạo capacity lỗi sau khi đã tạo ngày): gắn
     * sức chứa của lần sinh này theo cùng điều kiện "ngày chưa bán vé nào" như sửa hàng loạt, thay vì bỏ qua im lặng.
     * Ngày đã có sức chứa thì giữ nguyên — đổi số là việc của sửa hàng loạt.
     * @return lý do không gắn được, null khi đã gắn hoặc không cần gắn.
     */
    private suspend fun attachMissingCapacity(
        req: GenerateCalendarRequest, day: TicketCalendarDayDocument, date: LocalDate, ticketsOfDate: List<TicketDocument>,
    ): CalendarSkip? {
        if (day.capacityId != null) return null
        val dailyCapacity = req.dailyCapacity?.resolve(date) ?: return null
        val result = changeDayCapacity(day, dailyCapacity, ticketsOfDate)
        return if (result is ChangeResult.Skipped) CalendarSkip(day.date, null, result.reason) else null
    }

    /**
     * Jackson (KotlinModule mặc định) không kiểm null của phần tử list/map và của tham số kiểu generic ([ByDayOfWeek]),
     * nên `{"default": null}` hay `[null]` lọt tới đây và thành NPE 500 — chặn sớm để trả 400.
     */
    private fun GenerateCalendarRequest.hasNullValue(): Boolean =
        hasNullElement(daysOfWeek) || hasNullElement(excludeDates) || hasNullElement(types) ||
            types.any { it.price.hasNullValue() || it.quota.hasNullValue() } || dailyCapacity?.hasNullValue() == true

    private fun ByDayOfWeek<*>.hasNullValue(): Boolean = default == null || hasNullElement(byDayOfWeek.values)

    private fun hasNullElement(values: Collection<*>?): Boolean = values?.any { it == null } == true

    /**
     * Đổi giá theo lô [PRICE_CHUNK] vé: một updateMany có điều kiện resCap = 0 (vẫn CAS trong filter như bản từng vé), rồi
     * đọc lại vé của lô còn giá khác — đó là vé đã bán/giữ lúc ghi. Từng vé một là N lệnh Mongo tuần tự: 366 ngày × 53 hạng
     * mất hơn 100s, quá timeout nginx 60s nên admin bấm lại và thao tác chạy chồng.
     * @return số vé đã đổi giá; vé đã bán được thêm vào [skipped].
     */
    private suspend fun changePrices(tickets: List<TicketDocument>, price: Long, skipped: MutableList<CalendarSkip>): Int {
        var updated = 0
        tickets.filter { it.basePrice != price }.chunked(PRICE_CHUNK).forEach { chunk ->
            val ids = chunk.map { it.id }
            updated += ticketDocDB.updateCalendarPricesIfUnsold(ids, price).toInt()
            ticketDocDB.loadCalendarTicketsPricedOtherThan(ids, price).forEach {
                skipped += CalendarSkip(it.calendarDate.orEmpty(), it.sid, CalendarSkipReason.HAS_SALES)
            }
        }
        return updated
    }

    /**
     * [action] cho từng phần tử, tối đa [CalendarPlanner.BULK_PARALLELISM] phần tử cùng lúc. Mỗi phần tử vẫn chạy tuần tự
     * các bước của nó (Redis trước Mongo sau) và các phần tử độc lập nhau (khác key, khác doc), nên song song không đổi bất
     * biến. Lỗi một phần tử không hủy các phần tử đang chạy — hủy giữa bước Redis và bước Mongo để lại bộ đếm lệch — nhưng
     * phần tử chưa bắt đầu thì bỏ: lỗi thường là Mongo/Redis sập, chạy tiếp hàng nghìn phần tử mỗi cái chờ timeout driver làm
     * request kéo dài hàng giờ. Đợi phần tử đang chạy xong rồi ném lỗi đầu tiên (thao tác đặt giá trị tuyệt đối nên admin
     * chạy lại là đủ).
     * @param idOf id phần tử cho dòng log tổng hợp khi có lỗi.
     * @return kết quả theo đúng thứ tự [items].
     */
    private suspend fun <T, R> mapBounded(
        operation: String, items: List<T>, idOf: (T) -> String, action: suspend (T) -> R,
    ): List<R> = coroutineScope {
        val permits = Semaphore(CalendarPlanner.BULK_PARALLELISM)
        val failed = AtomicBoolean(false)
        // null = bỏ qua vì đã có phần tử lỗi trước khi tới lượt.
        val outcomes: List<Result<R>?> = items.map { item ->
            async {
                permits.withPermit {
                    // Kiểm sau khi lấy permit: phần tử chờ permit trong lúc phần tử khác lỗi cũng bị bỏ.
                    if (failed.get()) return@withPermit null
                    try {
                        Result.success(action(item))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed.set(true)
                        Result.failure(e)
                    }
                }
            }
        }.awaitAll()
        val failures = items.zip(outcomes).mapNotNull { (item, outcome) -> outcome?.exceptionOrNull()?.let { idOf(item) to it } }
        if (failures.isNotEmpty()) {
            val skipped = outcomes.count { it == null }
            logger.error("calendar {}: {} of {} items failed, {} not started after the first failure, sample ids {}",
                operation, failures.size, items.size, skipped, failures.take(CalendarPlanner.BULK_MAX_SUPPRESSED).map { it.first })
            val first = failures.first().second
            failures.drop(1).take(CalendarPlanner.BULK_MAX_SUPPRESSED).forEach { first.addSuppressed(it.second) }
            throw first
        }
        // Không có lỗi thì không phần tử nào bị bỏ, mọi kết quả đều có.
        outcomes.map { checkNotNull(it).getOrThrow() }
    }

    /**
     * Xóa vé lịch khi chưa bán và bộ đếm Redis không lệch Mongo.
     * @return lý do không xóa, null khi đã xóa.
     */
    private suspend fun deleteTicketIfUntouched(ticket: TicketDocument): CalendarSkipReason? {
        // Bộ đếm Redis lệch Mongo nghĩa là có khách vừa trừ chỗ mà chưa ghi Mongo: xóa lúc này để lại đơn mồ côi.
        val live = redisClient.getTicketRemCap(ticket.id)
        if (live != null && live != ticket.remCap) return CalendarSkipReason.HELD
        if (!ticketDocDB.deleteCalendarTicketIfUnsold(ticket.id)) return CalendarSkipReason.HAS_SALES
        redisClient.removeTicketRemCap(ticket.id)
        return null
    }

    /** Xóa ngày (kèm sức chứa chung chưa bán) khi không còn vé nào của ngày. @return true khi đã xóa. */
    private suspend fun deleteDayIfEmpty(sessionId: String, day: TicketCalendarDayDocument): Boolean {
        if (ticketDocDB.countCalendarTicketsOfDate(sessionId, day.date) > 0) return false
        val capacityId = day.capacityId
        if (capacityId != null) {
            if (!ticketCapacityDB.deleteCapacityIfUnsold(capacityId)) return false
            redisClient.removeTicketRemCap(capacityId)
        }
        return calendarDayDB.deleteDay(day.id)
    }

    /** Tăng quota luôn được; giảm chỉ khi chưa có vé bán/giữ. Thứ tự Redis/Mongo: xem [changeCounter]. */
    private suspend fun changeTicketQuota(ticket: TicketDocument, quota: Int): ChangeResult {
        val delta = quota - (ticket.totCap ?: 0)
        if (delta == 0) return ChangeResult.Unchanged
        if (delta < 0 && (ticket.resCap ?: 0) > 0) return ChangeResult.Skipped(CalendarSkipReason.HAS_SALES)
        return changeCounter(
            key = ticket.id,
            delta = delta,
            loadRemCap = { ticketDocDB.loadTicketDocById(ticket.id)?.remCap },
            changeInDb = { ticketDocDB.changeCalendarQuota(ticket.id, delta) },
        )
    }

    /**
     * Đổi tổng chỗ của một bộ đếm (vé hoặc sức chứa ngày): Redis trước, Mongo sau, cả khi tăng lẫn giảm. Bộ đếm vé lịch
     * được khởi tạo lười (giữ chỗ đọc remCap Mongo rồi SETNX), nên nếu Mongo đổi trước hoặc Redis bị bỏ qua vì chưa có
     * key thì một lượt giữ chỗ đang đọc dở vẫn nạp số cũ và Redis bán vượt quota. Vì vậy key chưa có thì tự nạp từ Mongo
     * trước (cùng SETNX) rồi mới áp delta — mọi SETNX đến sau thành no-op. Mongo từ chối thì hoàn Redis; lỗi không rõ
     * Mongo đã ghi chưa thì chỉ hoàn khi tăng: tệ nhất là bán thiếu, không bán vượt.
     */
    private suspend fun changeCounter(
        key: String,
        delta: Int,
        loadRemCap: suspend () -> Int?,
        changeInDb: suspend () -> Boolean,
    ): ChangeResult {
        val onRedis = addOnRedisInitialisingFromDb(key, delta, loadRemCap)
        if (onRedis == UpdateRedisFailedConstants.FAILED) return ChangeResult.Skipped(CalendarSkipReason.HELD)
        val inDb = try {
            changeInDb()
        } catch (e: Exception) {
            if (delta > 0 && onRedis >= 0) withContext(NonCancellable) { revertOnRedis(key, delta) }
            throw e
        }
        if (!inDb) {
            if (onRedis >= 0) revertOnRedis(key, delta)
            return ChangeResult.Skipped(if (delta > 0) CalendarSkipReason.NOT_FOUND else CalendarSkipReason.HAS_SALES)
        }
        return ChangeResult.Changed
    }

    /** @return như [RedisAsyncClient.addTicketRemCap]; NOT_EXIST chỉ khi doc đã mất hoặc key vừa bị xóa giữa chừng. */
    private suspend fun addOnRedisInitialisingFromDb(key: String, delta: Int, loadRemCap: suspend () -> Int?): Int {
        val rem = redisClient.addTicketRemCap(key, delta)
        if (rem != UpdateRedisFailedConstants.NOT_EXIST) return rem
        val remCap = loadRemCap() ?: return rem
        redisClient.setNxTicketRemCap(key, remCap)
        return redisClient.addTicketRemCap(key, delta)
    }

    private suspend fun revertOnRedis(key: String, delta: Int) {
        val reverted = redisClient.addTicketRemCap(key, -delta)
        if (reverted < 0 && reverted != UpdateRedisFailedConstants.NOT_EXIST) {
            logger.error("calendar counter {}: could not revert {} on redis after mongo refused, result {}", key, delta, reverted)
        }
    }

    /** Cùng quy tắc [changeTicketQuota] cho sức chứa chung; ngày chưa có sức chứa chỉ được gắn khi chưa bán vé nào. */
    private suspend fun changeDayCapacity(day: TicketCalendarDayDocument, capacity: Int, tickets: List<TicketDocument>): ChangeResult {
        val capacityId = day.capacityId
        if (capacityId == null) {
            if (tickets.any { (it.resCap ?: 0) > 0 }) return ChangeResult.Skipped(CalendarSkipReason.HAS_SALES)
            val doc = TicketCapacityDocument(
                showId = day.showId,
                sessionId = day.sessionId,
                description = "Sức chứa ngày ${day.date}",
                totCap = capacity,
                remCap = capacity,
                resCap = 0,
                calendarDate = day.date,
            )
            ticketCapacityDB.insertTicketCapacity(doc)
            if (!calendarDayDB.attachCapacity(day.id, doc.id)) {
                ticketCapacityDB.deleteCapacityIfUnsold(doc.id)
                return ChangeResult.Skipped(CalendarSkipReason.NOT_FOUND)
            }
            return ChangeResult.Changed
        }

        val current = ticketCapacityDB.loadTicketCapacityById(capacityId)
            ?: return ChangeResult.Skipped(CalendarSkipReason.NOT_FOUND)
        val delta = capacity - current.totCap
        if (delta == 0) return ChangeResult.Unchanged
        if (delta < 0 && current.resCap > 0) return ChangeResult.Skipped(CalendarSkipReason.HAS_SALES)
        return changeCounter(
            key = capacityId,
            delta = delta,
            loadRemCap = { ticketCapacityDB.loadTicketCapacityById(capacityId)?.remCap },
            changeInDb = { ticketCapacityDB.changeCapacity(capacityId, delta) },
        )
    }

    private suspend fun loadCalendarSession(showId: String, sessionId: String): ShowMessage.GetSessionResponse {
        if (!ObjectId.isValid(showId) || !ObjectId.isValid(sessionId)) throw CalendarException.badRequest("Thiếu thông tin suất")
        val session = showSessionCache.get(showId, sessionId) ?: throw CalendarException.notFound("Không tải được suất diễn")
        if (!session.isCalendar() || !session.hasCalendar()) throw CalendarException.badRequest("Suất này không phải vé theo lịch")
        return session
    }

    private suspend fun loadOpenCalendarSession(showId: String, sessionId: String): ShowMessage.GetSessionResponse {
        val session = loadCalendarSession(showId, sessionId)
        if (session.status != ShowSessionStatus.Open.name) throw CalendarException.forbidden("Suất diễn chưa được mở bán")
        return session
    }

    private fun sessionDateRange(session: ShowMessage.GetSessionResponse): Pair<LocalDate, LocalDate> =
        CalendarRules.today(Instant.ofEpochMilli(session.startTime)) to CalendarRules.today(Instant.ofEpochMilli(session.endTime))

    private fun selectDates(
        session: ShowMessage.GetSessionResponse, fromDate: String, toDate: String, daysOfWeek: List<Int>?, dates: List<String>?,
    ): Set<String> {
        // Cùng lý do [hasNullValue]: phần tử null của list lọt qua Jackson.
        if (hasNullElement(daysOfWeek) || hasNullElement(dates)) throw CalendarException.badRequest(NULL_VALUE_MESSAGE)
        val from = parseDate(fromDate)
        val to = parseDate(toDate)
        val (firstDate, lastDate) = sessionDateRange(session)
        CalendarPlanner.validateRange(from, to, firstDate, lastDate)?.let { throw CalendarException.badRequest(it) }
        daysOfWeek?.let { CalendarPlanner.validateDaysOfWeek(it)?.let { error -> throw CalendarException.badRequest(error) } }
        val picked = dates?.map { parseDate(it) }?.toSet()
        return CalendarPlanner.expandDates(from, to, daysOfWeek?.toSet() ?: CalendarPlanner.ALL_DAYS_OF_WEEK)
            .filter { picked == null || it in picked }
            .map { it.toString() }
            .toSet()
            .ifEmpty { throw CalendarException.badRequest("Không có ngày nào khớp lựa chọn") }
    }

    private suspend fun loadCapacities(days: List<TicketCalendarDayDocument>): Map<String, TicketCapacityDocument> {
        val ids = days.mapNotNull { it.capacityId }
        if (ids.isEmpty()) return emptyMap()
        return ticketCapacityDB.loadTicketCapacityByIds(ids).associateBy { it.id }
    }

    private fun parseDate(value: String): LocalDate =
        CalendarRules.parseDate(value) ?: throw CalendarException.badRequest("Ngày không hợp lệ: ${CalendarPlanner.echoInput(value)}")

    private fun monthRange(month: String): Pair<String, String> {
        val yearMonth = runCatching { YearMonth.parse(month) }.getOrNull()
            ?: throw CalendarException.badRequest("Tháng không hợp lệ: ${CalendarPlanner.echoInput(month)}")
        return yearMonth.atDay(1).toString() to yearMonth.atEndOfMonth().toString()
    }

    private fun isPast(date: String?, today: LocalDate): Boolean =
        date?.let { CalendarRules.parseDate(it) }?.isBefore(today) ?: false

    private fun <T> ByDayOfWeek<T>.allValues(): List<T> = listOf(default) + byDayOfWeek.values

    private fun TicketDocument.toAdminTicket() = CalendarAdminTicket(
        ticketId = id,
        category = sid,
        priceCat = priceCat,
        dname = dname,
        price = basePrice,
        quota = totCap ?: 0,
        remaining = remCap ?: 0,
        reserved = resCap ?: 0,
        locked = (resCap ?: 0) > 0,
    )
}
