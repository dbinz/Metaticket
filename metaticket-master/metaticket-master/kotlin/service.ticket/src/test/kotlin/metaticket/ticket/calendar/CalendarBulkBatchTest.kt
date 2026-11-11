package metaticket.ticket.calendar

import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.CalendarTestFixtures.SESSION_ID
import metaticket.ticket.calendar.CalendarTestFixtures.SHOW_ID
import metaticket.ticket.calendar.CalendarTestFixtures.calendarSession
import metaticket.ticket.calendar.CalendarTestFixtures.calendarTicket
import metaticket.ticket.calendar.CalendarTestFixtures.today
import metaticket.ticket.database.service.TicketCalendarDayDBService
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.pojo.db.TicketCalendarDayDocument
import metaticket.ticket.pojo.request.BulkUpdateCalendarRequest
import metaticket.ticket.pojo.request.DeleteCalendarRequest
import metaticket.ticket.pojo.response.CalendarSkip
import metaticket.ticket.pojo.response.CalendarSkipReason
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.redis.UpdateRedisFailedConstants
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFailsWith

/**
 * Sửa/xóa hàng loạt trên khoảng lớn (366 ngày × hàng chục hạng) phải xong trước timeout nginx mà giữ nguyên bất biến:
 * đổi giá chỉ ô chưa bán (CAS trong filter), quota Redis trước Mongo sau, xóa chỉ ô chưa bán và Redis không lệch.
 * Mongo/Redis giả theo đúng filter/script thật; mỗi lệnh DB giả tốn 10ms thời gian ảo để đo được độ song song mà không
 * phụ thuộc đồng hồ thật.
 */
class CalendarBulkBatchTest {

    private class Cell(val id: String, val date: LocalDate, val sid: String, var price: Long, var tot: Int, var res: Int) {
        val rem get() = tot - res
        fun toDoc() = calendarTicket(id, date, sid = sid, price = price, quota = tot, reserved = res)
    }

    private val sessionCache = mockk<ShowSessionCache>()
    private val ticketDocDB = mockk<TicketDocDBService>()
    private val capacityDB = mockk<TicketCapacityDBService>()
    private val dayDB = mockk<TicketCalendarDayDBService>()
    private val redis = mockk<RedisAsyncClient>()
    private val service = CalendarTicketService(sessionCache, ticketDocDB, capacityDB, dayDB, mockk(), redis)

    private val from = today.plusDays(5)
    private val cells = LinkedHashMap<String, Cell>()
    private val redisState = HashMap<String, Int>()
    private val events = mutableListOf<String>()
    private var inFlight = 0
    private var maxInFlight = 0

    private suspend fun <R> dbCall(block: () -> R): R {
        inFlight++
        maxInFlight = maxOf(maxInFlight, inFlight)
        delay(10)
        return block().also { inFlight-- }
    }

    /** [dayCount] ngày × [sidCount] hạng; [setup] chỉnh từng ô theo số thứ tự. */
    private fun arrange(dayCount: Int, sidCount: Int, setup: (Int, Cell) -> Unit = { _, _ -> }): LocalDate {
        val to = from.plusDays(dayCount - 1L)
        var index = 0
        for (d in 0 until dayCount) for (s in 0 until sidCount) {
            val cell = Cell("t$d-$s", from.plusDays(d.toLong()), "S$s", price = 100_000, tot = 100, res = 0)
            setup(index++, cell)
            cells[cell.id] = cell
        }
        coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession()
        coEvery { ticketDocDB.loadCalendarTickets(SHOW_ID, SESSION_ID, from.toString(), to.toString(), null) } answers {
            cells.values.map { it.toDoc() }
        }
        coEvery { dayDB.loadDays(SESSION_ID, from.toString(), to.toString()) } returns (0 until dayCount).map { d ->
            val date = from.plusDays(d.toLong())
            TicketCalendarDayDocument(
                id = "day-$date", showId = SHOW_ID, sessionId = SESSION_ID, date = date.toString(),
                capacityId = if (d % 2 == 0) "cap-$date" else null,
            )
        }
        return to
    }

