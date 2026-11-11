package meta.metasearch.dtos

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import meta.metasearch.models.Show
import meta.metasearch.models.ShowSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Card highlight chỉ mang `ongoingUntil` (không có startTime của suất) nên FE gắn nhãn "Đang diễn ra" theo đúng field này. */
class HighlightShowItemTest {

    // from() đọc đồng hồ thật → biên tính bằng ngày để không phụ thuộc thời điểm chạy test.
    private val now = System.currentTimeMillis()
    private val day = 86_400_000L

    private fun card(start: Long, end: Long) = HighlightShowItem.from(
        Show(
            id = "x", createdAt = 0, sessions = listOf(
                ShowSession(id = "s", status = "Open", startTime = start, endTime = end, isFree = false, sessionType = "CALENDAR"),
            ),
        )
    )

    @Test
    fun `a running calendar session carries ongoingUntil`() {
        assertEquals(now + 30 * day, card(now - 10 * day, now + 30 * day).ongoingUntil)
    }

    @Test
    fun `a calendar session that has not started yet is not on-going`() {
        val item = card(now + 10 * day, now + 60 * day)

        assertNull(item.ongoingUntil)
        assertEquals(now + 10 * day, item.startTime)
    }

    @Test
    fun `an ended calendar session is not on-going`() {
        assertNull(card(now - 60 * day, now - day).ongoingUntil)
    }

    // service.show publishes minPrice = null when no session is Open/Paused; the card must say so explicitly.
    @Test
    fun `a card without a price serializes minPrice as an explicit null`() {
        val json = jacksonObjectMapper().readTree(jacksonObjectMapper().writeValueAsString(card(now + day, now + 2 * day)))

        assertTrue(json.has("minPrice"))
        assertTrue(json["minPrice"].isNull)
        assertFalse(json.has("ongoingUntil"))
    }
}
