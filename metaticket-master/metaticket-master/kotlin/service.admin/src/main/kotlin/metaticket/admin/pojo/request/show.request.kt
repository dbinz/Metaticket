package metaticket.admin.pojo.request

import metaticket.order.pojo.db.CustomFieldTmpl
import metaticket.payment.datastructure.PaymentType
import metaticket.admin.pojo.db.SessionCalendarDraft
import metaticket.show.datastructure.MailPluginTemplate
import metaticket.show.datastructure.SessionType

data class CreateShowSessionDraftBasicInfoRequest constructor(
    val showId: String,
    val cityId: String,
    val sessionName: String?,
    val code: String,
    val address: String,
    val startTime: Long,
    val endTime: Long,
    val requireDelivery: Boolean,
    val orderNote: String?,
    val isFree: Boolean?,
    val showInHome: Boolean?,
    val ledgendImage: String?,
    val ledgendImageUrl: String?,
    val eventSquareImage: String?,
    val eventSquareImageUrl: String?,
    val eventNameImage: String?,
    val eventNameImageUrl: String?,
    val manageTicket: Boolean?,
    val qrcodeEmail: Boolean?,
    // Optional Session_Slug entered in the admin form; forwarded to the show service on promotion.
    val slug: String? = null,
    val sessionType: SessionType? = null,
    val calendar: SessionCalendarDraft? = null,
)

data class UpdateShowSessionDraftRequest constructor(
    val id: String,
    val cityId: String,
    val sessionName: String?,
    val code: String,
    val address: String,
    val startTime: Long,
    val endTime: Long,
    val requireDelivery: Boolean,
    val orderNote: String?,
    val isFree: Boolean?,
    val showInHome: Boolean?,
    val ledgendImage: String?,
    val ledgendImageUrl: String?,
    val eventSquareImage: String?,
    val eventSquareImageUrl: String?,
    val eventNameImage: String?,
    val eventNameImageUrl: String?,
    val manageTicket: Boolean?,
    val qrcodeEmail: Boolean?,
    // Optional Session_Slug entered in the admin form; forwarded to the show service on promotion.
    val slug: String? = null,
    val sessionType: SessionType? = null,
    val calendar: SessionCalendarDraft? = null,
)

data class UpdateShowSessionDraftPaymentRequest constructor(
    val id: String,
    var paymentOffline : Boolean? = null,
    var paymentNote : String? = null,
    val pgAccountCode: String? = null,
    val paymentMethods: List<PaymentType>? = null,
)

data class UpdateShowSessionDraftSeatPlanRequest constructor(
    val id: String,
    val edType: String,
    val seatPlanId: String?,
    val extraInfo: String? = null,
)

data class UpdateShowSessionDraftMailTemplatesRequest constructor(
    val id: String,
    val mailTemplates: List<MailPluginTemplate>,
)

data class CreateShowSessionDraftPromotionRequest constructor(
    val sessionDraftId: String,
    val data: String,
    val name: String,
)

data class UpdateShowSessionDraftPromotionRequest constructor(
    val id: String,
    val data: String,
    val name: String,
)

data class CreateShowSessionDraftOrderCustomFieldRequest constructor(
    val sessionDraftId: String,
    val customFields: List<CustomFieldTmpl>,
)

data class UpdateShowSessionDraftOrderCustomFieldRequest constructor(
    val id: String,
    val customFields: List<CustomFieldTmpl>,
)

data class CreateShowSessionRequestDraft constructor(
    val id: String,
)

data class DeleteShowSessionDraftRequest constructor(
    val id: String,
)
