package metaticket.ticket.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Giá trị mặc định kèm giá trị riêng theo thứ (1 = T2 … 7 = CN). */
data class ByDayOfWeek<T>(
    val default: T,
    val byDayOfWeek: Map<Int, T> = emptyMap(),
) {
    fun resolve(date: LocalDate): T = byDayOfWeek[date.dayOfWeek.value] ?: default
}

/** Phần tính toán thuần của việc sinh/sửa lịch bán, tách khỏi I/O để test được. */
object CalendarPlanner {
    const val MAX_DAYS_PER_REQUEST = 366
    val ALL_DAYS_OF_WEEK = (1..7).toSet()

    /**
     * Trần giá một vé lịch (VND), giả định nghiệp vụ vì vé thường chưa có trần. Không có trần thì giá cỡ Long.MAX nhân
     * số lượng tràn số khi order cộng tiền (tổng âm).
     */
    const val MAX_PRICE = 100_000_000_000L

    /** Số ô xử lý đồng thời khi sửa/xóa hàng loạt: đủ để 366 ngày × vài chục hạng xong trước timeout nginx 60s. */
    const val BULK_PARALLELISM = 32

    /** Số lỗi tối đa gắn kèm (suppressed) khi nhiều ô lỗi cùng lúc: đủ để chẩn đoán, không in hàng nghìn stack trace. */
    const val BULK_MAX_SUPPRESSED = 5

    /** Các ngày trong [from, to] rơi vào [daysOfWeek] và không nằm trong [excluded], tăng dần. */
    fun expandDates(from: LocalDate, to: LocalDate, daysOfWeek: Set<Int>, excluded: Set<LocalDate> = emptySet()): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { it.dayOfWeek.value in daysOfWeek && it !in excluded }
            .toList()

    /** @return thông báo lỗi tiếng Việt, null khi khoảng ngày hợp lệ. */
    fun validateRange(from: LocalDate, to: LocalDate, sessionFirstDate: LocalDate, sessionLastDate: LocalDate): String? {
        if (to.isBefore(from)) return "Ngày kết thúc phải sau ngày bắt đầu"
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS_PER_REQUEST) {
            return "Mỗi lần chỉ thao tác tối đa $MAX_DAYS_PER_REQUEST ngày"
        }
        if (from.isBefore(sessionFirstDate) || to.isAfter(sessionLastDate)) {
            return "Khoảng ngày phải nằm trong thời gian của suất ($sessionFirstDate – $sessionLastDate)"
        }
        return null
    }

    fun validateDaysOfWeek(daysOfWeek: Collection<Int>): String? {
        if (daysOfWeek.isEmpty()) return "Chọn ít nhất một thứ trong tuần"
        if (daysOfWeek.any { it !in ALL_DAYS_OF_WEEK }) return "Thứ trong tuần phải từ 1 (T2) đến 7 (CN)"
        return null
    }

    /**
     * Khai giá/quota cho thứ không bán là dấu hiệu nhập nhầm, nên báo lỗi thay vì bỏ qua lặng lẽ.
     * @return thông báo lỗi tiếng Việt, null khi hợp lệ.
     */
    fun <T> validateByDayOfWeek(label: String, value: ByDayOfWeek<T>, daysOfWeek: Set<Int>): String? {
        val unknown = value.byDayOfWeek.keys.filter { it !in daysOfWeek }
        if (unknown.isNotEmpty()) return "$label: khai cho thứ không bán (${unknown.sorted().joinToString { dayLabel(it) }})"
        return null
    }

    fun dayLabel(dayOfWeek: Int): String = if (dayOfWeek == 7) "CN" else "T${dayOfWeek + 1}"

    private const val MAX_ECHO_LENGTH = 40

    /**
     * Input của client trước khi đưa vào thông báo lỗi: surrogate UTF-16 lẻ làm Ktor encode text/plain thất bại (500 thay
     * vì 400), ký tự điều khiển/chuỗi dài làm bẩn thông báo — thay bằng U+FFFD và cắt còn [MAX_ECHO_LENGTH] ký tự.
     */
    fun echoInput(value: String): String {
        val codePoints = value.codePoints().toArray()
        val shown = StringBuilder()
        codePoints.take(MAX_ECHO_LENGTH).forEach { cp ->
            // codePoints() trả surrogate lẻ thành một code point riêng trong dải D800–DFFF.
            val unsafe = cp in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code || Character.isISOControl(cp)
            shown.appendCodePoint(if (unsafe) '�'.code else cp)
        }
        if (codePoints.size > MAX_ECHO_LENGTH) shown.append('…')
        return shown.toString()
    }
}
