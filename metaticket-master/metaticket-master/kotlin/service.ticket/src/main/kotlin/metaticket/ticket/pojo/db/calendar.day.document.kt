package metaticket.ticket.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

enum class CalendarDayStatus {
    OPEN,
    /** Ngừng bán thêm; vé đã bán của ngày vẫn hợp lệ. */
    CLOSED,
}

/**
 * Header của một ngày bán trong session vé theo lịch. Giá/quota từng loại vé nằm ở [TicketDocument] cùng
 * calendarDate; collection riêng vì startTicket đặt status Opened cho mọi vé của session, không dùng để đóng ngày được.
 */
data class TicketCalendarDayDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String,

    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,

    /** "yyyy-MM-dd" theo giờ VN. */
    val date: String,

    val status: CalendarDayStatus = CalendarDayStatus.OPEN,

    /** Sức chứa chung cả ngày cho mọi loại vé; null = chỉ giới hạn theo quota từng loại. */
    @BsonRepresentation(BsonType.OBJECT_ID)
    val capacityId: String? = null,

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)
