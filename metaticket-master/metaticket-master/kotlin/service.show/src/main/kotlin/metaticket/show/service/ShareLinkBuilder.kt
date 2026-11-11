package metaticket.show.service

/**
 * Builds absolute, public-facing homepage share links for shows and sessions.
 *
 * This component is **pure**: it performs no I/O, holds no state, references no framework types,
 * and is **total** — every method returns a value for any string input and never throws.
 *
 * Two link shapes exist for each entity, mirroring the existing Home_Frontend routes:
 *   - Detail (slug):  `/s/{showSlug}`               Detail (id):  `/show/{showId}`
 *   - Booking (slug): `/s/{showSlug}/{sessionSlug}` Booking (id): `/dat-ve?show={showId}&session={sessionId}`
 *
 * The slug form is preferred only when the relevant slug(s) are **present** (non-null and non-blank
 * after trimming). When a slug is present, its stored canonical value is inserted **byte-for-byte**
 * with no URL re-encoding, trimming, or case change — slugs are normalized to canonical form at
 * write time by the `show-session-slug-links` feature.
 */
object ShareLinkBuilder {

    /**
     * Joins [baseUrl] and an already-built [path] so that exactly one slash separates the origin
     * from the first path segment.
     *
     * [path] is expected to begin with a leading `/`. A single trailing slash on [baseUrl] is
     * collapsed so the result never contains a doubled `//` separator and never omits the slash.
     */
    fun join(baseUrl: String, path: String): String {
        val trimmedBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        return trimmedBase + path
    }

    /**
     * Returns the Show_Detail_Page_Link for the given show.
     *
     * Produces `/s/{showSlug}` when [showSlug] is present (non-null, non-blank), otherwise
     * `/show/{showId}`. The chosen path is joined to [baseUrl] with a single separator slash.
     */
    fun showDetailLink(baseUrl: String, showId: String, showSlug: String?): String {
        val path = if (isPresent(showSlug)) "/s/$showSlug" else "/show/$showId"
        return join(baseUrl, path)
    }

    /**
     * Returns the Booking_Page_Link for the given session.
     *
     * Produces `/s/{showSlug}/{sessionSlug}` when **both** [showSlug] and [sessionSlug] are present
     * (non-null, non-blank), otherwise `/dat-ve?show={showId}&session={sessionId}`. The chosen path
     * is joined to [baseUrl] with a single separator slash.
     */
    fun bookingLink(
        baseUrl: String,
        showId: String,
        showSlug: String?,
        sessionId: String,
        sessionSlug: String?,
    ): String {
        val path = if (isPresent(showSlug) && isPresent(sessionSlug)) {
            "/s/$showSlug/$sessionSlug"
        } else {
            "/dat-ve?show=$showId&session=$sessionId"
        }
        return join(baseUrl, path)
    }

    /** A slug is "present" when it is non-null and contains non-whitespace content. */
    private fun isPresent(slug: String?): Boolean = !slug.isNullOrBlank()
}
