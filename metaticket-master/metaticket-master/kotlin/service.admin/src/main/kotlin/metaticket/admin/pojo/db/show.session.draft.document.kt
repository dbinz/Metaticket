package metaticket.admin.pojo.db

import metaticket.show.datastructure.MailPluginTemplate
import metaticket.show.datastructure.SessionType
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

data class CityDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),
    var name : String = "",
    val code: String,
)


data class ShowSessionDraftDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    var showId : String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId : String? = null,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val seatPlanId : String? = null,
    val edType : String? = null,

    val ledgendImage: String? = null,
    val ledgendImageUrl: String? = null,
    val eventSquareImage: String? = null,
    val eventSquareImageUrl: String? = null,
    val eventNameImage: String? = null,
    val eventNameImageUrl: String? = null,
    /** Optional HTML for booking "Thông tin bổ sung" (promoted onto the active session). */
    val extraInfo: String? = null,

    val code: String?,
    val sessionName: String?,
    val address: String,
    val startTime: Long,
    val endTime: Long,
    val requireDelivery: Boolean,
    val orderNote: String? = null,
    val paymentOffline: Boolean? = null,
    val paymentNote: String? = null,
    var promotionsData: String? = null,
    var mailPluginTemplate: List<MailPluginTemplate>? = null,

    val status: ShowSessionDraftStatus = ShowSessionDraftStatus.None,
    val city: CityDocument? = null,
    val isFree: Boolean? = null,
    val showInHome: Boolean? = null,
    val pgAccountCode: String? = null,
    val paymentMethods: List<String>? = null,

    /**
     * Nếu enable, sẽ sinh ra slot ticket cho show, người mua vé có thể quản lý vé của họ trên homepage.
     * Nếu disable, bỏ qua việc sinh ra slot ticket, đồng thời chức năng gửi qrcode đính kèm email cũng không khả dụng.
     */
    var manageTicket: Boolean? = false,

    /**
     * Chỉ có thể enable được khi đã enable manage ticket
     * Nếu enable, khi setup show, chọn plugin QRCode plugin và qrcode email template để đính kèm qrcode trong email
     * Nếu disable thì sẽ không gửi kèm mã QRcode theo email, cho du có chọn plugin và email template là qrcode đi chăng nữa.
     */
    var qrcodeEmail: Boolean? = false,

    /** Optional Canonical_Session_Slug carried from the admin form; promoted onto the active session. */
    val slug: String? = null,

    /** null = SINGLE. CALENDAR: vé theo lịch, startTime/endTime là ngày đầu/cuối của lịch. */
    val sessionType: SessionType? = null,
    val calendar: SessionCalendarDraft? = null,

    val createdAt: Date = Date(),
)

/** Cấu hình vé theo lịch trên nháp; cùng dạng với SessionCalendarConfig của service.show. */
data class SessionCalendarDraft(
    val openTime: String,
    val closeTime: String,
    val bookingWindowDays: Int,
    val sameDayCutoffMinutes: Int? = null,
)

enum class ShowSessionDraftStatus {
    None, Achieved, Deleted
}
