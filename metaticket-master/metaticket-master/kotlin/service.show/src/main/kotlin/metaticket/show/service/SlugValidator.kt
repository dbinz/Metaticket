package metaticket.show.service

/**
 * The result of a slug format check.
 *
 * @see SlugValidator.checkFormat
 */
sealed interface SlugCheck {

    /** The slug is acceptable: it is either absent (skipped) or a valid Canonical_Slug. */
    object Valid : SlugCheck

    /**
     * The slug is not acceptable.
     *
     * @param reason a short machine-readable reason: `"empty"`, `"too-long"`, or `"format"`.
     */
    data class Invalid(val reason: String) : SlugCheck
}

/**
 * Decides whether a candidate slug is structurally valid.
 *
 * The validator is the format authority on the Show_Service. An absent slug (null, empty, or
 * whitespace-only) is treated as valid and skipped, mirroring the additive/optional nature of
 * slugs. Any non-absent value is checked AS SUBMITTED (trimmed) against the length bound and the
 * Slug_Format_Rule — it is no longer normalized first, so a non-canonical value is rejected rather
 * than silently rewritten.
 */
object SlugValidator {

    /**
     * Matches a Canonical_Slug that satisfies the Slug_Format_Rule: one or more groups of lowercase
     * ASCII letters/digits, separated by single hyphens, with no leading, trailing, or consecutive
     * hyphens.
     */
    private val SLUG_FORMAT = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

    /**
     * Checks the format of [rawSlug].
     *
     * - Absent ([rawSlug] is null, empty, or whitespace-only) → [SlugCheck.Valid] (skipped).
     * - Trimmed value is longer than [SlugNormalizer.MAX_LENGTH] → `Invalid("too-long")`.
     * - Trimmed value (as entered) does not satisfy the Slug_Format_Rule → `Invalid("format")`.
     * - Otherwise → [SlugCheck.Valid].
     */
    fun checkFormat(rawSlug: String?): SlugCheck {
        // Absent slug (null/empty/whitespace-only) is skipped.
        if (rawSlug.isNullOrBlank()) {
            return SlugCheck.Valid
        }

        // Validate the value AS SUBMITTED against the Slug_Format_Rule rather than its normalized
        // form. A non-canonical slug (uppercase, whitespace, accents, disallowed characters, or
        // leading/trailing/consecutive hyphens) is a proper-URL-path violation and is rejected with a
        // clear error instead of being silently rewritten by the normalizer.
        val value = rawSlug.trim()

        // Value is too long.
        if (value.length > SlugNormalizer.MAX_LENGTH) {
            return SlugCheck.Invalid("too-long")
        }

        // Value is not a valid Canonical_Slug as entered.
        if (!SLUG_FORMAT.matches(value)) {
            return SlugCheck.Invalid("format")
        }

        return SlugCheck.Valid
    }
}
