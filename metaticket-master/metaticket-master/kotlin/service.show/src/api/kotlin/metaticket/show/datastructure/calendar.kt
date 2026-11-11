package metaticket.show.datastructure

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * SINGLE: một suất tại một thời điểm (hành vi cũ, null cũng hiểu là SINGLE).
 * CALENDAR: vé theo lịch — một session bán vé cho từng ngày tham quan, tồn kho/giá theo ngày nằm ở service.ticket.
 */
enum class SessionType {
    SINGLE,
    CALENDAR,
}

/**
 * Quy tắc dùng chung cho vé theo lịch (show, ticket, order, ticketslots cùng dùng để không lệch nhau).
 * Mọi phép tính ngày theo giờ Việt Nam; ngày tham quan lưu dạng chuỗi "yyyy-MM-dd" để không lệch múi giờ.
 */
object CalendarRules {
    val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    const val PROMOTABLE_ED_TYPE = "PolicyConfig"
    const val MAX_BOOKING_WINDOW_DAYS = 730

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val DISPLAY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun parseTime(value: String?): LocalTime? =
        value?.let { runCatching { LocalTime.parse(it, TIME_FORMAT) }.getOrNull() }

    fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun today(now: Instant = Instant.now()): LocalDate = LocalDate.ofInstant(now, ZONE)

    fun displayDate(date: LocalDate): String = date.format(DISPLAY_DATE_FORMAT)

    /** @return thông báo lỗi tiếng Việt, null khi cấu hình hợp lệ. */
    fun validateConfig(openTime: String?, closeTime: String?, bookingWindowDays: Int?, sameDayCutoffMinutes: Int?): String? {
        val open = parseTime(openTime) ?: return "Giờ mở cửa không hợp lệ (HH:mm)"
        val close = parseTime(closeTime) ?: return "Giờ đóng cửa không hợp lệ (HH:mm)"
        if (!close.isAfter(open)) return "Giờ đóng cửa phải sau giờ mở cửa"
        if (bookingWindowDays == null || bookingWindowDays !in 1..MAX_BOOKING_WINDOW_DAYS) {
            return "Số ngày bán trước phải từ 1 đến $MAX_BOOKING_WINDOW_DAYS"
        }
        if (sameDayCutoffMinutes != null && sameDayCutoffMinutes < 0) {
            return "Số phút ngừng bán trong ngày không được âm"
        }
        return null
    }

    /**
     * Vé theo lịch chỉ soát vào đúng ngày tham quan. @return thông báo từ chối, null khi được vào; slot không có ngày
     * tham quan (vé suất thường) luôn qua.
     */
    fun checkInRejection(visitDate: String?, now: Instant = Instant.now()): String? {
        if (visitDate == null) return null
        val date = parseDate(visitDate) ?: return "Ngày tham quan của vé không hợp lệ"
        val today = today(now)
        return when {
            date.isAfter(today) -> "Vé chỉ có hiệu lực ngày ${displayDate(date)}, chưa đến ngày sử dụng"
            date.isBefore(today) -> "Vé chỉ có hiệu lực ngày ${displayDate(date)}, đã hết hạn"
            else -> null
        }
    }

    /**
     * Lý do ngày tham quan không bán được tại thời điểm [now], null khi bán được.
     * sameDayCutoffMinutes == null nghĩa là không bán vé cho chính ngày hôm nay.
     */
    fun unsellableReason(
        date: LocalDate,
        now: Instant,
        closeTime: LocalTime,
        bookingWindowDays: Int,
        sameDayCutoffMinutes: Int?,
    ): String? {
        val today = today(now)
        if (date.isBefore(today)) return "Ngày ${displayDate(date)} đã qua"
        if (date.isAfter(today.plusDays(bookingWindowDays.toLong()))) {
            return "Chỉ bán vé trước tối đa $bookingWindowDays ngày"
        }
        if (date == today) {
            val cutoff = sameDayCutoffMinutes ?: return "Không bán vé trong ngày"
            val lastSale = date.atTime(closeTime).minusMinutes(cutoff.toLong()).atZone(ZONE).toInstant()
            if (!now.isBefore(lastSale)) return "Đã hết giờ bán vé cho hôm nay"
        }
        return null
    }
}
