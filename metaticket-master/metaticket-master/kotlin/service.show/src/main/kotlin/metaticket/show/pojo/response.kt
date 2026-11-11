package metaticket.show.pojo

import com.fasterxml.jackson.annotation.JsonInclude
import metaticket.payment.datastructure.PaymentType
import metaticket.show.datastructure.MailPluginTemplate
import metaticket.show.datastructure.SessionType
import metaticket.show.datastructure.ShowSessionStatus
import metaticket.show.pojo.db.CityDocument
import metaticket.show.pojo.db.SessionCalendarConfig
import metaticket.show.pojo.db.ShowDocument
import metaticket.show.pojo.db.ShowSessionDocument


/**
 *
 * @author dongcatchay
 */

data class ShowSessionResponse constructor(
    var id: String,
    var venue : String = "",
    var seatPlanId : String?,
    var edType: String?,
    var ledgendImageUrl: String? = null,
    val eventSquareImageUrl: String? = null,
    val eventNameImageUrl: String? = null,
    val extraInfo: String? = null,
    var requireDelivery: Boolean,
    var paymentOffline: Boolean? = null,
    var mailPluginTemplate: List<MailPluginTemplate>,
    var paymentNote: String? = null,
    var orderNote: String? = null,
    val code: String = "",
    val sessionName: String? = null,
    val startTime: Long,
    val endTime: Long,
    val status: ShowSessionStatus,
    val city: CityDocument? = null,
    val isFree: Boolean? = null,
    val showInHome: Boolean? = false,
    val pgAccountCode: String? = null,
    val paymentMethods: List<PaymentType>? = null,
    val manageTicket: Boolean? = false,
    val qrcodeEmail: Boolean? = false,
    // Session_Slug; always present. Canonical slug when stored, or "" when absent.
    val slug: String = "",
    val sessionType: SessionType = SessionType.SINGLE,
    val calendar: SessionCalendarConfig? = null,
)

data class ShowResponse(
    val id: String,
    val sessions: List<ShowSessionResponse>,
    val showName: String,
    val code: String,
    val website: String?,
    val bannerImageUrl: String?,
    val detailImageUrl: String?,
    val thumbnailUrl: String?,
    val logoHostUrl: String,
    val logoShowUrl: String,
    val eventCategoryCode: String,
    val eventCategory: String,
    val faq: String?,
    val description: String?,
    val ticketDescription: String?,
    val policy: String?,
    val status: ShowSessionStatus,
    val shipPrice: Long,
    val pinned: Boolean,
    val hideSessions: Boolean,
    val createdAt: Long,
    // Show_Slug; always present. Canonical slug when stored, or "" when absent.
    val slug: String = "",
    // Identifier of the Session matched by a Session_Slug on the Fetch_By_Slug_Endpoint; omitted
    // otherwise. Mirrors the Home_Frontend `ShowBySlugResponse = ShowResponse & { matchedSessionId?: string }`.
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val matchedSessionId: String? = null,
)

/**
 * Response for the Fetch_By_Slug_Endpoint: a plain [ShowResponse] (so its `sessions` array — and each
 * session's `slug` — serializes exactly like the fetch-by-id response) carrying the optional
 * [ShowResponse.matchedSessionId]. A previous `@JsonUnwrapped` wrapper was removed because unwrapping
 * a nested object whose properties include the `sessions` collection is fragile and could drop the
 * session payload from the serialized response.
 */

fun ShowSessionDocument.toResponse(): ShowSessionResponse {
    return ShowSessionResponse(
        id = id,
        venue = venue,
        seatPlanId = seatPlanId,
        edType = edType,
        ledgendImageUrl = ledgendImageUrl ?: "",
        eventSquareImageUrl = eventSquareImageUrl ?: "",
        eventNameImageUrl = eventNameImageUrl ?: "",
        extraInfo = extraInfo ?: "",
        requireDelivery = requireDelivery,
        paymentOffline = paymentOffline ?: false,
        mailPluginTemplate = mailPluginTemplate ?: listOf(),
        paymentNote = paymentNote ?: "",
        orderNote = orderNote ?: "",
        code = code ?: "",
        sessionName = sessionName ?: "",
        startTime = startTime.time,
        endTime = endTime.time,
        status = status,
        city = city,
        isFree = isFree ?: false,
        showInHome = showInHome ?: false,
        pgAccountCode = pgAccountCode ?: "",
        paymentMethods = paymentMethods ?: listOf(),
        manageTicket = manageTicket ?: false,
        qrcodeEmail = qrcodeEmail ?: false,
        slug = slug ?: "",
        sessionType = sessionType ?: SessionType.SINGLE,
        calendar = calendar,
    )
}

fun ShowDocument.toResponse(): ShowResponse {
    return ShowResponse(
        id = this.id,
        sessions = this.sessions.map { it.toResponse() },
        showName = this.showName,
        code = this.code,
        website = this.website,
        bannerImageUrl = this.bannerImageUrl ?: "",
        detailImageUrl = this.detailImageUrl ?: "",
        thumbnailUrl = this.thumbnailUrl ?: "",
        logoHostUrl = this.logoHostUrl ?: "",
        logoShowUrl = this.logoShowUrl ?: "",
        eventCategoryCode = this.eventCategoryCode?:"",
        eventCategory = this.eventCategory?:"",
        faq = this.faq,
        description = this.description,
        ticketDescription = this.ticketDescription,
        policy = this.policy,
        status = this.status,
        shipPrice = this.shipPrice,
        pinned = this.pinned ?: false,
        hideSessions = this.hideSessions ?: false,
        createdAt = this.createdAt.time,
        slug = this.slug ?: "",
    )
}
