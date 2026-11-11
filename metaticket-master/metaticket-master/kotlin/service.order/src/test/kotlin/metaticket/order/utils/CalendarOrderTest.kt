package metaticket.order.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.order.datastructure.kafka.TicketInfoDTO
import metaticket.show.grpc.ShowMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Đơn vé theo lịch: thời gian hiển thị trong email và tương thích JSON của event order-succeed. */
class CalendarOrderTest {

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private fun event(visitDate: String?) = OrderSucceedEvent(
        showId = "s", sessionId = "ss", orderId = "o", reservationId = "r", orderCode = "C1", ownerId = "u",
        ticketInfo = listOf(TicketInfoDTO("t", "STD", "SeatZone", 2, "Vé thường", 1)),
        createdAt = 1, lastModified = 1, visitDate = visitDate,
    )

    @Test
    fun `visit date is shown with the opening time in the email format`() {
        val session = ShowMessage.GetSessionResponse.newBuilder()
            .setCalendar(ShowMessage.SessionCalendarProto.newBuilder().setOpenTime("08:00").setCloseTime("17:00").setBookingWindowDays(30))
            .build()

        assertEquals("08:00, 08/11/2026", formatVisitDate("2026-11-08", session))
        assertEquals("08/11/2026", formatVisitDate("2026-11-08", ShowMessage.GetSessionResponse.getDefaultInstance()))
    }

    @Test
    fun `a normal order event keeps the old JSON shape`() {
        val json = mapper.writeValueAsString(event(visitDate = null))

        assertFalse(json.contains("visitDate"), json)
    }

    @Test
    fun `a calendar order event carries the visit date and unknown fields are ignored when reading`() {
        val json = mapper.writeValueAsString(event(visitDate = "2026-11-08"))
        val withFutureField = json.replaceFirst("{", "{\"someFutureField\":1,")

        val parsed = mapper.readValue(withFutureField, OrderSucceedEvent::class.java)

        assertEquals("2026-11-08", parsed.visitDate)
    }
}
