package metaticket.order

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import metaticket.order.datastructure.OrderStatus
import metaticket.order.request.LoadInvoiceStatisticsRequest
import metaticket.order.request.LoadReportRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * service.admin bản mới gửi request báo cáo sang service.order bản cũ (deploy lệch / rollback order):
 * Jackson mặc định từ chối field lạ, nên request không lọc theo ngày tham quan không được mang key `visitDate*`.
 */
class ReportRequestCompatTest {

    private val mapper = jacksonObjectMapper()

    /** Hình dạng request ở service.order trước tính năng vé theo lịch. */
    private data class LegacyReportRequest(
        val startDate: Long? = null,
        val endDate: Long? = null,
        val status: List<OrderStatus>? = null,
        val showId: String? = null,
        val sessionId: String? = null,
    )

    @Test
    fun `report request without visit date is readable by the legacy order service`() {
        val json = mapper.writeValueAsString(
            LoadReportRequest(startDate = 1L, endDate = 2L, status = listOf(OrderStatus.PaymentSucceed), showId = "s", sessionId = "ss")
        )

        assertFalse(json.contains("visitDate"))
        assertEquals(LegacyReportRequest(1L, 2L, listOf(OrderStatus.PaymentSucceed), "s", "ss"), mapper.readValue<LegacyReportRequest>(json))
    }

    @Test
    fun `statistics request without visit date is readable by the legacy order service`() {
        val json = mapper.writeValueAsString(
            LoadInvoiceStatisticsRequest(startDate = null, endDate = null, status = null, showId = "s", sessionId = null)
        )

        assertFalse(json.contains("visitDate"))
        assertEquals("s", mapper.readValue<LegacyReportRequest>(json).showId)
    }

    @Test
    fun `visit date is still sent when set`() {
        val json = mapper.writeValueAsString(LoadReportRequest(visitDateFrom = "2026-10-20", visitDateTo = "2026-10-20"))

        assertEquals("2026-10-20", mapper.readValue<LoadReportRequest>(json).visitDateFrom)
    }
}
