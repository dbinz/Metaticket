package metaticket.ticket.service

import io.ktor.server.application.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import metaticket.ticket.ReservationExecutionRetryable
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.database.service.TicketReservationDBService
import metaticket.ticket.datastructure.SeatType
import metaticket.ticket.datastructure.TicketReservationStatus.Locked
import metaticket.ticket.datastructure.TicketReservationStatus.Unlocked
import metaticket.ticket.datastructure.TicketStatus
import metaticket.ticket.pojo.db.TicketDocument
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import metaticket.ticket.pojo.db.TicketReservationDocument
import metaticket.ticket.pojo.request.TicketReservationDetailDTO
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.redis.UpdateRedisFailedConstants
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Kiểm tra khóa/mở vé (dùng chung cho admin và đối tác).
 *
 * Bất biến cần giữ: capacity bị trừ đúng một lần cho mỗi vé đang khóa. Cụ thể:
 * - khóa thất bại ở bất kỳ bước nào thì không để lại doc Locked và trả lại mọi capacity đã trừ;
 * - khi Redis chưa có counter (suất chưa mở bán), Mongo phải kiểm tra còn đủ vé;
 * - mở khóa trả capacity đúng một lần, kể cả khi bị gọi lặp.
 */
class ReservationCoreServiceLockTest {

    private val showId = "6595773df323909fe23b954e"
    private val sessionId = "65957746f323909fe23b954f"
    private val otherSessionId = "65957746f323909fe23b9550"
    private val seatId = "66a000000000000000000001"
    private val seat2Id = "66a000000000000000000002"
    private val zoneId = "66a000000000000000000003"
    private val capacityId = "66b000000000000000000001"
    private val resId = "66c000000000000000000001"
    private val operator = "op-user-1"

    private lateinit var ticketResDB: TicketReservationDBService
    private lateinit var redis: RedisAsyncClient
    private lateinit var retryable: ReservationExecutionRetryable
    private lateinit var ticketDocDB: TicketDocDBService
    private lateinit var ticketCapacityDB: TicketCapacityDBService
    private lateinit var service: ReservationCoreService

    @BeforeEach
    fun setup() {
        val env = mockk<ApplicationEnvironment>()
        every { env.config.property("jrunr.expireReservation.delayInSecond").getString() } returns "300"

        ticketResDB = mockk()
        redis = mockk()
        retryable = mockk(relaxed = true)
        ticketDocDB = mockk()
        ticketCapacityDB = mockk()

        service = ReservationCoreService(
            env = env,
            ticketResDB = ticketResDB,
            redisClient = redis,
            resExeRetryable = retryable,
            showSessionCache = mockk(),
            ticketDocDB = ticketDocDB,
            configPerResCache = mockk(),
            jobrunrGateway = mockk(),
            reservationReps = mockk(),
            ticketCapacityDB = ticketCapacityDB,
            calendarDayDB = mockk(),
        )
    }

    private fun ticket(
        id: String,
        seatType: SeatType = SeatType.Seat,
        capacityId: String? = null,
        sessionId: String = this.sessionId,
    ) = TicketDocument(
        id = id, showId = showId, sessionId = sessionId, capacityId = capacityId, sid = "sid-$id",
        seatType = seatType, status = TicketStatus.Opened, priceCat = "VIP", basePrice = 100_000,
    )

    private fun lockCommand(vararg details: Pair<String, Int>) = LockTicketsCommand(
        showId = showId,
        sessionId = sessionId,
        operatorId = operator,
        details = details.map { (id, qty) -> TicketReservationDetailDTO(ticketId = id, quantity = qty) },
    )

    private fun givenTickets(vararg tickets: TicketDocument) {
        coEvery { ticketDocDB.loadTicketDocByIds(any()) } returns tickets.toList()
    }