    private fun bulk(to: LocalDate, price: Long? = null, quota: Int? = null) = BulkUpdateCalendarRequest(
        showId = SHOW_ID, sessionId = SESSION_ID, fromDate = from.toString(), toDate = to.toString(), price = price, quota = quota,
    )

    private fun skipOf(cell: Cell, reason: CalendarSkipReason) = CalendarSkip(cell.date.toString(), cell.sid, reason)

    /** Mongo giả của đổi giá: cùng filter với updateCalendarPricesIfUnsold (chỉ ô chưa bán) và lệnh đọc lại ô còn giá khác. */
    private fun stubPriceStore(newPrice: Long) {
        coEvery { ticketDocDB.updateCalendarPricesIfUnsold(any(), newPrice) } coAnswers {
            val ids = firstArg<List<String>>()
            dbCall {
                ids.mapNotNull { cells[it] }.filter { it.res == 0 && it.price != newPrice }.onEach { it.price = newPrice }.size.toLong()
            }
        }
        coEvery { ticketDocDB.loadCalendarTicketsPricedOtherThan(any(), newPrice) } coAnswers {
            val ids = firstArg<List<String>>()
            dbCall { ids.mapNotNull { cells[it] }.filter { it.price != newPrice }.map { it.toDoc() } }
        }
    }

    private fun stubQuotaStore(failWhen: (String) -> Boolean = { false }) {
        coEvery { redis.addTicketRemCap(any(), any()) } answers {
            val key = firstArg<String>()
            events += "redis:$key"
            val current = redisState[key] ?: return@answers UpdateRedisFailedConstants.NOT_EXIST
            val next = current + secondArg<Int>()
            if (next < 0) return@answers UpdateRedisFailedConstants.FAILED
            redisState[key] = next
            next
        }
        coEvery { redis.setNxTicketRemCap(any(), any()) } answers { redisState.putIfAbsent(firstArg(), secondArg()) == null }
        coEvery { ticketDocDB.loadTicketDocById(any()) } answers { cells[firstArg()]?.toDoc() }
        coEvery { ticketDocDB.changeCalendarQuota(any(), any()) } coAnswers {
            val id = firstArg<String>()
            val delta = secondArg<Int>()
            dbCall {
                events += "mongo:$id"
                if (failWhen(id)) throw IllegalStateException("mongo down at $id")
                val cell = cells.getValue(id)
                // Cùng filter với changeCalendarQuota: giảm chỉ khi chưa bán và còn đủ chỗ.
                if (delta < 0 && (cell.res != 0 || cell.rem < -delta)) return@dbCall false
                cell.tot += delta
                true
            }
        }
    }

    @Test
    fun `bulk price change on a large range is a few conditional batches and still skips sold tickets`() = runTest {
        val newPrice = 120_000L
        val to = arrange(dayCount = 60, sidCount = 50) { i, cell ->
            if (i % 7 == 0) cell.res = 1
            if (i % 5 == 0) cell.price = newPrice
        }
        stubPriceStore(newPrice)
        val unsoldToChange = cells.values.filterIndexed { i, _ -> i % 5 != 0 && i % 7 != 0 }
        val soldToChange = cells.values.filterIndexed { i, _ -> i % 5 != 0 && i % 7 == 0 }

        val response = service.bulkUpdate(bulk(to, price = newPrice), "op")

        assertEquals(unsoldToChange.size, response.updated)
        assertEquals(soldToChange.map { skipOf(it, CalendarSkipReason.HAS_SALES) }.toSet(), response.skipped.toSet())
        assertEquals(soldToChange.size, response.skipped.size)
        assertTrue(soldToChange.all { it.price == 100_000L }, "ô đã bán không được đổi giá")
        assertTrue(unsoldToChange.all { it.price == newPrice })
        // 2400 ô cần đổi giá → 3 lô 1000, mỗi lô một lệnh ghi + một lệnh đọc, thay vì 2400 lệnh ghi lẻ.
        coVerify(exactly = 3) { ticketDocDB.updateCalendarPricesIfUnsold(any(), newPrice) }
        coVerify(exactly = 3) { ticketDocDB.loadCalendarTicketsPricedOtherThan(any(), newPrice) }
    }

