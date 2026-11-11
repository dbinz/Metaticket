package metaticket.accessory.service

/** Result of [SlugValidator.checkFormat]. */
sealed interface SlugCheck {
    /** Acceptable: absent (skipped) or a valid Canonical_Slug. */
    object Valid : SlugCheck

    /** Not acceptable (`empty` / `too-long` / `format`). */
    data class Invalid(val reason: String) : SlugCheck
}

/**
 * Format authority for accessory-show slugs (mirrors `service.show` SlugValidator).
 * Absent slugs are valid/skipped; non-absent values are checked as submitted (not silently rewritten).
 */
object SlugValidator {

    private val SLUG_FORMAT = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    /**
     * Checks [rawSlug]: blank → Valid; too long / bad format → Invalid; else Valid.
     */
    fun checkFormat(rawSlug: String?): SlugCheck {
        if (rawSlug.isNullOrBlank()) return SlugCheck.Valid
        val value = rawSlug.trim()
        if (value.length > SlugNormalizer.MAX_LENGTH) return SlugCheck.Invalid("too-long")
        if (!SLUG_FORMAT.matches(value)) return SlugCheck.Invalid("format")
        return SlugCheck.Valid
    }
}
