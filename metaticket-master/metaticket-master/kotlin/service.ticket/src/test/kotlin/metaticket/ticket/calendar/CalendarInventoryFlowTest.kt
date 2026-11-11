package metaticket.ticket.calendar

import com.mongodb.client.result.UpdateResult
import io.ktor.server.application.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import metaticket.ticket.ReservationExecutionRetryable
import metaticket.ticket.cache.ConfigPerReservationCache
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.CalendarTestFixtures.SESSION_ID
import metaticket.ticket.calendar.CalendarTestFixtures.SHOW_ID
import metaticket.ticket.calendar.CalendarTestFixtures.calendarSession
import metaticket.ticket.calendar.CalendarTestFixtures.calendarTicket
import metaticket.ticket.calendar.CalendarTestFixtures.today
import metaticket.ticket.controller.applyCapacities
import metaticket.ticket.database.service.TicketCalendarDayDBService
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.gateway.JobrunrGateway
import metaticket.ticket.grpc.TicketMessage
import metaticket.ticket.pojo.db.*
import metaticket.ticket.pojo.request.TicketReservationDetailDTO
import metaticket.ticket.processor.ReservationProcessor
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.redis.UpdateRedisFailedConstants
import metaticket.ticket.service.CreateReservationCommand
import metaticket.ticket.service.ReservationCoreService
import metaticket.ticket.service.ReservationException
import metaticket.ticket.service.TicketServiceImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/** Đường đi tồn kho của vé theo lịch qua giữ chỗ, processor và mở bán; kèm hồi quy lỗi B1/B2/B3. */
class CalendarInventoryFlowTest {

    private val resId = "66c000000000000000000001"
    private val dayCapacityId = "66b000000000000000000009"
    private val date = today.plusDays(5)

