package metaticket.ticket.calendar

import io.mockk.*
import kotlinx.coroutines.test.runTest
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.CalendarTestFixtures.SESSION_ID
import metaticket.ticket.calendar.CalendarTestFixtures.SHOW_ID
import metaticket.ticket.calendar.CalendarTestFixtures.calendarSession
import metaticket.ticket.calendar.CalendarTestFixtures.calendarTicket
import metaticket.ticket.calendar.CalendarTestFixtures.nextDayOfWeek
import metaticket.ticket.calendar.CalendarTestFixtures.today
import metaticket.ticket.database.service.CategoryMappingDBService
import metaticket.ticket.database.service.TicketCalendarDayDBService
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.pojo.db.CategoryItemDocument
import metaticket.ticket.pojo.db.CategoryMappingDocument
import metaticket.ticket.pojo.db.TicketCalendarDayDocument
import metaticket.ticket.pojo.db.TicketCapacityDocument
import metaticket.ticket.pojo.db.TicketDocument
import metaticket.ticket.pojo.request.BulkUpdateCalendarRequest
import metaticket.ticket.pojo.request.CalendarTicketTypeDTO
import metaticket.ticket.pojo.request.GenerateCalendarRequest
import metaticket.ticket.pojo.response.CalendarSkip
import metaticket.ticket.pojo.response.CalendarSkipReason
import metaticket.ticket.redis.RedisAsyncClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Sinh/sửa lịch bán. Bất biến chính: ô (ngày, loại vé) đã có vé bán/giữ không đổi giá, không giảm quota — chỉ tăng quota
 * hoặc ngừng bán ngày.
 */
class CalendarTicketServiceTest {

    private val wednesday = 3
    private val saturday = 6
    private val sunday = 7

    private lateinit var sessionCache: ShowSessionCache
    private lateinit var ticketDocDB: TicketDocDBService
    private lateinit var capacityDB: TicketCapacityDBService
    private lateinit var dayDB: TicketCalendarDayDBService
    private lateinit var categoryDB: CategoryMappingDBService
    private lateinit var redis: RedisAsyncClient
    private lateinit var service: CalendarTicketService

    @BeforeEach
    fun setup() {
        sessionCache = mockk()
        ticketDocDB = mockk()
        capacityDB = mockk()
        dayDB = mockk()
        categoryDB = mockk()
        redis = mockk()
        service = CalendarTicketService(sessionCache, ticketDocDB, capacityDB, dayDB, categoryDB, redis)
        coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession()
    }

    private fun category(code: String, price: Long) = code to CategoryItemDocument(code, "Vé $code", price, null, null, null)

    /** Bài toán mẫu: bán T4, T7, CN; 3 hạng vé, mỗi hạng mỗi thứ một giá; quota hạng thường cuối tuần cao hơn. */
    private fun weeklyRequest(from: java.time.LocalDate, to: java.time.LocalDate) = GenerateCalendarRequest(
        showId = SHOW_ID,
        sessionId = SESSION_ID,
        fromDate = from.toString(),
        toDate = to.toString(),
        daysOfWeek = listOf(wednesday, saturday, sunday),
        types = listOf(
            CalendarTicketTypeDTO("VIP",
                price = ByDayOfWeek(500_000L, mapOf(wednesday to 450_000L, saturday to 550_000L, sunday to 600_000L)),
                quota = ByDayOfWeek(50)),
            CalendarTicketTypeDTO("STD",
                price = ByDayOfWeek(300_000L, mapOf(wednesday to 250_000L, saturday to 320_000L, sunday to 350_000L)),
                quota = ByDayOfWeek(300, mapOf(saturday to 500, sunday to 500))),
            CalendarTicketTypeDTO("KID",
                price = ByDayOfWeek(150_000L, mapOf(wednesday to 120_000L, saturday to 150_000L, sunday to 180_000L)),
                quota = ByDayOfWeek(100)),
        ),
    )

