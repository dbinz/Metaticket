package metaticket.ticket.calendar

import io.mockk.*
import kotlinx.coroutines.test.runTest
import metaticket.ticket.ReservationExecutionRetryable
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.CalendarTestFixtures.SESSION_ID
import metaticket.ticket.calendar.CalendarTestFixtures.SHOW_ID
import metaticket.ticket.calendar.CalendarTestFixtures.calendarSession
import metaticket.ticket.calendar.CalendarTestFixtures.calendarTicket
import metaticket.ticket.calendar.CalendarTestFixtures.today
import metaticket.ticket.database.service.TicketCalendarDayDBService
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.pojo.db.TicketCalendarDayDocument
import metaticket.ticket.pojo.db.TicketCapacityDocument
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import metaticket.ticket.pojo.db.TicketReservationDocument
import metaticket.ticket.pojo.request.BulkUpdateCalendarRequest
import metaticket.ticket.processor.ReservationProcessor
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.redis.UpdateRedisFailedConstants
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Hồi quy oversell khi admin đổi quota vé / sức chứa ngày lúc bộ đếm Redis của vé lịch chưa tồn tại (bộ đếm được nạp
 * lười khi giữ chỗ: đọc remCap Mongo rồi SETNX). Redis giả theo đúng script update.ticket.remaining (-999 thiếu key,
 * -1 khi âm), Mongo giả theo đúng filter của changeCalendarQuota/changeCapacity/addReservedCap; lượt giữ chỗ chạy qua
 * [ReservationProcessor] thật và được xen vào giữa các bước của admin. Bất biến: Redis không cho bán nhiều hơn remCap Mongo.
 */
class CalendarCapacityRaceTest {

    private val date = today.plusDays(10)
    private val ticketId = "66a000000000000000000001"
    private val capacityId = "66b000000000000000000001"
    private val resId = "66c000000000000000000001"

    /** Bộ đếm của một doc Mongo; mọi update thật giữ remCap = totCap - resCap. */
    private class MongoCounter(var tot: Int, var res: Int = 0) {
        val rem get() = tot - res

        /** Cùng điều kiện filter của changeCalendarQuota/changeCapacity: giảm chỉ khi chưa bán và còn đủ chỗ. */
        fun change(delta: Int): Boolean {
            if (delta < 0 && (res != 0 || rem < -delta)) return false
            tot += delta
            return true
        }
    }

    private enum class Interleave {
        /** Khách trừ Redis trước lượt ghi Mongo của admin, còn lượt ghi Mongo của khách tới sau. */
        BETWEEN_ADMIN_STEPS,
        /** Khách giữ chỗ trọn vẹn ngay sau lượt ghi Mongo của admin. */
        AFTER_ADMIN_DB_WRITE,
        /** Khách đọc remCap Mongo, admin sửa xong trọn vẹn, rồi khách mới SETNX bằng số đã đọc. */
        STRADDLING_LAZY_INIT,
    }

    private val redisState = ConcurrentHashMap<String, Int>()
    private lateinit var ticketMongo: MongoCounter
    private lateinit var capacityMongo: MongoCounter
    private var interleave: Interleave? = null
    private var deferCustomerWrites = false
    private val deferredCustomerWrites = mutableListOf<() -> Unit>()
    private lateinit var adminUpdate: suspend () -> Unit

    private val redis = mockk<RedisAsyncClient>()
    private val ticketDocDB = mockk<TicketDocDBService>()
    private val capacityDB = mockk<TicketCapacityDBService>()
    private val dayDB = mockk<TicketCalendarDayDBService>()
    private val sessionCache = mockk<ShowSessionCache>()
    private val retryable = mockk<ReservationExecutionRetryable>(relaxed = true)
    private val processor = ReservationProcessor(sessionCache, redis, ticketDocDB, capacityDB, retryable)
    private val service = CalendarTicketService(sessionCache, ticketDocDB, capacityDB, dayDB, mockk(), redis)

    /** Lần xen đầu tiên khớp [point] được dùng rồi bỏ, để lượt của admin lồng bên trong không kích hoạt lại. */
    private fun take(point: Interleave): Boolean = (interleave == point).also { if (it) interleave = null }

