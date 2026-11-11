package meta.metasearch.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Session vé theo lịch được hiển thị/sắp xếp như đang diễn ra cho tới ngày cuối của lịch. */
class CalendarSessionsTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun session(type: String, start: Long, end: Long, status: String = "Open") = ShowSession(
        id = "s", startTime = start, endTime = end, status = status, isFree = false, sessionType = type,
    )

    @Test
    fun `a running calendar session is shown as now, a normal one keeps its start`() {
        assertEquals(now, CalendarSessions.displayStartTime(session("CALENDAR", now - 30 * day, now + 300 * day), now))
        assertEquals(now - 30 * day, CalendarSessions.displayStartTime(session("SINGLE", now - 30 * day, now + 300 * day), now))
        // Lịch chưa bắt đầu thì giữ ngày đầu; lịch đã hết thì giữ nguyên như suất đã qua.
        assertEquals(now + 5 * day, CalendarSessions.displayStartTime(session("CALENDAR", now + 5 * day, now + 300 * day), now))
        assertEquals(now - 30 * day, CalendarSessions.displayStartTime(session("CALENDAR", now - 30 * day, now - day), now))
    }

    @Test
    fun `ongoingUntil is the last date of calendar sessions still on sale`() {
        val active = listOf("OpeningWait", "Open", "Paused")
        val sessions = listOf(
            session("CALENDAR", now, now + 100 * day),
            session("CALENDAR", now, now + 400 * day, status = "Stopped"),
            session("SINGLE", now, now + 500 * day),
        )

        assertEquals(now + 100 * day, CalendarSessions.ongoingUntil(sessions, active))
        assertNull(CalendarSessions.ongoingUntil(listOf(session("SINGLE", now, now + day)), active))
    }
}