    private fun stubGenerateDependencies(existing: List<TicketDocument> = emptyList()): CapturingSlot<List<TicketDocument>> {
        coEvery { categoryDB.findByShowAndSession(SHOW_ID, SESSION_ID) } returns CategoryMappingDocument(
            showId = SHOW_ID, sessionId = SESSION_ID, kindMap = "PolicyConfig",
            catMapping = mapOf(category("VIP", 500_000), category("STD", 300_000), category("KID", 150_000)),
        )
        coEvery { ticketDocDB.loadCalendarTickets(SHOW_ID, SESSION_ID, any(), any(), null) } returns existing
        coEvery { dayDB.loadDays(SESSION_ID, any(), any()) } returns emptyList()
        coEvery { dayDB.insertIfAbsent(any()) } returns true
        val inserted = slot<List<TicketDocument>>()
        coEvery { ticketDocDB.insertCalendarTickets(capture(inserted)) } answers { inserted.captured.size }
        return inserted
    }

    @Test
    fun `generate creates one ticket per date and type with the weekday price and quota`() = runTest {
        val from = nextDayOfWeek(today.plusDays(7), 1) // một thứ Hai
        val to = from.plusDays(13)                      // hai tuần: 2 x (T4, T7, CN) = 6 ngày
        val inserted = stubGenerateDependencies()

        val response = service.generate(weeklyRequest(from, to), "op")

        assertEquals(6, response.createdDays)
        assertEquals(18, response.createdTickets)
        val byKey = inserted.captured.associateBy { java.time.LocalDate.parse(it.calendarDate).dayOfWeek.value to it.sid }
        assertEquals(450_000L, byKey[wednesday to "VIP"]?.basePrice)
        assertEquals(550_000L, byKey[saturday to "VIP"]?.basePrice)
        assertEquals(600_000L, byKey[sunday to "VIP"]?.basePrice)
        assertEquals(250_000L, byKey[wednesday to "STD"]?.basePrice)
        assertEquals(180_000L, byKey[sunday to "KID"]?.basePrice)
        assertEquals(300, byKey[wednesday to "STD"]?.totCap)
        assertEquals(500, byKey[saturday to "STD"]?.totCap)
        assertEquals(500, byKey[sunday to "STD"]?.remCap)
        assertEquals("Vé VIP", byKey[sunday to "VIP"]?.priceCat)
        assertTrue(inserted.captured.all { it.resCap == 0 && it.sessionId == SESSION_ID })
    }

    @Test
    fun `generate skips tickets that already exist`() = runTest {
        val from = nextDayOfWeek(today.plusDays(7), 1)
        val firstWednesday = nextDayOfWeek(from.minusDays(1), wednesday)
        val inserted = stubGenerateDependencies(existing = listOf(calendarTicket("x", firstWednesday, sid = "VIP")))

        val response = service.generate(weeklyRequest(from, from.plusDays(6)), "op")

        assertEquals(8, response.createdTickets)
        assertEquals(listOf(CalendarSkipReason.EXISTS), response.skipped.map { it.reason })
        assertTrue(inserted.captured.none { it.calendarDate == firstWednesday.toString() && it.sid == "VIP" })
    }

    @Test
    fun `generate rejects a weekday price for a day that is not sold`() = runTest {
        val from = nextDayOfWeek(today.plusDays(7), 1)
        val request = weeklyRequest(from, from.plusDays(6)).let {
            it.copy(types = it.types.map { t -> t.copy(price = ByDayOfWeek(1L, mapOf(1 to 2L))) })
        }

        val error = assertFailsWith<CalendarException> { service.generate(request, "op") }
        assertEquals(400, error.status)
    }

    @Test
    fun `generate rejects a session that is not a calendar session`() = runTest {
        coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession().toBuilder().clearSessionType().build()
        val from = nextDayOfWeek(today.plusDays(7), 1)

        assertFailsWith<CalendarException> { service.generate(weeklyRequest(from, from.plusDays(6)), "op") }
    }

    private fun bulk(date: java.time.LocalDate, price: Long? = null, quota: Int? = null) = BulkUpdateCalendarRequest(
        showId = SHOW_ID, sessionId = SESSION_ID, fromDate = date.toString(), toDate = date.toString(),
        price = price, quota = quota,
    )

