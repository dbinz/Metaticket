package metaticket.ticket.pojo.response

import metaticket.ticket.pojo.db.CalendarDayStatus

enum class CalendarSkipReason {
    /** Ngày/vé đã có từ trước. */
    EXISTS,
    /** Đã có vé bán hoặc đang giữ: không đổi giá, không giảm quota, không xóa. */
    HAS_SALES,
    /** Khách đang giữ chỗ dở dang, thử lại sau ít phút. */
    HELD,
    PAST_DATE,
    NOT_FOUND,
}

data class CalendarSkip(
    val date: String,
    val category: String? = null,
    val reason: CalendarSkipReason,
)

data class GenerateCalendarResponse(
    val createdDays: Int,
    val createdTickets: Int,
    val skipped: List<CalendarSkip>,
)

data class BulkUpdateCalendarResponse(
    val updated: Int,
    val skipped: List<CalendarSkip>,
)

data class DeleteCalendarResponse(
    val deletedTickets: Int,
    val deletedDays: Int,
    val skipped: List<CalendarSkip>,
)

data class CalendarAdminTicket(
    val ticketId: String,
    val category: String,
    val priceCat: String,
    val dname: String?,
    val price: Long,
    val quota: Int,
    val remaining: Int,
    /** Đã bán + đang giữ. */
    val reserved: Int,
    /** true = đã có vé bán/giữ: chỉ được tăng quota hoặc ngừng bán. */
    val locked: Boolean,
)

data class CalendarAdminCapacity(
    val capacityId: String,
    val total: Int,
    val remaining: Int,
    val reserved: Int,
)

data class CalendarAdminDay(
    val date: String,
    val status: CalendarDayStatus,
    val capacity: CalendarAdminCapacity?,
    val tickets: List<CalendarAdminTicket>,
)

enum class CalendarPublicDayStatus { AVAILABLE, SOLD_OUT, CLOSED, UNAVAILABLE }

data class CalendarPublicDay(
    val date: String,
    val status: CalendarPublicDayStatus,
    val minPrice: Long?,
)
