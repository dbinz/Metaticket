package metaticket.show.pojo.db

import metaticket.payment.datastructure.PaymentType
import metaticket.show.datastructure.MailPluginTemplate
import metaticket.show.datastructure.SessionType
import metaticket.show.datastructure.ShowSessionStatus
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

enum class SaleMode { Auto, Manually }

data class CityDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),
    var name : String = "",
    val code: String,
)


data class ShowDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    val code: String,
    var sessions : List<ShowSessionDocument> = emptyList(),
    var showName : String = "",

    /**
     * Optional URL-friendly Show_Slug.
     * An absent slug is represented as `null` (no value / empty / whitespace-only input is stored as null).
     * When present, the stored value is always the Canonical_Slug (lowercase [a-z0-9-], 1..200 chars,
     * no leading/trailing/consecutive hyphens). Uniqueness is enforced by a partial unique index.
     */
    var slug: String? = null,

    var eventCategoryCode: String? = null,
    var eventCategory: String? = null,
    var website : String? = "",
    var bannerImageUrl: String? = "",   // show in admin page
    var detailImageUrl: String? = "",   // show in homepage
    var thumbnailUrl: String? = "",     // show in homepage
    var logoHostUrl: String? = "",
    var logoShowUrl: String? = "",
    var faq: String? = "",
    var ticketDescription: String? = "",
    var description: String? = "",
    var policy: String? = "",
    var status : ShowSessionStatus = ShowSessionStatus.Open,
    var shipPrice: Long = 0,
    var pinned: Boolean? = false,
    /**
     * Banner slot 1..10 on the pin-management page. Null = not in the board.
     * Homepage top-banner uses [pinned] + this order.
     */
    var pinOrder: Int? = null,
    /**
     * If enabled, FE should hide the sessions/products section ("Suất diễn/Sản phẩm").
     * Defaults to false for backward compatibility with existing documents.
     */
    var hideSessions: Boolean? = false,
    val soldTicket: Int = 0,

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)

data class ShowSessionDocument constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    @BsonRepresentation(BsonType.OBJECT_ID)
    var seatPlanId : String? = null,
    var edType: String?,
    val code: String? = null,
    val sessionName: String? = null,

    /**
     * Optional URL-friendly Session_Slug, unique within its parent Show.
     * An absent slug is represented as `null` (no value / empty / whitespace-only input is stored as null).
     * When present, the stored value is always the Canonical_Slug (lowercase [a-z0-9-], 1..200 chars,
     * no leading/trailing/consecutive hyphens). Uniqueness within the parent Show is enforced at the
     * application level (sessions are embedded, not a separate collection).
     */
    var slug: String? = null,

    var status : ShowSessionStatus = ShowSessionStatus.Created,
    /**
     * increase it every have a request generate unique id
     * It uses for generating order id in format <show code>|<session code>|<uniqueIncrement>
     */
    val uniqueIncrement: Long = 0,
    val saleMode: SaleMode? = SaleMode.Auto,
    val requireDelivery: Boolean = false,
    var startTime: Date,
    var endTime: Date,
    var venue : String,
    var city: CityDocument?,
    var ledgendImageUrl: String? = null,
    var eventSquareImageUrl: String? = null,
    var eventNameImageUrl: String? = null,
    /** Optional HTML shown as "Thông tin bổ sung" on the booking seat-map page. */
    var extraInfo: String? = null,
    var orderNote: String? = null,
    var paymentNote: String? = null,
    var paymentOffline: Boolean? = false,
    var isFree: Boolean? = false,
    val showInHome: Boolean? = false,
    var pgAccountCode: String? = null,
    var paymentMethods: List<PaymentType>? = null,
    var mailPluginTemplate: List<MailPluginTemplate> = emptyList(),

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

    /** null = SINGLE (session tạo trước khi có vé theo lịch). */
    var sessionType: SessionType? = null,

    /**
     * Chỉ có khi sessionType = CALENDAR. startTime/endTime khi đó là 00:00 ngày đầu và 23:59 ngày cuối của lịch,
     * giá/quota từng ngày nằm ở service.ticket.
     */
    var calendar: SessionCalendarConfig? = null,

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)

/** Giờ dạng "HH:mm" theo giờ Việt Nam, xem [metaticket.show.datastructure.CalendarRules]. */
data class SessionCalendarConfig(
    val openTime: String,
    val closeTime: String,
    val bookingWindowDays: Int,
    /** Ngừng bán vé cho hôm nay trước giờ đóng cửa X phút; null = không bán vé trong ngày. */
    val sameDayCutoffMinutes: Int? = null,
)

data class EventCategory(
    @BsonId @BsonRepresentation(BsonType.STRING)
    val id: String,
    val name: String
)
