package metaticket.ticket.calendar

import metaticket.show.datastructure.CalendarRules
import metaticket.show.datastructure.SessionType
import metaticket.show.grpc.ShowMessage
import metaticket.ticket.datastructure.SeatType
import metaticket.ticket.datastructure.TicketStatus
import metaticket.ticket.pojo.db.TicketDocument
import java.time.LocalDate

/** Dữ liệu mẫu dùng chung cho test vé theo lịch. Ngày tính tương đối theo hôm nay để test không hết hạn. */
object CalendarTestFixtures {
    const val SHOW_ID = "6595773df323909fe23b954e"
    const val SESSION_ID = "65957746f323909fe23b954f"
    const val OTHER_SESSION_ID = "65957746f323909fe23b9550"

    val today: LocalDate get() = CalendarRules.today()

    fun calendarSession(
        firstDate: LocalDate = today.minusDays(30),
        lastDate: LocalDate = today.plusDays(400),
        status: String = "Open",
        bookingWindowDays: Int = 90,
        sameDayCutoffMinutes: Int? = 60,
    ): ShowMessage.GetSessionResponse = ShowMessage.GetSessionResponse.newBuilder()
        .setShowId(SHOW_ID)
        .setSessionId(SESSION_ID)
        .setStatus(status)
        .setStartTime(firstDate.atStartOfDay(CalendarRules.ZONE).toInstant().toEpochMilli())
        .setEndTime(lastDate.atTime(23, 59).atZone(CalendarRules.ZONE).toInstant().toEpochMilli())
        .setSessionType(SessionType.CALENDAR.name)
        .setCalendar(
            ShowMessage.SessionCalendarProto.newBuilder()
                .setOpenTime("08:00")
                .setCloseTime("17:00")
                .setBookingWindowDays(bookingWindowDays)
                .also { b -> sameDayCutoffMinutes?.let { b.setSameDayCutoffMinutes(it) } }
        )
        .build()

    fun calendarTicket(
        id: String,
        date: LocalDate?,
        sid: String = "STD",
        price: Long = 300_000,
        quota: Int = 100,
        reserved: Int = 0,
        sessionId: String = SESSION_ID,
    ) = TicketDocument(
        id = id,
        showId = SHOW_ID,
        sessionId = sessionId,
        sid = sid,
        seatType = SeatType.SeatZone,
        status = TicketStatus.Opened,
        totCap = quota,
        remCap = quota - reserved,
        resCap = reserved,
        priceCat = sid,
        basePrice = price,
        calendarDate = date?.toString(),
    )

    /** Ngày gần nhất sau [from] (không tính [from]) rơi vào thứ [dayOfWeek] (1 = T2 … 7 = CN). */
    fun nextDayOfWeek(from: LocalDate, dayOfWeek: Int): LocalDate =
        generateSequence(from.plusDays(1)) { it.plusDays(1) }.first { it.dayOfWeek.value == dayOfWeek }
}