    private fun stubBulk(date: java.time.LocalDate, ticket: TicketDocument) {
        coEvery { dayDB.loadDays(SESSION_ID, date.toString(), date.toString()) } returns emptyList()
        coEvery { ticketDocDB.loadCalendarTickets(SHOW_ID, SESSION_ID, date.toString(), date.toString(), null) } returns listOf(ticket)
    }

    @Test
    fun `quota cannot go down once a date has sales`() = runTest {
        val date = today.plusDays(10)
        stubBulk(date, calendarTicket("t1", date, quota = 100, reserved = 3))

        val response = service.bulkUpdate(bulk(date, quota = 80), "op")

        assertEquals(0, response.updated)
        assertEquals(CalendarSkipReason.HAS_SALES, response.skipped.single().reason)
        coVerify(exactly = 0) { ticketDocDB.changeCalendarQuota(any(), any()) }
        coVerify(exactly = 0) { redis.addTicketRemCap(any(), any()) }
    }

    @Test
    fun `quota can go up after sales, Redis first then Mongo`() = runTest {
        val date = today.plusDays(10)
        stubBulk(date, calendarTicket("t1", date, quota = 100, reserved = 3))
        coEvery { ticketDocDB.changeCalendarQuota("t1", 20) } returns true
        coEvery { redis.addTicketRemCap("t1", 20) } returns 117

        val response = service.bulkUpdate(bulk(date, quota = 120), "op")

        assertEquals(1, response.updated)
        assertTrue(response.skipped.isEmpty())
        coVerifyOrder {
            redis.addTicketRemCap("t1", 20)
            ticketDocDB.changeCalendarQuota("t1", 20)
        }
    }

    @Test
    fun `quota increase gives Redis back when the Mongo write fails, so it can only undersell`() = runTest {
        val date = today.plusDays(10)
        stubBulk(date, calendarTicket("t1", date, quota = 100))
        coEvery { redis.addTicketRemCap("t1", 20) } returns 120
        coEvery { ticketDocDB.changeCalendarQuota("t1", 20) } throws IllegalStateException("mongo down")
        coEvery { redis.addTicketRemCap("t1", -20) } returns 100

        assertFailsWith<IllegalStateException> { service.bulkUpdate(bulk(date, quota = 120), "op") }
        coVerify { redis.addTicketRemCap("t1", -20) }
    }

    @Test
    fun `quota decrease without sales takes Redis first and gives it back when Mongo refuses`() = runTest {
        val date = today.plusDays(10)
        stubBulk(date, calendarTicket("t1", date, quota = 100))
        coEvery { redis.addTicketRemCap("t1", -20) } returns 80
        coEvery { ticketDocDB.changeCalendarQuota("t1", -20) } returns false // một khách vừa giữ chỗ
        coEvery { redis.addTicketRemCap("t1", 20) } returns 100

        val response = service.bulkUpdate(bulk(date, quota = 80), "op")

        assertEquals(CalendarSkipReason.HAS_SALES, response.skipped.single().reason)
        coVerify { redis.addTicketRemCap("t1", 20) }
    }

    @Test
    fun `price change on a sold date is skipped`() = runTest {
        val date = today.plusDays(10)
        stubBulk(date, calendarTicket("t1", date, price = 300_000, reserved = 1))
        coEvery { ticketDocDB.updateCalendarPricesIfUnsold(listOf("t1"), 350_000) } returns 0
        coEvery { ticketDocDB.loadCalendarTicketsPricedOtherThan(listOf("t1"), 350_000) } returns
            listOf(calendarTicket("t1", date, price = 300_000, reserved = 1))

        val response = service.bulkUpdate(bulk(date, price = 350_000), "op")

        assertEquals(0, response.updated)
        assertEquals(CalendarSkipReason.HAS_SALES, response.skipped.single().reason)
    }