    private suspend fun reserveTwoSeats() = processor.process(resId)

    private suspend fun adminDbWrite(write: () -> Boolean): Boolean = when {
        take(Interleave.BETWEEN_ADMIN_STEPS) -> {
            deferCustomerWrites = true
            reserveTwoSeats()
            deferCustomerWrites = false
            write().also {
                deferredCustomerWrites.forEach { it() }
                deferredCustomerWrites.clear()
            }
        }
        take(Interleave.AFTER_ADMIN_DB_WRITE) -> write().also { reserveTwoSeats() }
        else -> write()
    }

    private fun customerDbWrite(write: () -> Unit): Boolean {
        if (deferCustomerWrites) deferredCustomerWrites += write else write()
        return true
    }

    private fun arrange(ticketQuota: Int, dayCapacity: Int?) {
        ticketMongo = MongoCounter(ticketQuota)
        capacityMongo = MongoCounter(dayCapacity ?: 0)

        coEvery { redis.addTicketRemCap(any(), any()) } answers {
            val key = firstArg<String>()
            val current = redisState[key] ?: return@answers UpdateRedisFailedConstants.NOT_EXIST
            val next = current + secondArg<Int>()
            if (next < 0) return@answers UpdateRedisFailedConstants.FAILED
            redisState[key] = next
            next
        }
        coEvery { redis.setNxTicketRemCap(any(), any()) } coAnswers {
            val key = firstArg<String>()
            val counterUnderTest = if (dayCapacity != null) capacityId else ticketId
            if (key == counterUnderTest && take(Interleave.STRADDLING_LAZY_INIT)) adminUpdate()
            redisState.putIfAbsent(key, secondArg()) == null
        }
        coEvery { redis.getTicketRemCap(any()) } answers { redisState[firstArg()] }

        coEvery { ticketDocDB.loadTicketDocById(ticketId) } answers {
            calendarTicket(ticketId, date, quota = ticketMongo.tot, reserved = ticketMongo.res)
        }
        coEvery { ticketDocDB.loadCalendarTickets(SHOW_ID, SESSION_ID, any(), any(), any()) } answers {
            listOf(calendarTicket(ticketId, date, quota = ticketMongo.tot, reserved = ticketMongo.res))
        }
        coEvery { ticketDocDB.changeCalendarQuota(ticketId, any()) } coAnswers {
            val delta = secondArg<Int>()
            adminDbWrite { ticketMongo.change(delta) }
        }
        coEvery { ticketDocDB.addReservedCap(any()) } answers {
            val pairs = firstArg<List<Pair<String, Int>>>()
            customerDbWrite { pairs.forEach { (id, qty) -> if (id == ticketId) ticketMongo.res += qty } }
        }

        coEvery { capacityDB.loadTicketCapacityById(capacityId) } answers {
            TicketCapacityDocument(
                id = capacityId, showId = SHOW_ID, sessionId = SESSION_ID, calendarDate = date.toString(),
                totCap = capacityMongo.tot, remCap = capacityMongo.rem, resCap = capacityMongo.res,
            )
        }
        coEvery { capacityDB.changeCapacity(capacityId, any()) } coAnswers {
            val delta = secondArg<Int>()
            adminDbWrite { capacityMongo.change(delta) }
        }
        coEvery { capacityDB.addReservedCap(any<List<Pair<String, Int>>>()) } answers {
            val pairs = firstArg<List<Pair<String, Int>>>()
            customerDbWrite { pairs.forEach { (id, qty) -> if (id == capacityId) capacityMongo.res += qty } }
        }

        val day = TicketCalendarDayDocument(
            showId = SHOW_ID, sessionId = SESSION_ID, date = date.toString(), capacityId = dayCapacity?.let { capacityId },
        )
        coEvery { dayDB.loadDays(SESSION_ID, any(), any()) } returns listOf(day)
        coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession()
        coEvery { sessionCache.checkShowSessionInOpen(SHOW_ID, SESSION_ID) } returns true
        coEvery { redis.getReservation(resId) } returns TicketReservationDocument(
            id = resId, showId = SHOW_ID, sessionId = SESSION_ID, customerUserId = "cus-1", expireInS = 300,
            status = TicketReservationStatus.Reserving,
            details = listOf(TicketReservationDetailsDocument(
                ticketId = ticketId, quantity = 2, dayCapacityId = dayCapacity?.let { capacityId },
            )),
        )
        coEvery { redis.updateReservationStatus(resId, TicketReservationStatus.Reserving, TicketReservationStatus.Reserved) } returns 0
    }