    @Nested
    inner class LockTickets {

        /**
         * Luồng chuẩn khi suất đang mở bán: trừ Redis → cập nhật DB → ghi doc Locked sau cùng.
         */
        @Test
        fun `open session - takes redis then db then writes the lock document last`() = runTest {
            givenTickets(ticket(seatId))
            coEvery { redis.addTicketRemCap(seatId, -1) } returns 0
            coEvery { ticketDocDB.addReservedCap(listOf(seatId to 1)) } returns true
            coEvery { ticketResDB.insertReservation(any()) } returns resId

            val reservation = service.lockTickets(lockCommand(seatId to 1))

            assertEquals(Locked, reservation.status)
            assertEquals(-1L, reservation.expireInS)
            assertEquals(operator, reservation.customerUserId)
            assertEquals(listOf(seatId), reservation.details.map { it.ticketId })
            coVerifyOrder {
                redis.addTicketRemCap(seatId, -1)
                ticketDocDB.addReservedCap(listOf(seatId to 1))
                ticketResDB.insertReservation(any())
            }
            coVerify(exactly = 0) { retryable.releaseRemainingCapOnRedis(any(), any()) }
        }

        /**
         * Lỗi C1 cũ: doc Locked được ghi trước rồi mới trừ Redis, nên hết vé vẫn để lại "khóa ma".
         */
        @Test
        fun `not enough capacity on redis - no lock document and already taken tickets are given back`() = runTest {
            givenTickets(ticket(seatId), ticket(seat2Id))
            coEvery { redis.addTicketRemCap(seatId, -1) } returns 0
            coEvery { redis.addTicketRemCap(seat2Id, -1) } returns UpdateRedisFailedConstants.FAILED

            assertFailsWith<ReservationException.Conflict> {
                service.lockTickets(lockCommand(seatId to 1, seat2Id to 1))
            }

            coVerify(exactly = 0) { ticketResDB.insertReservation(any()) }
            coVerify { retryable.releaseRemainingCapOnRedis(any(), match { d -> d.map { it.ticketId } == listOf(seatId) }) }
            coVerify(exactly = 0) { retryable.releaseReservedCapInDb(any(), any()) }
        }

        @Test
        fun `db update fails - redis is given back and no lock document is written`() = runTest {
            givenTickets(ticket(seatId))
            coEvery { redis.addTicketRemCap(seatId, -1) } returns 0
            coEvery { ticketDocDB.addReservedCap(any()) } returns false

            assertFailsWith<ReservationException.InternalError> { service.lockTickets(lockCommand(seatId to 1)) }

            coVerify(exactly = 0) { ticketResDB.insertReservation(any()) }
            coVerify { retryable.releaseRemainingCapOnRedis(any(), match { it.single().ticketId == seatId }) }
            coVerify(exactly = 0) { retryable.releaseReservedCapInDb(any(), any()) }
        }

        @Test
        fun `lock document insert fails - redis and db are both given back`() = runTest {
            givenTickets(ticket(seatId))
            coEvery { redis.addTicketRemCap(seatId, -1) } returns 0
            coEvery { ticketDocDB.addReservedCap(any()) } returns true
            coEvery { ticketResDB.insertReservation(any()) } throws RuntimeException("mongo down")
            coEvery { ticketResDB.loadReservationById(any()) } returns null

            assertFailsWith<ReservationException.InternalError> { service.lockTickets(lockCommand(seatId to 1)) }

            coVerify { retryable.releaseRemainingCapOnRedis(any(), match { it.single().ticketId == seatId }) }
            coVerify { retryable.releaseReservedCapInDb(any(), match { it.single().ticketId == seatId }) }
        }

        /**
         * Insert đã ghi được nhưng mất ack: nếu hoàn lại capacity thì doc Locked còn đó sẽ bán vượt khi mở khóa.
         */
        @Test
        fun `insert acknowledgement lost but document written - treated as success, nothing given back`() = runTest {
            givenTickets(ticket(seatId))
            coEvery { redis.addTicketRemCap(seatId, -1) } returns 0
            coEvery { ticketDocDB.addReservedCap(any()) } returns true
            coEvery { ticketResDB.insertReservation(any()) } throws RuntimeException("timeout")
            coEvery { ticketResDB.loadReservationById(any()) } answers {
                TicketReservationDocument(firstArg(), showId, sessionId, emptyList(), Locked, operator, -1)
            }

            val reservation = service.lockTickets(lockCommand(seatId to 1))

            assertEquals(Locked, reservation.status)
            coVerify(exactly = 0) { retryable.releaseRemainingCapOnRedis(any(), any()) }
            coVerify(exactly = 0) { retryable.releaseReservedCapInDb(any(), any()) }
        }

        /**
         * Lỗi C2 cũ: Redis không có key thì bỏ qua kiểm tra, remCap trong Mongo có thể âm.
         */
        @Test
        fun `session not opened yet - capacity is taken with a conditional mongo update`() = runTest {
            givenTickets(ticket(seatId))
            coEvery { redis.addTicketRemCap(seatId, -1) } returns UpdateRedisFailedConstants.NOT_EXIST
            coEvery { ticketDocDB.tryAddReservedCap(seatId, 1) } returns true
            coEvery { redis.getTicketRemCap(seatId) } returns null
            coEvery { ticketResDB.insertReservation(any()) } returns resId

            service.lockTickets(lockCommand(seatId to 1))

            coVerify(exactly = 0) { ticketDocDB.addReservedCap(any()) }
            coVerify(exactly = 1) { ticketResDB.insertReservation(any()) }
        }

        @Test
        fun `session not opened yet and mongo has not enough left - conflict, nothing written`() = runTest {
            givenTickets(ticket(seatId))
            coEvery { redis.addTicketRemCap(seatId, -1) } returns UpdateRedisFailedConstants.NOT_EXIST
            coEvery { ticketDocDB.tryAddReservedCap(seatId, 1) } returns false

            assertFailsWith<ReservationException.Conflict> { service.lockTickets(lockCommand(seatId to 1)) }

            coVerify(exactly = 0) { ticketResDB.insertReservation(any()) }
            coVerify(exactly = 0) { retryable.releaseRemainingCapOnRedis(any(), any()) }
            coVerify(exactly = 0) { retryable.releaseReservedCapInDb(any(), any()) }
        }

        /**
         * Counter xuất hiện ngay sau khi trừ Mongo (startTicket chạy cùng lúc): trừ thêm Redis để lệch về phía an toàn.
         */
        @Test
        fun `counter appears during a pre-open lock - redis is taken too`() = runTest {
            givenTickets(ticket(seatId))
            coEvery { redis.addTicketRemCap(seatId, -1) } returnsMany listOf(UpdateRedisFailedConstants.NOT_EXIST, 0)
            coEvery { ticketDocDB.tryAddReservedCap(seatId, 1) } returns true
            coEvery { redis.getTicketRemCap(seatId) } returns 1
            coEvery { ticketResDB.insertReservation(any()) } returns resId

            service.lockTickets(lockCommand(seatId to 1))

            coVerify(exactly = 2) { redis.addTicketRemCap(seatId, -1) }
            coVerify(exactly = 0) { ticketDocDB.addReservedCap(any()) }
        }

        @Test
        fun `ticket of another session - rejected before any capacity is taken`() = runTest {
            givenTickets(ticket(seatId, sessionId = otherSessionId))

            assertFailsWith<ReservationException.PreconditionFailed> { service.lockTickets(lockCommand(seatId to 1)) }

            coVerify(exactly = 0) { redis.addTicketRemCap(any(), any()) }
        }

        @Test
        fun `unknown ticket - not found`() = runTest {
            givenTickets()

            assertFailsWith<ReservationException.NotFound> { service.lockTickets(lockCommand(seatId to 1)) }
            coVerify(exactly = 0) { redis.addTicketRemCap(any(), any()) }
        }

        @Test
        fun `invalid ticket id - not found without touching the database`() = runTest {
            assertFailsWith<ReservationException.NotFound> { service.lockTickets(lockCommand("not-an-id" to 1)) }
            coVerify(exactly = 0) { ticketDocDB.loadTicketDocByIds(any()) }
        }

        @Test
        fun `seat with quantity above one - rejected`() = runTest {
            givenTickets(ticket(seatId))

            assertFailsWith<ReservationException.PreconditionFailed> { service.lockTickets(lockCommand(seatId to 2)) }
            coVerify(exactly = 0) { redis.addTicketRemCap(any(), any()) }
        }

        /**
         * Tạo đơn hàng từ reservation sẽ lỗi nếu trùng ticketId, nên các dòng trùng được gộp lại.
         */
        @Test
        fun `duplicated zone ticket - quantities merged into one detail`() = runTest {
            givenTickets(ticket(zoneId, seatType = SeatType.SeatZone))
            coEvery { redis.addTicketRemCap(zoneId, -5) } returns 10
            coEvery { ticketDocDB.addReservedCap(listOf(zoneId to 5)) } returns true
            coEvery { ticketResDB.insertReservation(any()) } returns resId

            val reservation = service.lockTickets(lockCommand(zoneId to 2, zoneId to 3))

            assertEquals(listOf(5), reservation.details.map { it.quantity })
            coVerify(exactly = 1) { redis.addTicketRemCap(zoneId, -5) }
        }

        @Test
        fun `shared capacity - taken on the capacity counter and capacity document`() = runTest {
            givenTickets(ticket(zoneId, seatType = SeatType.SeatZone, capacityId = capacityId))
            coEvery { redis.addTicketRemCap(capacityId, -2) } returns 3
            coEvery { ticketCapacityDB.addReservedCap(listOf(capacityId to 2)) } returns true
            coEvery { ticketResDB.insertReservation(any()) } returns resId

            val reservation = service.lockTickets(lockCommand(zoneId to 2))

            assertEquals(capacityId, reservation.details.single().capacityId)
            coVerify(exactly = 0) { ticketDocDB.addReservedCap(any()) }
        }

        @Test
        fun `empty request - rejected`() = runTest {
            assertFailsWith<ReservationException.PreconditionFailed> { service.lockTickets(lockCommand()) }
        }
    }

