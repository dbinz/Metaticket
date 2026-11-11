package metaticket.show.service

import java.text.Normalizer

/**
 * Converts arbitrary input text into a Canonical_Slug.
 *
 * A Canonical_Slug consists only of lowercase ASCII letters (`a`-`z`), digits (`0`-`9`), and single
 * hyphens (`-`); it does not start or end with a hyphen and it contains no consecutive hyphens
 * (the Slug_Format_Rule). [normalize] may return the empty string when the input has no
 * slug-worthy content.
 *
 * The normalizer is **total** (defined for every input) and **idempotent**:
 *   - `normalize(normalize(x)) == normalize(x)` for any input `x`.
 *   - any value already satisfying the Slug_Format_Rule is returned unchanged.
 */
object SlugNormalizer {

    /** Maximum allowed length of a Canonical_Slug, in characters. */
    const val MAX_LENGTH = 200

    /** Matches a maximal run of characters that are NOT lowercase ASCII letters or digits. */
    private val DISALLOWED_RUN = Regex("[^a-z0-9]+")

    /**
     * Returns the Canonical_Slug for [input].
     *
     * Algorithm:
     * 1. Lowercase and Unicode-normalize (strip diacritics), so accented characters fold to their
     *    base ASCII letters (e.g. "Tiệc" -> "tiec").
     * 2. Replace every maximal run of disallowed characters or whitespace with a single hyphen `-`.
     * 3. Trim leading and trailing hyphens (collapsing of repeated hyphens is implicit because each
     *    run becomes a single hyphen).
     *
     * The result contains only `[a-z0-9-]`, with no leading, trailing, or consecutive hyphens, or is
     * the empty string when the input has no slug-worthy content.
     */
    fun normalize(input: String): String {
        // 1. Lowercase, then strip diacritics via Unicode NFD decomposition.
        val lowered = input.lowercase()
        val deAccented = stripDiacritics(lowered)

        // 2. Replace each run of disallowed/whitespace characters with a single hyphen.
        val hyphenated = DISALLOWED_RUN.replace(deAccented, "-")

        // 3. Trim leading/trailing hyphens. Internal runs are already collapsed to single hyphens.
        return hyphenated.trim('-')
    }

    /**
     * Strips combining diacritical marks by decomposing characters (NFD) and dropping the marks.
     * Handles the Vietnamese characters `đ`/`Đ`, which do not decompose under NFD, by mapping them
     * to `d` before normalization.
     */
    private fun stripDiacritics(value: String): String {
        val withoutSpecialLetters = value.replace('\u0111', 'd') // 'đ' (lowercase, already lowercased input)
        val decomposed = Normalizer.normalize(withoutSpecialLetters, Normalizer.Form.NFD)
        // Remove all combining marks (Unicode category "Mn" - nonspacing marks).
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }
}
