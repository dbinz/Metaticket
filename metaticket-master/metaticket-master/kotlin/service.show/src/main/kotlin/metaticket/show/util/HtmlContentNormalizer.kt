package metaticket.show.util

/**
 * Normalizes optional HTML / rich-text fields before persistence (show description, ticket
 * description, policy, session extraInfo, …).
 *
 * Does **not** convert newlines / whitespace into `<br>` — only the content the operator typed is kept.
 * Blank or whitespace-only payloads (including `&nbsp;`-only) become `null` so the UI can hide empty blocks.
 */
object HtmlContentNormalizer {
    /** Returns trimmed content, or null when there is no real content to store. */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // Whitespace-only after expanding common non-breaking spaces — still empty content.
        val collapsed = trimmed
            .replace("\u00a0", " ")
            .replace("&nbsp;", " ", ignoreCase = true)
            .trim()
        if (collapsed.isEmpty()) return null
        return trimmed
    }
}
