package meta.metasearch.dtos

import meta.metasearch.models.ShowSession

data class CreateOrUpdateShowRequest(
    var id: String = "",
    var showName: String = "",
    var code: String = "",
    var status: String = "",
    var eventCategoryCode: String = "",
    var eventCategory: String = "",
    var detailImageUrl: String? = null,
    var thumbnailUrl: String,
    var bannerImageUrl: String? = null,
    var logoHostUrl: String? = null,
    var logoShowUrl: String? = null,
    var createdAt: Long,
    var description: String? = null,
    var ranking: Int = 0,
    var pinned: Boolean = false,
    var pinOrder: Int? = null,
    var soldTicket: Long = 0,
    var slug: String = "",
    /** Lowest ticket base price (VND); null when service.show has no priced ticket to report. */
    var minPrice: Long? = null,
    var sessions: List<ShowSession> = listOf()
)

