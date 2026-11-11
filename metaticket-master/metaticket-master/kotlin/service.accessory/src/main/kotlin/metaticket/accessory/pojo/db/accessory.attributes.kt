package metaticket.accessory.pojo.db

/** Stable key for the first product attribute slot (values on [AccessoryVariant.attr1]). */
const val ATTR1_KEY: String = "attr1"

/** Stable key for the second product attribute slot (values on [AccessoryVariant.attr2]). */
const val ATTR2_KEY: String = "attr2"

/** One named attribute dimension on a product (max 2 per product). */
data class AccessoryAttributeDef(
    val key: String,
    val name: String,
)

/** True when any variant carries a non-blank [AccessoryVariant.attr1] value. */
fun AccessoryDocument.hasAttr1Values(): Boolean =
    variants.any { !it.attr1.isNullOrBlank() }

/** True when any variant carries a non-blank [AccessoryVariant.attr2] value. */
fun AccessoryDocument.hasAttr2Values(): Boolean =
    variants.any { !it.attr2.isNullOrBlank() }

/**
 * Resolved attribute definitions for API / storefront (0–2 entries).
 * When values exist without a custom name, falls back to "Thuộc tính 1/2".
 */
fun AccessoryDocument.resolvedAttributes(): List<AccessoryAttributeDef> {
    val defs = mutableListOf<AccessoryAttributeDef>()
    if (hasAttr1Values()) {
        defs += AccessoryAttributeDef(
            key = ATTR1_KEY,
            name = attr1Name?.trim()?.takeIf { it.isNotEmpty() } ?: "Thuộc tính 1",
        )
    }
    if (hasAttr2Values()) {
        defs += AccessoryAttributeDef(
            key = ATTR2_KEY,
            name = attr2Name?.trim()?.takeIf { it.isNotEmpty() } ?: "Thuộc tính 2",
        )
    }
    return defs
}

/**
 * Normalize optional attribute display names for persistence.
 * Clears a name when its slot has no values; trims blanks to null.
 */
fun normalizeAttributeNames(
    attr1Name: String?,
    attr2Name: String?,
    variants: List<AccessoryVariant>,
): Pair<String?, String?> {
    val has1 = variants.any { !it.attr1.isNullOrBlank() }
    val has2 = variants.any { !it.attr2.isNullOrBlank() }
    val n1 = if (has1) attr1Name?.trim()?.takeIf { it.isNotEmpty() } else null
    val n2 = if (has2) attr2Name?.trim()?.takeIf { it.isNotEmpty() } else null
    return n1 to n2
}

/** Options map for one variant (`attr1` / `attr2` → value), empty when neither set. */
fun AccessoryVariant.optionMap(): Map<String, String> {
    val out = linkedMapOf<String, String>()
    attr1?.trim()?.takeIf { it.isNotEmpty() }?.let { out[ATTR1_KEY] = it }
    attr2?.trim()?.takeIf { it.isNotEmpty() }?.let { out[ATTR2_KEY] = it }
    return out
}
