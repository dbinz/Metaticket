package metaticket.order

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import kotlinx.coroutines.test.runTest
import metaticket.order.cache.CalculatePromotionClassesCache
import metaticket.order.cache.ShowCache
import metaticket.order.controller.OrderFailedHandler
import metaticket.order.database.service.*
import metaticket.order.gateway.*
import metaticket.order.kafka.OrderSucceedProducer
import metaticket.show.grpc.ShowMessage
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.grpc.TicketMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class OrderCreationValidationTest {

    private val reservationGateway = mockk<ReservationGateway>()
    private val showGateway = mockk<ShowGateway>()
    private val ticketGateway = mockk<TicketGateway>()
    private val showCache = mockk<ShowCache>()
    private val orderFailedHandler = mockk<OrderFailedHandler>(relaxed = true)

    private val service = OrderCreationService(
        orderSucceedProducer = mockk<OrderSucceedProducer>(relaxed = true),
        orderDB = mockk<OrderDBService>(relaxed = true),
        reservationGateway = reservationGateway,
        showGateway = showGateway,
        ticketGateway = ticketGateway,
        jobrunrGateway = mockk<JobrunrGateway>(relaxed = true),
        notificationGateway = mockk<NotificationGateway>(relaxed = true),
        showCache = showCache,
        orderFailedHandler = orderFailedHandler,
        customFieldDB = mockk<OrderCustomFieldMappingDBService>(relaxed = true),
        promotionDb = mockk<PromotionDBService>(relaxed = true),
        calculatePromotionClasses = mockk<CalculatePromotionClassesCache>(relaxed = true),
        orderDataCollectorGateway = mockk<OrderDataCollectorGateway>(relaxed = true),
        promotionCodeQuantityDb = mockk<PromotionCodeQuantityDBService>(relaxed = true),
        promotionComboQuantityDb = mockk<PromotionComboQuantityDBService>(relaxed = true),
        jsonMapper = mockk<ObjectMapper>(relaxed = true),
    )

    private val showId = "show-1"
    private val sessionId = "session-1"
    private val resId = "res-1"
    private val customerId = "user-1"

    @BeforeEach
    fun setUp() {
        val reservation = TicketMessage.ReservationProto.newBuilder()
            .setId(resId)
            .setShowId(showId)
            .setSessionId(sessionId)
            .setCustomerUserId(customerId)
            .setStatus(TicketReservationStatus.Reserved.name)
            .addDetails(TicketMessage.ReservationDetailProto.newBuilder().setTicketId("ticket-1").setQuantity(1))
            .build()
        coEvery { reservationGateway.getReservation(resId) } returns reservation

        val show = ShowMessage.GetShowResponse.newBuilder()
            .setShowId(showId)
            .addSessions(
                ShowMessage.GetSessionResponse.newBuilder()
                    .setSessionId(sessionId)
                    .setStatus("Open")
                    .setRequireDelivery(false)
            )
            .build()
        coEvery { showCache.getShow(showId) } returns show
        coEvery { showGateway.generateUniqueId(showId, sessionId) } returns "ORD001"
    }

    private fun input() = CreateOrderInput(
        reservationId = resId,
        customerUserId = customerId,
        customerName = "Test User",
        customerPhone = "0900000001",
        customerEmail = "test@example.com",
        deliveryInfo = null,
        customFields = null,
        promotionCode = null,
    )

    @Test
    fun `rejects order when ticket belongs to different show`() = runTest {
        val foreignTicket = TicketMessage.TicketProto.newBuilder()
            .setId("ticket-1")
            .setShowId("foreign-show")
            .setSessionId(sessionId)
            .setBasePrice(100_000)
            .setSid("STD")
            .setPriceCat("STD")
            .setSeatType("SeatZone")
            .build()
        coEvery { ticketGateway.getTicketsByIds(listOf("ticket-1")) } returns listOf(foreignTicket)

        val ex = assertFailsWith<OrderValidationException> {
            service.executeCreateOrder(input())
        }
        assertEquals("Vé không thuộc suất diễn của đơn hàng", ex.message)
        coVerify(exactly = 1) { orderFailedHandler.onCreateOrderFailed(resId) }
    }

    @Test
    fun `rejects order when ticket belongs to different session`() = runTest {
        val foreignTicket = TicketMessage.TicketProto.newBuilder()
            .setId("ticket-1")
            .setShowId(showId)
            .setSessionId("foreign-session")
            .setBasePrice(100_000)
            .setSid("STD")
            .setPriceCat("STD")
            .setSeatType("SeatZone")
            .build()
        coEvery { ticketGateway.getTicketsByIds(listOf("ticket-1")) } returns listOf(foreignTicket)

        val ex = assertFailsWith<OrderValidationException> {
            service.executeCreateOrder(input())
        }
        assertEquals("Vé không thuộc suất diễn của đơn hàng", ex.message)
        coVerify(exactly = 1) { orderFailedHandler.onCreateOrderFailed(resId) }
    }

    @Test
    fun `creates order when every ticket belongs to the reservation show and session`() = runTest {
        val reservation = TicketMessage.ReservationProto.newBuilder()
            .setId(resId)
            .setShowId(showId)
            .setSessionId(sessionId)
            .setCustomerUserId(customerId)
            .setStatus(TicketReservationStatus.Reserved.name)
            .addDetails(TicketMessage.ReservationDetailProto.newBuilder().setTicketId("ticket-1").setQuantity(1))
            .addDetails(TicketMessage.ReservationDetailProto.newBuilder().setTicketId("ticket-2").setQuantity(2))
            .build()
        coEvery { reservationGateway.getReservation(resId) } returns reservation
        val tickets = listOf("ticket-1" to "STD", "ticket-2" to "VIP").map { (id, cat) ->
            TicketMessage.TicketProto.newBuilder()
                .setId(id)
                .setShowId(showId)
                .setSessionId(sessionId)
                .setBasePrice(100_000)
                .setSid(cat)
                .setPriceCat(cat)
                .setSeatType("SeatZone")
                .build()
        }
        coEvery { ticketGateway.getTicketsByIds(listOf("ticket-1", "ticket-2")) } returns tickets

        val order = service.executeCreateOrder(input())

        // Chốt chặn E1 không được chặn nhầm đơn hợp lệ của suất thường nhiều hạng vé
        assertEquals(listOf("ticket-1", "ticket-2"), order.tickets.map { it.id })
        assertEquals(300_000, order.total)
        assertEquals(null, order.visitDate)
        coVerify(exactly = 0) { orderFailedHandler.onCreateOrderFailed(any()) }
    }
}
