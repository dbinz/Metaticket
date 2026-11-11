package metaticket.ticket.calendar

import metaticket.show.datastructure.CalendarRules
import metaticket.show.grpc.ShowMessage
import metaticket.ticket.pojo.db.CalendarDayStatus
import metaticket.ticket.pojo.db.TicketCalendarDayDocument
import metaticket.ticket.pojo.db.TicketDocument
import java.time.Instant

/** Điều kiện giữ chỗ vé theo lịch, thuần (không I/O) để test được. */
object CalendarReservationRules {

    /** Ngày tham quan chung của [tickets], hoặc lý do từ chối. */
    sealed interface DateResult {
        data class Ok(val date: String) : DateResult
        data class Rejected(val reason: String) : DateResult
    }

    /**
     * Mọi vé phải là vé theo lịch của đúng show/session và cùng một ngày: một đơn chỉ cho một ngày tham quan, và vé
     * của session khác không được giữ chỗ dưới session này (reservation lưu theo show/session của request).
     */
    fun resolveDate(showId: String, sessionId: String, tickets: List<TicketDocument>): DateResult {
        if (tickets.any { it.showId != showId || it.sessionId != sessionId }) {
            return DateResult.Rejected("Vé không thuộc suất đã chọn")
        }
        val dates = tickets.map { it.calendarDate }.toSet()
        if (null in dates) return DateResult.Rejected("Vé không thuộc lịch bán của suất")
        val date = dates.filterNotNull().singleOrNull()
            ?: return DateResult.Rejected("Mỗi đơn chỉ được chọn vé của một ngày")
        return DateResult.Ok(date)
    }

    /**
     * @return lý do không bán được ngày [day], null khi bán được. Ngày nằm ngoài thời gian của suất (admin thu hẹp lịch
     * sau khi đã sinh vé) cũng không bán.
     */
    fun unsellableReason(day: TicketCalendarDayDocument?, session: ShowMessage.GetSessionResponse, now: Instant): String? {
        if (day == null) return "Ngày này không có lịch bán"
        if (day.status != CalendarDayStatus.OPEN) return "Ngày ${day.date} đã ngừng bán"
        val date = CalendarRules.parseDate(day.date) ?: return "Ngày tham quan không hợp lệ"
        val firstDate = CalendarRules.today(Instant.ofEpochMilli(session.startTime))
        val lastDate = CalendarRules.today(Instant.ofEpochMilli(session.endTime))
        if (date.isBefore(firstDate) || date.isAfter(lastDate)) return "Ngày ${day.date} không thuộc thời gian của suất"
        val calendar = session.calendar
        val closeTime = CalendarRules.parseTime(calendar.closeTime) ?: return "Cấu hình lịch của suất không hợp lệ"
        return CalendarRules.unsellableReason(
            date = date,
            now = now,
            closeTime = closeTime,
            bookingWindowDays = calendar.bookingWindowDays,
            sameDayCutoffMinutes = if (calendar.hasSameDayCutoffMinutes()) calendar.sameDayCutoffMinutes else null,
        )
    }
}
