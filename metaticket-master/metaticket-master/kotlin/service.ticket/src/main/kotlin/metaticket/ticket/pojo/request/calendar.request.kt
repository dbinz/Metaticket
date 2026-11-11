package metaticket.ticket.pojo.request

import metaticket.ticket.calendar.ByDayOfWeek
import metaticket.ticket.pojo.db.CalendarDayStatus

/** Một loại vé của lịch: [category] là mã hạng trong category mapping của session (thành sid của vé). */
data class CalendarTicketTypeDTO(
    val category: String,
    val dname: String? = null,
    val policy: String? = null,
    val price: ByDayOfWeek<Long>,
    val quota: ByDayOfWeek<Int>,
)

/** Ngày dạng "yyyy-MM-dd"; thứ 1 = T2 … 7 = CN. */
data class GenerateCalendarRequest(
    val showId: String,
    val sessionId: String,
    val fromDate: String,
    val toDate: String,
    val daysOfWeek: List<Int>,
    val excludeDates: List<String> = emptyList(),
    val types: List<CalendarTicketTypeDTO>,
    /** Sức chứa chung cả ngày cho mọi loại vé; null = chỉ giới hạn theo quota từng loại. */
    val dailyCapacity: ByDayOfWeek<Int>? = null,
)

/**
 * Chọn ô (ngày, loại vé) để sửa/xóa: ngày trong [fromDate, toDate], lọc thêm theo [daysOfWeek] (null = mọi thứ) và
 * [dates] (null = mọi ngày trong khoảng); [categories] null = mọi loại vé.
 */
data class BulkUpdateCalendarRequest(
    val showId: String,
    val sessionId: String,
    val fromDate: String,
    val toDate: String,
    val daysOfWeek: List<Int>? = null,
    val dates: List<String>? = null,
    val categories: List<String>? = null,
    val price: Long? = null,
    val quota: Int? = null,
    val status: CalendarDayStatus? = null,
    val dailyCapacity: Int? = null,
)

data class DeleteCalendarRequest(
    val showId: String,
    val sessionId: String,
    val fromDate: String,
    val toDate: String,
    val daysOfWeek: List<Int>? = null,
    val dates: List<String>? = null,
    val categories: List<String>? = null,
)