    @Nested
    inner class CreateReservation {
        private val redis = mockk<RedisAsyncClient>()
        private val ticketDocDB = mockk<TicketDocDBService>()
        private val dayDB = mockk<TicketCalendarDayDBService>()
        private val sessionCache = mockk<ShowSessionCache>()
        private val configCache = mockk<ConfigPerReservationCache>()
        private val jobrunr = mockk<JobrunrGateway>()

        private val service = ReservationCoreService(
            env = mockk<ApplicationEnvironment>().also {
                every { it.config.property("jrunr.expireReservation.delayInSecond").getString() } returns "300"
            },
            ticketResDB = mockk(),
            redisClient = redis,
            resExeRetryable = mockk(relaxed = true),
            showSessionCache = sessionCache,
            ticketDocDB = ticketDocDB,
            configPerResCache = configCache,
            jobrunrGateway = jobrunr,
            reservationReps = mockk(),
            ticketCapacityDB = mockk(),
            calendarDayDB = dayDB,
        )

        private fun stub(tickets: List<TicketDocument>, day: TicketCalendarDayDocument?) {
            coEvery { sessionCache.checkShowSessionInOpen(SHOW_ID, SESSION_ID) } returns true
            coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession()
            coEvery { ticketDocDB.loadTicketDocByIds(any()) } returns tickets
            coEvery { dayDB.loadDay(SESSION_ID, any()) } returns day
            coEvery { configCache.get(SHOW_ID, SESSION_ID) } returns null
            coEvery { jobrunr.scheduleExpireReservation(any(), any()) } returns true
        }

        private fun command(vararg ticketIds: String) = CreateReservationCommand(
            showId = SHOW_ID, sessionId = SESSION_ID, customerUserId = "cus-1",
            details = ticketIds.map { TicketReservationDetailDTO(ticketId = it, quantity = 2, capacityId = "spoofed") },
        )

        @Test
        fun `stores the day capacity on every detail and ignores a client-sent capacityId`() = runTest {
            stub(
                tickets = listOf(calendarTicket("66a000000000000000000001", date, sid = "VIP"), calendarTicket("66a000000000000000000002", date)),
                day = TicketCalendarDayDocument(showId = SHOW_ID, sessionId = SESSION_ID, date = date.toString(), capacityId = dayCapacityId),
            )
            val stored = slot<TicketReservationDocument>()
            coEvery { redis.setReservation(capture(stored)) } returns true

            service.createReservation(command("66a000000000000000000001", "66a000000000000000000002"))

            assertTrue(stored.captured.details.all { it.dayCapacityId == dayCapacityId })
            assertTrue(stored.captured.details.all { it.capacityId == null })
        }

        @Test
        fun `rejects tickets of two dates in one order`() = runTest {
            stub(
                tickets = listOf(calendarTicket("66a000000000000000000001", date), calendarTicket("66a000000000000000000002", date.plusDays(1))),
                day = null,
            )
            assertFailsWith<ReservationException.PreconditionFailed> {
                service.createReservation(command("66a000000000000000000001", "66a000000000000000000002"))
            }
        }

        @Test
        fun `rejects a closed date`() = runTest {
            stub(
                tickets = listOf(calendarTicket("66a000000000000000000001", date)),
                day = TicketCalendarDayDocument(showId = SHOW_ID, sessionId = SESSION_ID, date = date.toString(), status = CalendarDayStatus.CLOSED),
            )
            assertFailsWith<ReservationException.PreconditionFailed> {
                service.createReservation(command("66a000000000000000000001"))
            }
        }

        @Test
        fun `rejects a calendar ticket booked through a normal session`() = runTest {
            stub(tickets = listOf(calendarTicket("66a000000000000000000001", date)), day = null)
            coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession().toBuilder().clearSessionType().build()

            assertFailsWith<ReservationException.PreconditionFailed> {
                service.createReservation(command("66a000000000000000000001"))
            }
        }

        @Test
        fun `rejects a foreign session ticket booked through a normal session`() = runTest {
            val foreignTicket = calendarTicket("66a000000000000000000001", null, sessionId = "foreign-session")
            stub(tickets = listOf(foreignTicket), day = null)
            coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession().toBuilder().clearSessionType().build()

            assertFailsWith<ReservationException.PreconditionFailed> {
                service.createReservation(command("66a000000000000000000001"))
            }
        }

        @Test
        fun `rejects a foreign show ticket booked through a normal session`() = runTest {
            val foreignTicket = calendarTicket("66a000000000000000000001", null).apply { showId = "foreign-show" }
            stub(tickets = listOf(foreignTicket), day = null)
            coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession().toBuilder().clearSessionType().build()

            assertFailsWith<ReservationException.PreconditionFailed> {
                service.createReservation(command("66a000000000000000000001"))
            }
        }

        @Test
        fun `accepts several categories of the same show and session through a normal session`() = runTest {
            stub(
                tickets = listOf(calendarTicket("66a000000000000000000001", null, sid = "VIP"), calendarTicket("66a000000000000000000002", null)),
                day = null,
            )
            coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession().toBuilder().clearSessionType().build()
            val stored = slot<TicketReservationDocument>()
            coEvery { redis.setReservation(capture(stored)) } returns true

            service.createReservation(command("66a000000000000000000001", "66a000000000000000000002"))

            // Chốt chặn E1 không được chặn nhầm đặt vé hợp lệ của suất thường; không gắn sức chứa ngày
            assertEquals(listOf("66a000000000000000000001", "66a000000000000000000002"), stored.captured.details.map { it.ticketId })
            assertTrue(stored.captured.details.all { it.dayCapacityId == null })
        }
    }

