package metaticket.ticket.calendar

import metaticket.ticket.calendar.CalendarTestFixtures.OTHER_SESSION_ID
import metaticket.ticket.calendar.CalendarTestFixtures.SESSION_ID
import metaticket.ticket.calendar.CalendarTestFixtures.SHOW_ID
import metaticket.ticket.calendar.CalendarTestFixtures.calendarSession
import metaticket.ticket.calendar.CalendarTestFixtures.calendarTicket
import metaticket.ticket.calendar.CalendarTestFixtures.today
import metaticket.ticket.pojo.db.CalendarDayStatus
import metaticket.ticket.pojo.db.TicketCalendarDayDocument
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import metaticket.ticket.pojo.db.toCounterUnits
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class CalendarReservationRulesTest {

    private val date = today.plusDays(5)

    @Test
    fun `resolveDate accepts tickets of one date of the session`() {
        val result = CalendarReservationRules.resolveDate(SHOW_ID, SESSION_ID, listOf(
            calendarTicket("a1", date, sid = "VIP"),
            calendarTicket("a2", date, sid = "STD"),
        ))
        assertEquals(CalendarReservationRules.DateResult.Ok(date.toString()), result)
    }

    @Test
    fun `resolveDate rejects mixed dates, foreign sessions and non-calendar tickets`() {
        val mixed = CalendarReservationRules.resolveDate(SHOW_ID, SESSION_ID, listOf(
            calendarTicket("a1", date), calendarTicket("a2", date.plusDays(1)),
        ))
        val foreign = CalendarReservationRules.resolveDate(SHOW_ID, SESSION_ID, listOf(
            calendarTicket("a1", date, sessionId = OTHER_SESSION_ID),
        ))
        val notCalendar = CalendarReservationRules.resolveDate(SHOW_ID, SESSION_ID, listOf(
            calendarTicket("a1", date), calendarTicket("a2", null),
        ))

        assertTrue(mixed is CalendarReservationRules.DateResult.Rejected)
        assertTrue(foreign is CalendarReservationRules.DateResult.Rejected)
        assertTrue(notCalendar is CalendarReservationRules.DateResult.Rejected)
    }

    @Test
    fun `unsellableReason rejects missing and closed days`() {
        val session = calendarSession()
        val open = TicketCalendarDayDocument(showId = SHOW_ID, sessionId = SESSION_ID, date = date.toString())

        assertNull(CalendarReservationRules.unsellableReason(open, session, Instant.now()))
        assertNotNull(CalendarReservationRules.unsellableReason(null, session, Instant.now()))
        assertNotNull(CalendarReservationRules.unsellableReason(open.copy(status = CalendarDayStatus.CLOSED), session, Instant.now()))
        assertNotNull(CalendarReservationRules.unsellableReason(open.copy(date = today.minusDays(1).toString()), session, Instant.now()))
    }

    @Test
    fun `unsellableReason rejects a date outside the session after the calendar was shortened`() {
        val shortened = calendarSession(firstDate = today, lastDate = date.minusDays(1))
        val day = TicketCalendarDayDocument(showId = SHOW_ID, sessionId = SESSION_ID, date = date.toString())

        assertNotNull(CalendarReservationRules.unsellableReason(day, shortened, Instant.now()))
        assertNull(CalendarReservationRules.unsellableReason(day, calendarSession(firstDate = today, lastDate = date), Instant.now()))
    }

    @Test
    fun `toCounterUnits adds the day capacity as a second counter and is idempotent`() {
        val details = listOf(
            TicketReservationDetailsDocument(ticketId = "t1", quantity = 2, dayCapacityId = "cap1"),
            TicketReservationDetailsDocument(ticketId = "t2", quantity = 1),
        )

        val units = details.toCounterUnits()

        assertEquals(3, units.size)
        assertEquals(listOf("t1", "cap1", "t2"), units.map { it.capacityId ?: it.ticketId })
        assertTrue(units.all { it.dayCapacityId == null })
        assertEquals(units, units.toCounterUnits())
    }
}