    @Test
    fun `daily capacity is not attached when a category filtered out of the request already sold on that date`() = runTest {
        val date = today.plusDays(10)
        val day = TicketCalendarDayDocument(showId = SHOW_ID, sessionId = SESSION_ID, date = date.toString())
        coEvery { dayDB.loadDays(SESSION_ID, date.toString(), date.toString()) } returns listOf(day)
        coEvery { ticketDocDB.loadCalendarTickets(SHOW_ID, SESSION_ID, date.toString(), date.toString(), listOf("A")) } returns
            listOf(calendarTicket("tA", date, sid = "A"))
        coEvery { ticketDocDB.loadCalendarTickets(SHOW_ID, SESSION_ID, date.toString(), date.toString(), null) } returns
            listOf(calendarTicket("tA", date, sid = "A"), calendarTicket("tB", date, sid = "B", reserved = 30))
        coEvery { capacityDB.insertTicketCapacity(any()) } returns "cap"
        coEvery { dayDB.attachCapacity(any(), any()) } returns true

        val response = service.bulkUpdate(BulkUpdateCalendarRequest(
            showId = SHOW_ID, sessionId = SESSION_ID, fromDate = date.toString(), toDate = date.toString(),
            categories = listOf("A"), dailyCapacity = 50,
        ), "op")

        assertEquals(0, response.updated)
        assertEquals(CalendarSkipReason.HAS_SALES, response.skipped.single().reason)
        coVerify(exactly = 0) { capacityDB.insertTicketCapacity(any()) }
    }

    @Test
    fun `generate attaches the daily capacity to an existing date that has none`() = runTest {
        val wednesdayDate = nextDayOfWeek(today.plusDays(7), wednesday)
        stubGenerateDependencies()
        val day = TicketCalendarDayDocument(showId = SHOW_ID, sessionId = SESSION_ID, date = wednesdayDate.toString())
        coEvery { dayDB.loadDays(SESSION_ID, any(), any()) } returns listOf(day)
        val capacity = slot<TicketCapacityDocument>()
        coEvery { capacityDB.insertTicketCapacity(capture(capacity)) } answers { capacity.captured.id }
        coEvery { dayDB.attachCapacity(day.id, any()) } returns true

        val response = service.generate(
            weeklyRequest(wednesdayDate, wednesdayDate).copy(dailyCapacity = ByDayOfWeek(200)), "op",
        )

        assertEquals(0, response.createdDays)
        assertEquals(3, response.createdTickets)
        assertTrue(response.skipped.isEmpty())
        assertEquals(200, capacity.captured.totCap)
        assertEquals(wednesdayDate.toString(), capacity.captured.calendarDate)
        coVerify { dayDB.attachCapacity(day.id, capacity.captured.id) }
    }

    @Test
    fun `generate reports the daily capacity of an existing sold date instead of dropping it silently`() = runTest {
        val wednesdayDate = nextDayOfWeek(today.plusDays(7), wednesday)
        stubGenerateDependencies(existing = listOf(calendarTicket("x", wednesdayDate, sid = "VIP", reserved = 1)))
        coEvery { dayDB.loadDays(SESSION_ID, any(), any()) } returns
            listOf(TicketCalendarDayDocument(showId = SHOW_ID, sessionId = SESSION_ID, date = wednesdayDate.toString()))

        val response = service.generate(
            weeklyRequest(wednesdayDate, wednesdayDate).copy(dailyCapacity = ByDayOfWeek(200)), "op",
        )

        assertTrue(CalendarSkip(wednesdayDate.toString(), null, CalendarSkipReason.HAS_SALES) in response.skipped)
        coVerify(exactly = 0) { capacityDB.insertTicketCapacity(any()) }
    }

    @Test
    fun `past dates are reported, not changed`() = runTest {
        val date = today.minusDays(1)
        stubBulk(date, calendarTicket("t1", date))

        val response = service.bulkUpdate(bulk(date, price = 1), "op")

        assertEquals(CalendarSkipReason.PAST_DATE, response.skipped.single().reason)
        coVerify(exactly = 0) { ticketDocDB.updateCalendarPricesIfUnsold(any(), any()) }
    }
}