    @Nested
    inner class Processor {
        private val redis = mockk<RedisAsyncClient>()
        private val ticketDocDB = mockk<TicketDocDBService>()
        private val capacityDB = mockk<TicketCapacityDBService>()
        private val sessionCache = mockk<ShowSessionCache>()
        private val retryable = mockk<ReservationExecutionRetryable>(relaxed = true)
        private val processor = ReservationProcessor(sessionCache, redis, ticketDocDB, capacityDB, retryable)

        private fun reserve(vararg details: TicketReservationDetailsDocument, calendar: Boolean = true) {
            coEvery { redis.getReservation(resId) } returns TicketReservationDocument(
                id = resId, showId = SHOW_ID, sessionId = SESSION_ID, details = details.toList(),
                status = TicketReservationStatus.Reserving, customerUserId = "cus-1", expireInS = 300,
            )
            coEvery { sessionCache.checkShowSessionInOpen(SHOW_ID, SESSION_ID) } returns true
            coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns
                if (calendar) calendarSession() else calendarSession().toBuilder().clearSessionType().build()
            coEvery { ticketDocDB.addReservedCap(any()) } returns true
            coEvery { capacityDB.addReservedCap(any<List<Pair<String, Int>>>()) } returns true
            coEvery { redis.updateReservationStatus(resId, TicketReservationStatus.Reserving, TicketReservationStatus.Reserved) } returns 0
        }

        @Test
        fun `calendar ticket without a Redis counter is initialised from Mongo, not sold unlimited (B2)`() = runTest {
            reserve(TicketReservationDetailsDocument(ticketId = "t1", quantity = 2))
            coEvery { redis.addTicketRemCap("t1", -2) } returnsMany listOf(UpdateRedisFailedConstants.NOT_EXIST, 3)
            coEvery { ticketDocDB.loadTicketDocById("t1") } returns calendarTicket("t1", date, quota = 5)
            coEvery { redis.setNxTicketRemCap("t1", 5) } returns true

            processor.process(resId)

            coVerify { redis.setNxTicketRemCap("t1", 5) }
            coVerify { retryable.createReservationDb(any()) }
        }

        @Test
        fun `calendar ticket with no stock after lazy init is rejected`() = runTest {
            reserve(TicketReservationDetailsDocument(ticketId = "t1", quantity = 2))
            coEvery { redis.addTicketRemCap("t1", -2) } returnsMany listOf(UpdateRedisFailedConstants.NOT_EXIST, UpdateRedisFailedConstants.FAILED)
            coEvery { ticketDocDB.loadTicketDocById("t1") } returns calendarTicket("t1", date, quota = 1)
            coEvery { redis.setNxTicketRemCap("t1", 1) } returns true

            processor.process(resId)

            coVerify(exactly = 0) { retryable.createReservationDb(any()) }
            coVerify(exactly = 0) { ticketDocDB.addReservedCap(any()) }
        }

        @Test
        fun `normal ticket without a Redis counter keeps the old behaviour`() = runTest {
            reserve(TicketReservationDetailsDocument(ticketId = "t1", quantity = 2), calendar = false)
            coEvery { redis.addTicketRemCap("t1", -2) } returns UpdateRedisFailedConstants.NOT_EXIST
            coEvery { ticketDocDB.loadTicketDocById("t1") } returns calendarTicket("t1", null)

            processor.process(resId)

            coVerify(exactly = 0) { redis.setNxTicketRemCap(any(), any()) }
            coVerify { retryable.createReservationDb(any()) }
        }

        @Test
        fun `day capacity is taken together with the ticket counter`() = runTest {
            reserve(TicketReservationDetailsDocument(ticketId = "t1", quantity = 2, dayCapacityId = dayCapacityId))
            coEvery { redis.addTicketRemCap("t1", -2) } returns 8
            coEvery { redis.addTicketRemCap(dayCapacityId, -2) } returns 40

            processor.process(resId)

            coVerify { ticketDocDB.addReservedCap(listOf("t1" to 2)) }
            coVerify { capacityDB.addReservedCap(listOf(dayCapacityId to 2)) }
            coVerify { retryable.createReservationDb(any()) }
        }

        @Test
        fun `full day capacity rolls the ticket counter back`() = runTest {
            reserve(TicketReservationDetailsDocument(ticketId = "t1", quantity = 2, dayCapacityId = dayCapacityId))
            coEvery { redis.addTicketRemCap("t1", -2) } returns 8
            coEvery { redis.addTicketRemCap(dayCapacityId, -2) } returns UpdateRedisFailedConstants.FAILED

            processor.process(resId)

            coVerify { retryable.releaseRemainingCapOnRedis(resId, match { units -> units.map { it.ticketId } == listOf("t1") && units.single().capacityId == null }) }
            coVerify(exactly = 0) { retryable.createReservationDb(any()) }
        }

        @Test
        fun `ticket counter failing in Mongo skips the day capacity and gives back every Redis counter`() = runTest {
            reserve(TicketReservationDetailsDocument(ticketId = "t1", quantity = 2, dayCapacityId = dayCapacityId))
            coEvery { redis.addTicketRemCap("t1", -2) } returns 8
            coEvery { redis.addTicketRemCap(dayCapacityId, -2) } returns 40
            coEvery { ticketDocDB.addReservedCap(any()) } returns false

            processor.process(resId)

            coVerify(exactly = 0) { capacityDB.addReservedCap(any<List<Pair<String, Int>>>()) }
            coVerify {
                retryable.releaseRemainingCapOnRedis(resId, match { units ->
                    units.map { it.capacityId ?: it.ticketId }.toSet() == setOf("t1", dayCapacityId)
                })
            }
            coVerify(exactly = 0) { retryable.createReservationDb(any()) }
        }

        @Test
        fun `calendar ticket deleted after the reservation was created is rejected, not sold unlimited`() = runTest {
            reserve(TicketReservationDetailsDocument(ticketId = "t1", quantity = 2))
            coEvery { redis.addTicketRemCap("t1", -2) } returns UpdateRedisFailedConstants.NOT_EXIST
            coEvery { ticketDocDB.loadTicketDocById("t1") } returns null

            processor.process(resId)

            coVerify(exactly = 0) { ticketDocDB.addReservedCap(any()) }
            coVerify(exactly = 0) { retryable.createReservationDb(any()) }
        }

        @Test
        fun `deleted day capacity of a calendar date is rejected, not sold unlimited`() = runTest {
            reserve(TicketReservationDetailsDocument(ticketId = "t1", quantity = 2, dayCapacityId = dayCapacityId))
            coEvery { redis.addTicketRemCap("t1", -2) } returns 8
            coEvery { redis.addTicketRemCap(dayCapacityId, -2) } returns UpdateRedisFailedConstants.NOT_EXIST
            coEvery { capacityDB.loadTicketCapacityById(dayCapacityId) } returns null

            processor.process(resId)

            coVerify(exactly = 0) { capacityDB.addReservedCap(any<List<Pair<String, Int>>>()) }
            coVerify(exactly = 0) { retryable.createReservationDb(any()) }
        }
    }

