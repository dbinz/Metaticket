import removeAccents from 'remove-accents';

/**
 * Client-side mirror of the Show_Service `SlugNormalizer` / `SlugValidator`.
 *
 * These helpers exist so the Admin_Portal can pre-fill sensible slug defaults and
 * give organizers immediate format/uniqueness feedback. The Show_Service remains the
 * authority: every slug submitted by the portal is independently re-normalized and
 * re-validated server-side. Stored slugs are always in their `Canonical_Slug` form.
 *
 * A `Canonical_Slug` (Slug_Format_Rule):
 *  - contains only lowercase ASCII letters (`a`-`z`), digits (`0`-`9`) and single hyphens (`-`)
 *  - does not start or end with a hyphen
 *  - contains no consecutive hyphens
 *  - has a length between 1 and {@link MAX_SLUG_LENGTH} characters inclusive
 */

/** Maximum allowed length of a canonical slug (inclusive). */
export const MAX_SLUG_LENGTH = 200;

/** Matches a non-empty, fully canonical slug. */
const SLUG_FORMAT_REGEX = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/** Matches any maximal run of characters that are not lowercase ASCII letters or digits. */
const DISALLOWED_RUN_REGEX = /[^a-z0-9]+/g;

/** Leading/trailing hyphens left over after run replacement. */
const EDGE_HYPHEN_REGEX = /^-+|-+$/g;

/** Reasons a non-absent slug can fail validation. Mirrors the server `SlugValidator`. */
export enum SlugError {
    /** The value normalizes to an empty string (no slug-worthy content). */
    Empty = 'empty',
    /** The canonical value exceeds {@link MAX_SLUG_LENGTH} characters. */
    TooLong = 'too-long',
    /** The canonical value does not satisfy the Slug_Format_Rule. */
    Format = 'format',
}

/**
 * Convert arbitrary input text into a `Canonical_Slug`.
 *
 * Algorithm (mirrors the server `SlugNormalizer`):
 *  1. Strip diacritics and lowercase the input.
 *  2. Replace every maximal run of disallowed characters or whitespace with a single hyphen.
 *  3. Trim leading/trailing hyphens (runs are already collapsed by step 2).
 *
 * The result contains only `[a-z0-9-]` with no leading, trailing, or consecutive hyphens.
 * It is total (never throws) and idempotent: `normalizeSlug(normalizeSlug(x)) === normalizeSlug(x)`.
 * May return `''` when the input has no slug-worthy content.
 */
export function normalizeSlug(input: string | null | undefined): string {
    if (input == null) {
        return '';
    }

    const lowered = removeAccents(input).toLowerCase();
    // Each run of disallowed chars/whitespace collapses to a single hyphen, then trim edges.
    return lowered.replace(DISALLOWED_RUN_REGEX, '-').replace(EDGE_HYPHEN_REGEX, '');
}

/**
 * Validate a raw slug value the way the server `SlugValidator.checkFormat` does.
 *
 * @returns `null` when the slug is valid or absent (no value / empty / whitespace-only);
 *          otherwise the {@link SlugError} describing the violation.
 */
export function isValidSlug(raw: string | null | undefined): SlugError | null {
    // Absent slug (no value, empty, or whitespace-only) is treated as valid and skipped.
    if (raw == null || raw.trim().length === 0) {
        return null;
    }

    // Validate the value AS ENTERED against the Slug_Format_Rule instead of its normalized form, so
    // non-canonical input (uppercase, spaces, accents, disallowed characters, or leading/trailing/
    // consecutive hyphens) is reported as invalid rather than being silently rewritten.
    const value = raw.trim();

    if (value.length > MAX_SLUG_LENGTH) {
        return SlugError.TooLong;
    }
    if (!SLUG_FORMAT_REGEX.test(value)) {
        return SlugError.Format;
    }
    return null;
}

/**
 * Generate a default canonical slug from `source`, guaranteeing uniqueness against `taken`.
 *
 * Returns `normalizeSlug(source)` when that canonical value is unused. When it is already
 * taken, appends the smallest suffix from the ascending sequence `-2`, `-3`, `-4`, … that
 * yields an unused, format-valid slug. Returns `''` when the canonical form is empty.
 *
 * @param source the text to derive the slug from (e.g. show name or session code)
 * @param taken  the set of canonical slugs already in use within the uniqueness scope
 */
export function generateDefaultSlug(source: string | null | undefined, taken: Set<string>): string {
    const canonical = normalizeSlug(source);
    if (canonical.length === 0) {
        return '';
    }
    if (!taken.has(canonical)) {
        return canonical;
    }

    for (let suffix = 2; ; suffix++) {
        const candidate = `${canonical}-${suffix}`;
        if (candidate.length > MAX_SLUG_LENGTH) {
            // Longer suffixes can only get longer, so no valid unique candidate exists.
            return canonical;
        }
        if (!taken.has(candidate)) {
            return candidate;
        }
    }
}
