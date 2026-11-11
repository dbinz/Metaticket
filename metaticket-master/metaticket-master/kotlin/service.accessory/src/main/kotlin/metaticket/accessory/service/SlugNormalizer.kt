package metaticket.accessory.service

import java.text.Normalizer

/**
 * Converts arbitrary input text into a Canonical_Slug (same rules as `service.show`).
 *
 * A Canonical_Slug uses only lowercase ASCII letters, digits, and single hyphens; it does not
 * start/end with a hyphen and has no consecutive hyphens. [normalize] is total and idempotent.
 */
object SlugNormalizer {

    /** Maximum allowed length of a Canonical_Slug, in characters. */
    const val MAX_LENGTH = 200

    /** Matches a maximal run of characters that are NOT lowercase ASCII letters or digits. */
    private val DISALLOWED_RUN = Regex("[^a-z0-9]+")

    /**
     * Returns the Canonical_Slug for [input] (may be empty when input has no slug-worthy content).
     */
    fun normalize(input: String): String {
        val lowered = input.lowercase()
        val deAccented = stripDiacritics(lowered)
        val hyphenated = DISALLOWED_RUN.replace(deAccented, "-")
        return hyphenated.trim('-')
    }

    /** Strips combining diacritical marks; maps Vietnamese `đ` → `d` before NFD. */
    private fun stripDiacritics(value: String): String {
        val withoutSpecialLetters = value.replace('\u0111', 'd')
        val decomposed = Normalizer.normalize(withoutSpecialLetters, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }
}