    @Test
    fun `bulk quota change runs tickets in bounded parallel, Redis before Mongo for each, sold tickets untouched`() = runTest {
        val to = arrange(dayCount = 20, sidCount = 20) { i, cell ->
            if (i % 7 == 0) cell.res = 3
            if (i % 3 == 0) redisState[cell.id] = cell.rem
        }
        stubQuotaStore()
        val sold = cells.values.filter { it.res > 0 }
        val unsold = cells.values.filter { it.res == 0 }

        val response = service.bulkUpdate(bulk(to, quota = 80), "op")

        assertEquals(CalendarPlanner.BULK_PARALLELISM, maxInFlight, "số lệnh Mongo chạy đồng thời")
        assertEquals(unsold.size, response.updated)
        assertEquals(sold.map { skipOf(it, CalendarSkipReason.HAS_SALES) }.toSet(), response.skipped.toSet())
        assertTrue(sold.all { it.tot == 100 && events.none { e -> e.endsWith(":${it.id}") } }, "ô đã bán không được chạm")
        for (cell in unsold) {
            assertEquals(80, cell.tot)
            assertEquals(cell.rem, redisState[cell.id], "Redis lệch Mongo ở ${cell.id}")
            assertTrue(events.indexOf("redis:${cell.id}") < events.indexOf("mongo:${cell.id}"), "Mongo ghi trước Redis ở ${cell.id}")
        }
    }

    @Test
    fun `bulk price change accepts zero and makes unsold tickets free`() = runTest {
        val to = arrange(dayCount = 3, sidCount = 2) { i, cell -> if (i == 0) cell.res = 1 }
        stubPriceStore(0)
        val sold = cells.values.first()
        val unsold = cells.values.drop(1)

        val response = service.bulkUpdate(bulk(to, price = 0), "op")

        assertEquals(unsold.size, response.updated)
        assertEquals(listOf(skipOf(sold, CalendarSkipReason.HAS_SALES)), response.skipped)
        assertTrue(unsold.all { it.price == 0L }, "ô chưa bán phải thành vé 0đ")
        assertEquals(100_000L, sold.price)
    }

    /**
     * Ô đã bắt đầu chạy trọn Redis → Mongo dù ô khác lỗi (hủy giữa hai bước để bộ đếm lệch); ô chưa bắt đầu khi đã có lỗi
     * thì bỏ, không ghi gì — request vẫn báo lỗi, admin chạy lại (giá trị tuyệt đối).
     */
    @Test
    fun `a failing ticket does not cancel tickets already running, and tickets not yet started are left untouched`() = runTest {
        val to = arrange(dayCount = 10, sidCount = 10) { _, cell -> redisState[cell.id] = cell.rem }
        val failing = "t0-5"
        stubQuotaStore(failWhen = { it == failing })

        assertFailsWith<IllegalStateException> { service.bulkUpdate(bulk(to, quota = 120), "op") }

        val (started, notStarted) = cells.values.partition { cell -> events.any { it.endsWith(":${cell.id}") } }
        assertTrue(started.any { it.id != failing }, "phải có ô chạy song song với ô lỗi")
        assertTrue(notStarted.isNotEmpty(), "ô chưa bắt đầu phải bị bỏ sau khi có lỗi")
        for (cell in cells.values) {
            // Ô lỗi: Mongo chưa ghi và Redis đã được hoàn; ô đã chạy: trọn vẹn; ô bị bỏ: nguyên như cũ.
            val expected = if (cell.id == failing || cell in notStarted) 100 else 120
            assertEquals(expected, cell.tot, "quota ở ${cell.id}")
            assertEquals(cell.rem, redisState[cell.id], "Redis lệch Mongo ở ${cell.id}")
        }
    }

