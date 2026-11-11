package metaticket.show.pojo.request

import metaticket.payment.datastructure.PaymentType
import metaticket.show.datastructure.MailPluginTemplate
import metaticket.show.datastructure.SessionType
import metaticket.show.pojo.db.SaleMode
import metaticket.show.pojo.db.SessionCalendarConfig

data class CreateShowRequest constructor(
    var code: String,
    var showName: String,
    var bannerImage: String?,
    var detailImageUrl: String?,
    var thumbnailUrl: String?,
    var logoShowImage: String?,
    var logoHostImage: String?,
    var website: String?,
    var ticketDescription: String?,
    var description: String?,
    var policy: String?,
    var eventCategoryCode: String?,
    var eventCategory: String?,
    var faq: String?,
    var pinned: Boolean?,
    var hideSessions: Boolean?,
    // Optional Show_Slug; absent (null) when not provided. Stored value is always canonical.
    var slug: String? = null,
)

data class UpdateShowRequest constructor(
    var id: String,
    var code: String,
    var showName: String?,
    var bannerImage: String?,
    var detailImageUrl: String?,
    var thumbnailUrl: String?,
    var logoShowImage: String?,
    var logoHostImage: String?,
    var eventCategoryCode: String?,
    var eventCategory: String?,
    var website: String?,
    var ticketDescription: String?,
    var description: String?,
    var hostLogoUrl: String?,
    var policy: String?,
    var faq: String?,
    var logoHostUrl: String?,
    var pinned: Boolean?,
    var hideSessions: Boolean?,
    // Optional Show_Slug; absent (null) when not provided. Stored value is always canonical.
    var slug: String? = null,
)

data class ShowSessionRequest (
    val showId: String,
    val sessionId: String,
)

data class ChangeSaleModeShowSessionRequest constructor(
    val showId: String,
    val sessionId: String,
    val saleMode: SaleMode,
)

data class UpdateShowSessionRequest constructor(
    var showId: String,
    var sessionId: String,
    var cityId: String,
    var sessionName : String?,
    var code : String,
    var address : String,
    val startTime: Long,
    val endTime: Long,
    var requireDelivery : Boolean,
    var orderNote: String?,
    var isFree: Boolean?,
    var showInHome: Boolean?,
    val ledgendImage: String?,
    val eventSquareImage: String?,
    val eventNameImage: String?,
    var manageTicket: Boolean?,
    var qrcodeEmail: Boolean?,
    // Optional Session_Slug; absent (null) when not provided. Stored value is always canonical.
    var slug: String? = null,
    // Loại session + cấu hình lịch; null = client cũ, giữ nguyên giá trị đang lưu.
    val sessionType: SessionType? = null,
    val calendar: SessionCalendarConfig? = null,
)

data class UpdateShowSessionPaymentRequest constructor(
    var showId: String,
    var sessionId: String,
    var paymentOffline : Boolean? = null,
    var paymentNote : String? = null,
    val pgAccountCode: String? = null,
    val paymentMethods: List<PaymentType>? = null
)

data class UpdateShowSessionSeatPlanRequest constructor(
    var showId: String,
    var sessionId: String,
    var edType : String,
    var seatPlanId : String? = null,
    /** Optional HTML for booking "Thông tin bổ sung"; blank/whitespace-only is cleared. */
    val extraInfo: String? = null,
)

data class UpdateShowSessionMailTemplateRequest constructor(
    var showId: String,
    var sessionId: String,
    var mailTemplates : List<MailPluginTemplate>,
)