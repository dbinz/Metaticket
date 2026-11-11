package metaticket.admin.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

enum class ReportFilterStatus { Active, Inactive }

data class ReportFilterDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),
    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String? = null,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String? = null,
    val reportKey: String,
    val reportName: String,
    val username: String,
    val status: ReportFilterStatus,
    val filter: String,
    // Ngày tham quan (vé theo lịch) lưu ngoài token `filter`: admin bản cũ decode token bằng Jackson không bỏ qua
    // claim lạ, còn BSON bỏ qua field lạ → rollback admin không làm hỏng các bộ lọc đã lưu.
    val visitDateFrom: String? = null,
    val visitDateTo: String? = null,
    val version: Int = 1,
    val lastUpdated: Date = Date(),
    val createdAt: Date = Date(),
)