    @Nested
    inner class UnlockReservation {

        private val lockedDetails = listOf(TicketReservationDetailsDocument(ticketId = seatId, quantity = 1, sid = "sid-$seatId"))

        /**
         * Lỗi C3 cũ: capacity được trả theo bản đọc trước CAS. Giờ trả theo đúng doc CAS vừa chuyển trạng thái.
         */
        @Test
        fun `unlock - gives back exactly the details of the document it switched`() = runTest {
            coEvery { ticketResDB.updateStatus(resId, Locked, Unlocked, true) } returns
                TicketReservationDocument(resId, showId, sessionId, lockedDetails, Locked, operator, -1)

            service.unlockReservation(UnlockReservationCommand(resId, operator))

            coVerify(exactly = 1) { retryable.releaseRemainingCapOnRedis(resId, lockedDetails) }
            coVerify(exactly = 1) { retryable.releaseReservedCapInDb(resId, lockedDetails) }
            coVerify(exactly = 0) { retryable.updateReservationStatusDb(any(), any(), any()) }
        }

        @Test
        fun `unlock twice - second call gives nothing back`() = runTest {
            coEvery { ticketResDB.updateStatus(resId, Locked, Unlocked, true) } returns null
            coEvery { ticketResDB.loadReservationById(resId) } returns
                TicketReservationDocument(resId, showId, sessionId, lockedDetails, Unlocked, operator, -1)

            assertFailsWith<ReservationException.PreconditionFailed> {
                service.unlockReservation(UnlockReservationCommand(resId, operator))
            }

            coVerify(exactly = 0) { retryable.releaseRemainingCapOnRedis(any(), any()) }
            coVerify(exactly = 0) { retryable.releaseReservedCapInDb(any(), any()) }
        }

        @Test
        fun `unknown reservation - not found`() = runTest {
            coEvery { ticketResDB.updateStatus(resId, Locked, Unlocked, true) } returns null
            coEvery { ticketResDB.loadReservationById(resId) } returns null

            assertFailsWith<ReservationException.NotFound> {
                service.unlockReservation(UnlockReservationCommand(resId, operator))
            }
        }

        @Test
        fun `invalid reservation id - not found without touching the database`() = runTest {
            assertFailsWith<ReservationException.NotFound> {
                service.unlockReservation(UnlockReservationCommand("bad-id", operator))
            }
            coVerify(exactly = 0) { ticketResDB.updateStatus(any(), any(), any(), any()) }
        }
    }
}
