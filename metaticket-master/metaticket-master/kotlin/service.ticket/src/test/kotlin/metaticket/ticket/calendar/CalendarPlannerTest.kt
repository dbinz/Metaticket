package metaticket.ticket.calendar

import metaticket.show.datastructure.CalendarRules
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Quy tắc thuần của vé theo lịch: sinh ngày, chọn giá theo thứ, ngày nào còn bán được (giờ Việt Nam). */
class CalendarPlannerTest {

    private val wednesday = 3
    private val saturday = 6
    private val sunday = 7

    @Test
    fun `expandDates keeps only the selected weekdays and drops excluded dates`() {
        // 2026-11-01 là Chủ nhật.
        val dates = CalendarPlanner.expandDates(
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
            setOf(wednesday, saturday, sunday),
            excluded = setOf(LocalDate.of(2026, 11, 15)),
        )

        assertEquals(12, dates.size)
        assertTrue(dates.all { it.dayOfWeek in setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) })
        assertFalse(LocalDate.of(2026, 11, 15) in dates)
        assertEquals(LocalDate.of(2026, 11, 1), dates.first())
        assertEquals(LocalDate.of(2026, 11, 29), dates.last())
    }

    @Test
    fun `ByDayOfWeek resolves the weekday price and falls back to default`() {
        val vip = ByDayOfWeek(default = 500_000L, byDayOfWeek = mapOf(wednesday to 450_000L, sunday to 600_000L))

        assertEquals(450_000L, vip.resolve(LocalDate.of(2026, 11, 4)))  // T4
        assertEquals(500_000L, vip.resolve(LocalDate.of(2026, 11, 7)))  // T7 không khai riêng → mặc định
        assertEquals(600_000L, vip.resolve(LocalDate.of(2026, 11, 8)))  // CN
    }

    @Test
    fun `validateByDayOfWeek rejects a price for a weekday that is not sold`() {
        val price = ByDayOfWeek(default = 100L, byDayOfWeek = mapOf(1 to 90L, sunday to 120L))

        val error = CalendarPlanner.validateByDayOfWeek("Giá VIP", price, setOf(wednesday, saturday, sunday))

        assertTrue(error?.contains("T2") == true, "error should name T2: $error")
        assertNull(CalendarPlanner.validateByDayOfWeek("Giá VIP", ByDayOfWeek(100L, mapOf(sunday to 1L)), setOf(sunday)))
    }

    @Test
    fun `validateRange caps the request size and keeps it inside the session`() {
        val first = LocalDate.of(2026, 1, 1)
        val last = LocalDate.of(2028, 12, 31)

        assertNull(CalendarPlanner.validateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), first, last)) // 366 ngày
        assertNotNull(CalendarPlanner.validateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2), first, last))
        assertNotNull(CalendarPlanner.validateRange(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 5), first, last))
        assertNotNull(CalendarPlanner.validateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 5), first, last))
    }

    @Test
    fun `validateDaysOfWeek accepts 1 to 7 only`() {
        assertNull(CalendarPlanner.validateDaysOfWeek(listOf(3, 6, 7)))
        assertNotNull(CalendarPlanner.validateDaysOfWeek(emptyList()))
        assertNotNull(CalendarPlanner.validateDaysOfWeek(listOf(0, 3)))
        assertNotNull(CalendarPlanner.validateDaysOfWeek(listOf(8)))
    }

    @Test
    fun `today uses Vietnam time, not UTC`() {
        // 17:30 UTC ngày 07/11 là 00:30 ngày 08/11 ở Việt Nam (Chủ nhật).
        val now = Instant.parse("2026-11-07T17:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 8), CalendarRules.today(now))
        assertEquals(DayOfWeek.SUNDAY, CalendarRules.today(now).dayOfWeek)
    }

    @Test
    fun `unsellableReason blocks past dates, dates beyond the window and late same-day sales`() {
        val close = LocalTime.of(17, 0)
        val tenAmVn = Instant.parse("2026-11-08T03:00:00Z")
        val fourPmVn = Instant.parse("2026-11-08T09:00:00Z")
        val today = LocalDate.of(2026, 11, 8)

        assertNotNull(CalendarRules.unsellableReason(today.minusDays(1), tenAmVn, close, 30, 60))
        assertNull(CalendarRules.unsellableReason(today.plusDays(30), tenAmVn, close, 30, 60))
        assertNotNull(CalendarRules.unsellableReason(today.plusDays(31), tenAmVn, close, 30, 60))
        // Hôm nay: bán tới trước giờ đóng cửa 60 phút (16:00).
        assertNull(CalendarRules.unsellableReason(today, tenAmVn, close, 30, 60))
        assertNotNull(CalendarRules.unsellableReason(today, fourPmVn, close, 30, 60))
        // Không cấu hình cutoff = không bán vé trong ngày.
        assertNotNull(CalendarRules.unsellableReason(today, tenAmVn, close, 30, null))
    }

    @Test
    fun `checkInRejection only lets a calendar ticket in on its visit date (Vietnam time)`() {
        // 23:30 ngày 07/11 ở Việt Nam = 16:30 UTC.
        val lateSaturdayVn = Instant.parse("2026-11-07T16:30:00Z")

        assertNull(CalendarRules.checkInRejection(null, lateSaturdayVn)) // vé suất thường
        assertNull(CalendarRules.checkInRejection("2026-11-07", lateSaturdayVn))
        assertNotNull(CalendarRules.checkInRejection("2026-11-08", lateSaturdayVn))
        assertNotNull(CalendarRules.checkInRejection("2026-11-06", lateSaturdayVn))
        assertNotNull(CalendarRules.checkInRejection("not-a-date", lateSaturdayVn))
    }

    @Test
    fun `validateConfig checks times, window and cutoff`() {
        assertNull(CalendarRules.validateConfig("08:00", "17:00", 90, 30))
        assertNull(CalendarRules.validateConfig("08:00", "17:00", 90, null))
        assertNotNull(CalendarRules.validateConfig("8h", "17:00", 90, null))
        assertNotNull(CalendarRules.validateConfig("17:00", "08:00", 90, null))
        assertNotNull(CalendarRules.validateConfig("08:00", "17:00", 0, null))
        assertNotNull(CalendarRules.validateConfig("08:00", "17:00", 90, -5))
    }
}
