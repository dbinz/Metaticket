package metaticket.accessory.pojo.db

import kotlin.math.max

/**
 * Collect all validation-rule violations for a single [AccessoryOrderLine] without throwing.
 *
 * Rules (design "AccessoryOrder → Validation Rules"):
 * - `quantity > 0`.
 * - `unitPrice >= 0`.
 * - `variantKey`/`variantLabel` are paired: both null or both non-null. (The "iff the referenced
 *   accessory has variants" half requires catalog context and is enforced at checkout.)
 */
fun AccessoryOrderLine.validationErrors(index: Int): List<String> {
    val errors = mutableListOf<String>()
    if (quantity <= 0) errors += "line[$index] `quantity` must be > 0 (was $quantity)"
    if (unitPrice < 0) errors += "line[$index] `unitPrice` must be >= 0 (was $unitPrice)"

    val hasKey = variantKey != null
    val hasLabel = variantLabel != null
    if (hasKey != hasLabel) {
        errors += "line[$index] `variantKey` and `variantLabel` must both be null or both be non-null " +
            "(variantKey=${variantKey}, variantLabel=${variantLabel})"
    }
    return errors
}

/**
 * Collect all validation-rule violations for an accessory order without throwing.
 *
 * Rules (design "AccessoryOrder → Validation Rules"):
 * - `code` non-blank (uniqueness enforced by the DB unique index).
 * - `lines` non-empty; every line valid (see [AccessoryOrderLine.validationErrors]).
 * - Fulfillment-mode invariants (Delivery needs address; MetaTicketPickup needs pickup snapshot;
 *   unused mode fields must be absent).
 * - `subtotal == Σ line.unitPrice * line.quantity`.
 * - `total == max(0, subtotal - (discount?.amount ?: 0))`.
 */
fun AccessoryOrderDocument.validationErrors(): List<String> {
    val errors = mutableListOf<String>()

    if (code.isBlank()) errors += "order `code` must be non-blank"

    errors += fulfillmentFieldErrors()

    if (lines.isEmpty()) {
        errors += "order `lines` must be non-empty"
    } else {
        lines.forEachIndexed { index, line -> errors += line.validationErrors(index) }
    }

    val expectedSubtotal = lines.sumOf { it.unitPrice * it.quantity }
    if (subtotal != expectedSubtotal) {
        errors += "order `subtotal` ($subtotal) must equal Σ line.unitPrice*quantity ($expectedSubtotal)"
    }

    val discountAmount = discount?.amount ?: 0L
    val expectedTotal = max(0L, subtotal - discountAmount)
    if (total != expectedTotal) {
        errors += "order `total` ($total) must equal max(0, subtotal - discount) ($expectedTotal)"
    }

    return errors
}

/** Mode-specific field presence rules for [AccessoryOrderDocument]. */
private fun AccessoryOrderDocument.fulfillmentFieldErrors(): List<String> {
    val errors = mutableListOf<String>()
    when (fulfillment) {
        FulfillmentType.PickupAtShow -> {
            if (deliveryAddress != null) {
                errors += "order `deliveryAddress` must be null when fulfillment is PickupAtShow"
            }
            if (pickupPointId != null || pickupPointName != null || pickupPointAddress != null) {
                errors += "order pickup-point fields must be null when fulfillment is PickupAtShow"
            }
        }
        FulfillmentType.MetaTicketPickup -> {
            if (pickupPointId.isNullOrBlank()) {
                errors += "order `pickupPointId` is required when fulfillment is MetaTicketPickup"
            }
            if (pickupPointName.isNullOrBlank()) {
                errors += "order `pickupPointName` is required when fulfillment is MetaTicketPickup"
            }
            if (pickupPointAddress.isNullOrBlank()) {
                errors += "order `pickupPointAddress` is required when fulfillment is MetaTicketPickup"
            }
            if (deliveryAddress != null) {
                errors += "order `deliveryAddress` must be null when fulfillment is MetaTicketPickup"
            }
            if (showSessionId != null || showSessionLabel != null) {
                errors += "order show-session fields must be null when fulfillment is MetaTicketPickup"
            }
        }
        FulfillmentType.Delivery -> {
            val addr = deliveryAddress
            if (addr == null) {
                errors += "order `deliveryAddress` is required when fulfillment is Delivery"
            } else {
                if (addr.address.isBlank()) errors += "order `deliveryAddress.address` must be non-blank"
                if (addr.ward.isBlank()) errors += "order `deliveryAddress.ward` must be non-blank"
                if (addr.city.isBlank()) errors += "order `deliveryAddress.city` must be non-blank"
            }
            if (pickupPointId != null || pickupPointName != null || pickupPointAddress != null) {
                errors += "order pickup-point fields must be null when fulfillment is Delivery"
            }
            if (showSessionId != null || showSessionLabel != null) {
                errors += "order show-session fields must be null when fulfillment is Delivery"
            }
        }
    }
    return errors
}

/** Validate an accessory order, throwing [AccessoryValidationException] on any rule violation. */
fun AccessoryOrderDocument.validate(): AccessoryOrderDocument {
    val errors = validationErrors()
    if (errors.isNotEmpty()) throw AccessoryValidationException(errors)
    return this
}
