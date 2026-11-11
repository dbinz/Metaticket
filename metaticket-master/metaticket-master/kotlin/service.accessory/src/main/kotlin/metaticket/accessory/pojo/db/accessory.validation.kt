package metaticket.accessory.pojo.db

/**
 * Sentinel value for "unlimited" stock. A stock cell holding [UNLIMITED_STOCK] is never decremented
 * and never blocks a checkout line.
 */
const val UNLIMITED_STOCK: Int = -1

/**
 * Lowest legal value for any stock cell: `-1` (unlimited) or any non-negative count.
 */
private const val MIN_STOCK: Int = UNLIMITED_STOCK

/**
 * Raised when an [AccessoryShowDocument] or [AccessoryDocument] violates its validation rules.
 * Carries the full list of human-readable rule violations.
 */
class AccessoryValidationException(val errors: List<String>) :
    IllegalArgumentException("accessory validation failed: ${errors.joinToString("; ")}")

/**
 * Collect all validation-rule violations for an accessory show without throwing.
 *
 * Rules (design "AccessoryShow → Validation Rules"):
 * - `code` non-blank.
 * - `codePrefix` non-blank (drives order-code generation).
 * - `name` non-blank.
 */
fun AccessoryShowDocument.validationErrors(): List<String> {
    val errors = mutableListOf<String>()
    if (code.isBlank()) errors += "accessory show `code` must be non-blank"
    if (codePrefix.isBlank()) errors += "accessory show `codePrefix` must be non-blank"
    if (name.isBlank()) errors += "accessory show `name` must be non-blank"
    return errors
}

/** Validate an accessory show, throwing [AccessoryValidationException] on any rule violation. */
fun AccessoryShowDocument.validate(): AccessoryShowDocument {
    val errors = validationErrors()
    if (errors.isNotEmpty()) throw AccessoryValidationException(errors)
    return this
}

/**
 * Collect all validation-rule violations for an accessory without throwing.
 *
 * Rules (design "Accessory → Validation Rules"):
 * - `price > 0`.
 * - `sku` non-blank; `name` non-blank.
 * - Single-SKU (`variants` empty): top-level `stock >= -1`.
 * - Variant (`variants` non-empty): every `variant.key` is non-blank and unique within the
 *   accessory; every `variant.stock >= -1`; every `variant.imageUrl` is non-blank.
 */
fun AccessoryDocument.validationErrors(): List<String> {
    val errors = mutableListOf<String>()
    if (sku.isBlank()) errors += "accessory `sku` must be non-blank"
    if (name.isBlank()) errors += "accessory `name` must be non-blank"
    if (price <= 0) errors += "accessory `price` must be > 0 (was $price)"

    if (variants.isEmpty()) {
        // Single-SKU: only the top-level stock is meaningful.
        if (stock < MIN_STOCK) errors += "accessory `stock` must be >= $MIN_STOCK (was $stock)"
    } else {
        // Variant product: validate every variant and enforce non-blank uniqueness of keys.
        val seenKeys = mutableSetOf<String>()
        variants.forEachIndexed { index, variant ->
            if (variant.key.isBlank()) {
                errors += "variant[$index] `key` must be non-blank"
            } else if (!seenKeys.add(variant.key)) {
                errors += "variant `key` must be unique within the accessory (duplicate: '${variant.key}')"
            }
            if (variant.stock < MIN_STOCK) {
                errors += "variant '${variant.key}' `stock` must be >= $MIN_STOCK (was ${variant.stock})"
            }
            if (variant.imageUrl.isNullOrBlank()) {
                errors += "variant '${variant.key.ifBlank { "#$index" }}' `imageUrl` is required"
            }
        }
    }
    validatePurchaseLimits(this)?.let { errors += it }
    return errors
}

/**
 * Cross-field checks for optional purchase quantity limits on a product.
 * Null means unlimited for that dimension. When set, values must be `>= 1` and mutually consistent.
 */
fun validatePurchaseLimits(doc: AccessoryDocument): List<String>? {
    val errors = mutableListOf<String>()
    val min = doc.minOrderQty
    val max = doc.maxOrderQty
    val perUser = doc.maxPerUserQty
    if (min != null && min < 1) errors += "`minOrderQty` must be >= 1 when set (was $min)"
    if (max != null && max < 1) errors += "`maxOrderQty` must be >= 1 when set (was $max)"
    if (perUser != null && perUser < 1) errors += "`maxPerUserQty` must be >= 1 when set (was $perUser)"
    if (min != null && max != null && min > max) {
        errors += "`minOrderQty` ($min) must be <= `maxOrderQty` ($max)"
    }
    if (max != null && perUser != null && max > perUser) {
        errors += "`maxOrderQty` ($max) must be <= `maxPerUserQty` ($perUser)"
    }
    if (min != null && perUser != null && min > perUser) {
        errors += "`minOrderQty` ($min) must be <= `maxPerUserQty` ($perUser)"
    }
    return errors.takeIf { it.isNotEmpty() }
}

/** Validate an accessory, throwing [AccessoryValidationException] on any rule violation. */
fun AccessoryDocument.validate(): AccessoryDocument {
    val errors = validationErrors()
    if (errors.isNotEmpty()) throw AccessoryValidationException(errors)
    return this
}