    @Test
    fun `when Mongo is down the bulk stops starting new tickets and keeps the error report small`() = runTest {
        val to = arrange(dayCount = 100, sidCount = 20) { _, cell -> redisState[cell.id] = cell.rem }
        stubQuotaStore(failWhen = { true })

        val error = assertFailsWith<IllegalStateException> { service.bulkUpdate(bulk(to, quota = 120), "op") }

        // Mỗi ô thật sẽ chờ timeout driver (Mongo 30s, Lettuce 60s): chạy tiếp cả 2000 ô là request kéo dài hàng chục phút.
        val mongoCalls = events.count { it.startsWith("mongo:") }
        assertTrue(mongoCalls <= CalendarPlanner.BULK_PARALLELISM, "đã gọi Mongo $mongoCalls lần khi Mongo sập")
        assertTrue(error.suppressed.size <= CalendarPlanner.BULK_MAX_SUPPRESSED, "gắn ${error.suppressed.size} lỗi kèm")
        for (cell in cells.values) {
            assertEquals(100, cell.tot, "quota ở ${cell.id}")
            assertEquals(cell.rem, redisState[cell.id], "Redis lệch Mongo ở ${cell.id}")
        }
    }

    @Test
    fun `bulk delete runs tickets in bounded parallel and keeps held or sold tickets`() = runTest {
        val to = arrange(dayCount = 20, sidCount = 20) { i, cell ->
            if (i % 7 == 0) cell.res = 1
            when {
                i % 11 == 0 -> redisState[cell.id] = cell.rem - 1 // khách vừa trừ Redis, chưa ghi Mongo
                i % 2 == 0 -> redisState[cell.id] = cell.rem
            }
        }
        val held = cells.values.filterIndexed { i, _ -> i % 11 == 0 }.toSet()
        val sold = cells.values.filter { it.res > 0 && it !in held }.toSet()
        val removable = cells.values.filter { it !in held && it !in sold }
        val heldRedis = held.associate { it.id to redisState[it.id] }
        coEvery { redis.getTicketRemCap(any()) } answers { redisState[firstArg()] }
        coEvery { redis.removeTicketRemCap(*anyVararg()) } answers {
            // args gồm cả continuation của hàm suspend: chỉ lấy các key String của vararg.
            val keys = args.flatMap { if (it is Array<*>) it.toList() else listOf(it) }.filterIsInstance<String>()
            keys.count { redisState.remove(it) != null }.toLong()
        }
        coEvery { ticketDocDB.deleteCalendarTicketIfUnsold(any()) } coAnswers {
            val id = firstArg<String>()
            dbCall { if (cells.getValue(id).res == 0) cells.remove(id) != null else false }
        }
        coEvery { ticketDocDB.countCalendarTicketsOfDate(SESSION_ID, any()) } coAnswers {
            val date = secondArg<String>()
            dbCall { cells.values.count { it.date.toString() == date }.toLong() }
        }
        coEvery { capacityDB.deleteCapacityIfUnsold(any()) } returns true
        val deletedDays = mutableListOf<String>()
        coEvery { dayDB.deleteDay(any()) } answers { deletedDays += firstArg<String>(); true }
        val keptDates = (held + sold).map { it.date }.toSet()

        val response = service.delete(DeleteCalendarRequest(SHOW_ID, SESSION_ID, from.toString(), to.toString()), "op")

        assertEquals(CalendarPlanner.BULK_PARALLELISM, maxInFlight, "số lệnh Mongo chạy đồng thời")
        assertEquals(removable.size, response.deletedTickets)
        val expectedSkips = held.map { skipOf(it, CalendarSkipReason.HELD) } + sold.map { skipOf(it, CalendarSkipReason.HAS_SALES) }
        assertEquals(expectedSkips.toSet(), response.skipped.toSet())
        assertTrue((held + sold).all { cells.containsKey(it.id) }, "ô giữ/bán không được xóa")
        assertEquals(heldRedis, held.associate { it.id to redisState[it.id] }, "bộ đếm của ô đang giữ không được đụng")
        assertTrue(removable.none { cells.containsKey(it.id) || redisState.containsKey(it.id) })
        val emptyDays = (0 until 20).map { from.plusDays(it.toLong()) }.filter { it !in keptDates }
        assertEquals(emptyDays.size, response.deletedDays)
        assertEquals(emptyDays.map { "day-$it" }.toSet(), deletedDays.toSet())
    }
}
