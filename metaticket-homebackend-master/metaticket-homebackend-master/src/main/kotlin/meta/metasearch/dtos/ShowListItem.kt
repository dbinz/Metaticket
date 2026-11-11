package meta.metasearch.dtos

import meta.metasearch.models.CalendarSessions
import meta.metasearch.models.Show

/**
 * Lean, session-trimmed view of a [Show] returned by the public list/search endpoints.
 *
 * The full [meta.metasearch.models.ShowSession] carries ~11 fields each and the array is unbounded,
 * so returning it in list responses is a heap risk (see OOM review). List consumers (home grid,
 * slider, search dropdown) only need the show-level fields plus, per session, `startTime` and
 * `showInHome`. This DTO drops everything else.
 */
data class ShowListItem(
    val id: String,
    val showName: String,
    val code: String,
    val slug: String,
    val status: String,
    val eventCategoryCode: String,
    val eventCategory: String,
    val detailImageUrl: String?,
    val thumbnailUrl: String,
    val bannerImageUrl: String?,
    val logoHostUrl: String?,
    val logoShowUrl: String?,
    val createdAt: Long,
    val description: String?,
    val ranking: Int,
    val pinned: Boolean,
    val pinOrder: Int?,
    val soldTicket: Long,
    /** Lowest ticket base price (VND) for the "Từ …đ" card label; null when the show has none. */
    val minPrice: Long?,
    val showInHome: Boolean,
    val startTime: Long,
    val startYear: Int,
    val startMonth: Int,
    val startDay: Int,
    val hasAllSessionsStopped: Boolean,
    val sessions: List<SessionSummary>,
) {
    companion object {
        fun from(show: Show): ShowListItem = ShowListItem(
            id = show.id,
            showName = show.showName,
            code = show.code,
            slug = show.slug,
            status = show.status,
            eventCategoryCode = show.eventCategoryCode,
            eventCategory = show.eventCategory,
            detailImageUrl = show.detailImageUrl,
            thumbnailUrl = show.thumbnailUrl,
            bannerImageUrl = show.bannerImageUrl,
            logoHostUrl = show.logoHostUrl,
            logoShowUrl = show.logoShowUrl,
            createdAt = show.createdAt,
            description = show.description,
            ranking = show.ranking,
            pinned = show.pinned,
            pinOrder = show.pinOrder,
            soldTicket = show.soldTicket,
            minPrice = show.minPrice,
            showInHome = show.showInHome,
            startTime = show.startTime,
            startYear = show.startYear,
            startMonth = show.startMonth,
            startDay = show.startDay,
            hasAllSessionsStopped = show.hasAllSessionsStopped,
            sessions = System.currentTimeMillis().let { now ->
                show.sessions.map {
                    SessionSummary(CalendarSessions.displayStartTime(it, now), it.showInHome, it.endTime, it.sessionType)
                }
            },
        )
    }
}

/**
 * Minimal session projection: only the fields list views actually read. For a running calendar session startTime is
 * "now" (the card shows it as on-going) and endTime is the last date of its calendar.
 */
data class SessionSummary(
    val startTime: Long,
    val showInHome: Boolean,
    val endTime: Long = 0,
    val sessionType: String = "SINGLE",
)