    @Test
    fun `startTicket only creates missing Redis counters (B1)`() = runTest {
        val ticketDocDB = mockk<TicketDocDBService>()
        val redis = mockk<RedisAsyncClient>()
        val service = TicketServiceImpl(ticketDocDB, mockk(), redis, mockk())
        coEvery { ticketDocDB.updateAllTicketStatus(SHOW_ID, SESSION_ID, any()) } returns mockk<UpdateResult>()
        coEvery { ticketDocDB.loadOpenNonCalendarTicketDoc(SHOW_ID, SESSION_ID) } returns listOf(calendarTicket("t1", null, quota = 7))
        coEvery { redis.setNxTicketRemCap("t1", 7) } returns false

        service.startTicket(TicketMessage.StartTicketRequest.newBuilder().setShowId(SHOW_ID).setSessionId(SESSION_ID).build())

        coVerify { redis.setNxTicketRemCap("t1", 7) }
        coVerify(exactly = 0) { redis.setTicketRemCap(any(), any()) }
    }

    @Test
    fun `applyCapacities gives each ticket its own capacity (B3)`() {
        val a = calendarTicket("t1", null).apply { capacityId = "capA" }
        val b = calendarTicket("t2", null).apply { capacityId = "capB" }

        applyCapacities(listOf(a, b), listOf(
            TicketCapacityDocument(id = "capA", showId = SHOW_ID, totCap = 10, remCap = 4, resCap = 6),
            TicketCapacityDocument(id = "capB", showId = SHOW_ID, totCap = 20, remCap = 15, resCap = 5),
        ))

        assertEquals(4, a.remCap)
        assertEquals(15, b.remCap)
        assertEquals(20, b.totCap)
    }
}
