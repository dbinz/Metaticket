package meta.metasearch.dtos

import com.fasterxml.jackson.annotation.JsonInclude
import meta.metasearch.models.CalendarSessions
import meta.metasearch.models.Show
import meta.metasearch.models.ShowSession
import kotlin.math.abs

/**
 * Card payload for [GET /public/metasearch/shows/highlight].
 * Detail-only fields (description, logos, full sessions, ranking, …) stay on the show-detail API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HighlightShowItem(
    val id: String,
    val showName: String,
    val slug: String,
    val status: String,
    val eventCategory: String,
    val thumbnailUrl: String,
    val bannerImageUrl: String?,
    val logoShowUrl: String?,
    val startTime: Long,
    val venue: String?,
    val cityName: String?,
    /**
     * Lowest ticket base price (VND) for the "Từ …đ" card label. Always present: null means no Open/Paused
     * session has a priced ticket and the card shows no price (overrides the class-level NON_NULL).
     */
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val minPrice: Long?,
    /** Last date of the card's session when it is a running calendar session ("Đang diễn ra · đến …"); omitted otherwise. */
    val ongoingUntil: Long? = null,
) {
    companion object {
        /** Sale-state rollup: first match among sessions wins. */
        private val STATUS_PRIORITY = listOf("Open", "Paused", "OpeningWait")

        /** Maps an indexed show onto a card; date/place come from the nearest home-visible session. */
        fun from(show: Show): HighlightShowItem {
            val now = System.currentTimeMillis()
            val session = pickCardSession(show.sessions, now)
            return HighlightShowItem(
                id = show.id,
                showName = show.showName,
                slug = show.slug,
                status = rollupShowStatus(show.sessions),
                eventCategory = show.eventCategory,
                thumbnailUrl = show.thumbnailUrl,
                bannerImageUrl = show.bannerImageUrl,
                logoShowUrl = show.logoShowUrl,
                startTime = session?.let { CalendarSessions.displayStartTime(it, now) } ?: show.startTime,
                venue = session?.venue?.takeIf { it.isNotBlank() },
                cityName = session?.cityName?.takeIf { it.isNotBlank() },
                minPrice = show.minPrice,
                ongoingUntil = session?.takeIf { CalendarSessions.isRunning(it, now) }?.endTime,
            )
        }

        /** Show badge = Open if any session is Open, else Paused, else OpeningWait; otherwise no sale state. */
        private fun rollupShowStatus(sessions: List<ShowSession>): String {
            val present = sessions.map { it.status }.toHashSet()
            return STATUS_PRIORITY.firstOrNull { it in present }.orEmpty()
        }

        /** Prefer a showInHome session closest to now; otherwise any nearest session. A running calendar session counts as now. */
        private fun pickCardSession(sessions: List<ShowSession>, now: Long): ShowSession? {
            val homeVisible = sessions.filter { it.showInHome }
            val pool = homeVisible.ifEmpty { sessions }
            return pool.minByOrNull { abs(CalendarSessions.displayStartTime(it, now) - now) }
        }
    }
}
