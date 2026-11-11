package metaticket.show.metasearch

import metaticket.payment.datastructure.PaymentType
import metaticket.show.datastructure.SessionType
import metaticket.show.datastructure.ShowSessionStatus


/**
 *
 * @author dongcatchay
 */

data class MetaSearchCreateUpdateShowRequest constructor(
    var id: String,
    var showName: String = "",
    var code: String,
    var status: ShowSessionStatus,
    var eventCategoryCode: String? = "",
    var eventCategory: String? = "",
    var bannerImageUrl: String? = null,
    var detailImageUrl: String? = null,
    var thumbnailUrl: String? = null,
    var logoHostUrl: String? = null,
    var logoShowUrl: String? = null,
    var description: String? = null,
    var createdAt: Long,
    var ranking: Int? = 0,
    var pinned: Boolean? = false,
    var pinOrder: Int? = null,
    val soldTicket: Long = 0,
    var slug: String = "",
    /**
     * Lowest ticket base price of the show (VND), backing the storefront "Từ …đ" card label.
     * Null when no session has a priced ticket (free shows, or tickets not created yet) — metasearch
     * then leaves the field unset and the card renders without a price.
     */
    var minPrice: Long? = null,
    var sessions: List<MetaSearchShowSession> = emptyList()
)

data class MetaSearchUpdateSoldRequest constructor(
    val id: String,
    val soldTicket: Long,
)

data class MetaSearchShowSession constructor(
    var id: String = "",
    val code: String? = null,
    var status: ShowSessionStatus,
    val sessionName: String? = null,
    var cityCode: String? = "",
    var cityName: String? = "",
    var isFree: Boolean = false,
    var showInHome: Boolean = false,
    var venue: String = "",
    var startTime: Long,
    var endTime: Long = 0,
    var paymentMethods: List<PaymentType> = emptyList(),
    var createdAt: Long,
    var slug: String = "",
    /** CALENDAR: bán vé theo ngày trong [startTime, endTime], metasearch coi là đang diễn ra tới endTime. */
    var sessionType: SessionType = SessionType.SINGLE,
)
