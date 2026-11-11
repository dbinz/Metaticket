package metaticket.show.service

import metaticket.show.datastructure.CalendarRules
import metaticket.show.datastructure.SessionType
import metaticket.show.pojo.db.SessionCalendarConfig
import java.util.*

/**
 * Ràng buộc của session vé theo lịch. Trả thông báo lỗi tiếng Việt, null khi hợp lệ.
 * Session lịch chỉ phát vé qua ticketslots (soát vé theo ngày) và chỉ bán vé số lượng, không dùng sơ đồ ghế.
 */
object CalendarSessionValidator {

    fun validate(
        sessionType: SessionType?,
        calendar: SessionCalendarConfig?,
        manageTicket: Boolean?,
        startTime: Date,
        endTime: Date,
    ): String? {
        if (sessionType != SessionType.CALENDAR) return null
        val config = calendar ?: return "Vé theo lịch cần cấu hình giờ mở cửa và số ngày bán trước"
        CalendarRules.validateConfig(
            config.openTime, config.closeTime, config.bookingWindowDays, config.sameDayCutoffMinutes
        )?.let { return it }
        if (manageTicket != true) return "Vé theo lịch bắt buộc bật quản lý vé"
        if (!endTime.after(startTime)) return "Ngày kết thúc phải sau ngày bắt đầu"
        return null
    }

    fun validateSeatPlan(sessionType: SessionType?, edType: String?, seatPlanId: String?): String? {
        if (sessionType != SessionType.CALENDAR) return null
        if (edType != null && edType != CalendarRules.PROMOTABLE_ED_TYPE) {
            return "Vé theo lịch chỉ dùng cấu hình vé ${CalendarRules.PROMOTABLE_ED_TYPE}, không dùng sơ đồ ghế"
        }
        if (seatPlanId != null) return "Vé theo lịch không dùng sơ đồ ghế"
        return null
    }
}
