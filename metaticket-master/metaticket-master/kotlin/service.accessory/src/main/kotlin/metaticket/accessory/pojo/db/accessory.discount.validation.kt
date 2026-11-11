package metaticket.accessory.pojo.db

/**
 * Collect all validation-rule violations for a [DiscountTier] without throwing.
 */
fun DiscountTier.validationErrors(index: Int): List<String> {
    val prefix = "discount tier[$index]"
    val errors = mutableListOf<String>()

    if (minOrderAmount < 0L) {
        errors += "$prefix `minOrderAmount` must be >= 0 (was $minOrderAmount)"
    }

    when (type) {
        DiscountType.Percentage ->
            if (value !in 1L..100L) {
                errors += "$prefix `value` must be in 1..100 for Percentage (was $value)"
            }
        DiscountType.FixedAmount ->
            if (value <= 0L) {
                errors += "$prefix `value` must be > 0 for FixedAmount (was $value)"
            }
        DiscountType.Tiered, DiscountType.EarlyBird ->
            errors += "$prefix `type` must be Percentage or FixedAmount (was $type)"
    }

    maxDiscountAmount?.let {
        if (it <= 0L) errors += "$prefix `maxDiscountAmount` must be > 0 when present (was $it)"
    }

    return errors
}

/**
 * Collect all validation-rule violations for a [DiscountCodeDocument] without throwing.
 */
fun DiscountCodeDocument.validationErrors(): List<String> {
    val errors = mutableListOf<String>()

    if (code.isBlank()) errors += "discount code `code` must be non-blank"

    when (type) {
        DiscountType.Percentage -> {
            if (value !in 1L..100L) {
                errors += "discount code `value` must be in 1..100 for Percentage (was $value)"
            }
            if (applyMode != DiscountApplyMode.Manual && applyMode != DiscountApplyMode.Auto) {
                errors += "discount code `applyMode` is invalid"
            }
        }
        DiscountType.FixedAmount -> {
            if (value <= 0L) {
                errors += "discount code `value` must be > 0 for FixedAmount (was $value)"
            }
        }
        DiscountType.EarlyBird -> {
            if (accessoryShowId.isNullOrBlank()) {
                errors += "discount code EarlyBird requires `accessoryShowId`"
            }
            if (startAt == null || endAt == null) {
                errors += "discount code EarlyBird requires both `startAt` and `endAt`"
            }
            when (rewardType) {
                DiscountType.Percentage ->
                    if (value !in 1L..100L) {
                        errors += "discount code EarlyBird `value` must be in 1..100 for Percentage reward (was $value)"
                    }
                DiscountType.FixedAmount ->
                    if (value <= 0L) {
                        errors += "discount code EarlyBird `value` must be > 0 for FixedAmount reward (was $value)"
                    }
                null -> errors += "discount code EarlyBird requires `rewardType` (Percentage|FixedAmount)"
                DiscountType.Tiered, DiscountType.EarlyBird ->
                    errors += "discount code EarlyBird `rewardType` must be Percentage or FixedAmount (was $rewardType)"
            }
        }
        DiscountType.Tiered -> {
            if (tiers.isEmpty()) {
                errors += "discount code Tiered requires at least one tier"
            }
            val thresholds = tiers.map { it.minOrderAmount }
            if (thresholds.size != thresholds.toSet().size) {
                errors += "discount code Tiered tiers must have unique `minOrderAmount` values"
            }
            tiers.forEachIndexed { i, tier -> errors += tier.validationErrors(i) }
        }
    }

    maxDiscountAmount?.let {
        if (it <= 0L) errors += "discount code `maxDiscountAmount` must be > 0 when present (was $it)"
    }

    if (minOrderAmount < 0L) {
        errors += "discount code `minOrderAmount` must be >= 0 (was $minOrderAmount)"
    }

    usageLimit?.let {
        if (it <= 0) errors += "discount code `usageLimit` must be > 0 when present (was $it)"
    }

    if (usedCount < 0) {
        errors += "discount code `usedCount` must be >= 0 (was $usedCount)"
    }

    if (startAt != null && endAt != null && startAt.after(endAt)) {
        errors += "discount code `startAt` ($startAt) must be <= `endAt` ($endAt)"
    }

    return errors
}

/** Validate a discount code, throwing [AccessoryValidationException] on any rule violation. */
fun DiscountCodeDocument.validate(): DiscountCodeDocument {
    val errors = validationErrors()
    if (errors.isNotEmpty()) throw AccessoryValidationException(errors)
    return this
}