    private fun request(quota: Int? = null, dailyCapacity: Int? = null) = BulkUpdateCalendarRequest(
        showId = SHOW_ID, sessionId = SESSION_ID, fromDate = date.toString(), toDate = date.toString(),
        quota = quota, dailyCapacity = dailyCapacity,
    )

    private suspend fun runRace(point: Interleave, request: BulkUpdateCalendarRequest) {
        adminUpdate = { service.bulkUpdate(request, "op") }
        interleave = point
        if (point == Interleave.STRADDLING_LAZY_INIT) reserveTwoSeats() else adminUpdate()

        assertTrue(interleave == null, "kịch bản xen $point không được kích hoạt")
        // Processor nuốt mọi exception: phải chắc lượt giữ chỗ thật sự thành công thì bất biến bên dưới mới có nghĩa.
        coVerify(exactly = 1) { retryable.createReservationDb(any()) }
    }

    private fun assertRedisNotAboveMongo(key: String, mongo: MongoCounter) {
        // Không có key thì lượt giữ chỗ kế tiếp nạp lại từ Mongo, không thể bán vượt.
        val onRedis = redisState[key] ?: return
        assertTrue(onRedis <= mongo.rem,
            "Redis còn $onRedis > remCap Mongo ${mongo.rem} (totCap ${mongo.tot}, resCap ${mongo.res}): bán vượt ${onRedis - mongo.rem} chỗ")
    }

    @Test
    fun `quota decrease while a reservation lazily inits the counter between admin steps does not oversell`() = runTest {
        arrange(ticketQuota = 10, dayCapacity = null)
        runRace(Interleave.BETWEEN_ADMIN_STEPS, request(quota = 5))
        assertRedisNotAboveMongo(ticketId, ticketMongo)
    }

    @Test
    fun `quota decrease while a reservation's lazy init straddles the admin update does not oversell`() = runTest {
        arrange(ticketQuota = 10, dayCapacity = null)
        runRace(Interleave.STRADDLING_LAZY_INIT, request(quota = 5))
        assertRedisNotAboveMongo(ticketId, ticketMongo)
    }

    @Test
    fun `quota increase while a reservation lazily inits the counter does not oversell`() = runTest {
        arrange(ticketQuota = 10, dayCapacity = null)
        runRace(Interleave.AFTER_ADMIN_DB_WRITE, request(quota = 15))
        assertRedisNotAboveMongo(ticketId, ticketMongo)
    }

    @Test
    fun `day capacity decrease while a reservation lazily inits the counter between admin steps does not oversell`() = runTest {
        arrange(ticketQuota = 100, dayCapacity = 10)
        runRace(Interleave.BETWEEN_ADMIN_STEPS, request(dailyCapacity = 5))
        assertRedisNotAboveMongo(capacityId, capacityMongo)
    }

    @Test
    fun `day capacity decrease while a reservation's lazy init straddles the admin update does not oversell`() = runTest {
        arrange(ticketQuota = 100, dayCapacity = 10)
        runRace(Interleave.STRADDLING_LAZY_INIT, request(dailyCapacity = 5))
        assertRedisNotAboveMongo(capacityId, capacityMongo)
    }

    @Test
    fun `day capacity increase while a reservation lazily inits the counter does not oversell`() = runTest {
        arrange(ticketQuota = 100, dayCapacity = 10)
        runRace(Interleave.AFTER_ADMIN_DB_WRITE, request(dailyCapacity = 15))
        assertRedisNotAboveMongo(capacityId, capacityMongo)
    }
}
