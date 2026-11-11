package metaticket.accessory.pojo.request

import metaticket.accessory.security.PublicId

/**
 * Checkout payload validation (design "Rate Limiting & Public Endpoint Protection → Payload
 * validation"; Req 5.3–5.5, 6.1, 6.2, 6.5, 6.14, 14.3, 14.4, 16.3, 16.4).
 *
 * This is the single, **pure/standalone** validation gate that runs **before** any database work on
 * `POST /accessory/checkout`. It collects *all* structural violations of the submitted
 * payload and reports them as structured [CheckoutValidationError]s so the controller (task 12.3)
 * can return a `400`/`422` naming exactly which constraint failed (Req 16.4) without creating an
 * order.
 *
 * Scope (task 6.1): payload-shape rules only —
 * - 1..50 lines (Req 6.1, 16.3),
 * - each `quantity` a whole number in 1..99 (Req 6.14, 16.3),
 * - each `accessoryId` a well-formed `Checksum`-encoded id that decodes to a valid `ObjectId`
 *   (Req 16.3),
 * - each line's variant selection *structurally* consistent (a supplied `variantKey` is non-blank),
 * - customer name 1..100 chars, phone 8..15 digits with an optional single leading `+`, email
 *   1..254 chars and well-formed (Req 5.1, 5.3, 5.4, 5.5, 16.3),
 * - rejection of legacy top-level shipping fields (`shipping*`); delivery uses nested
 *   `deliveryAddress` instead,
 * - fulfillment is one of `PickupAtShow` / `MetaTicketPickup` / `Delivery`, with structural
 *   checks for mode-specific fields (`pickupPointId`, `deliveryAddress.*`).
 *
 * **Out of scope here (belongs to `checkout`, task 6.2):** re-deriving the variant selection against
 * the *catalog* (has-variants ⇒ key required and must match a real variant; single-SKU ⇒ key must be
 * absent — Req 6.5), accessory existence/active/single-Open-show checks (Req 6.3, 6.4),
 * show-level fulfillment enablement, and per-variant applicable-stock checks (Req 6.6). Those
 * require a DB lookup and are performed after this gate passes.
 */

/** Default inclusive bounds for the number of submitted cart lines (Req 6.1, 16.3). Overridable via HOCON. */
const val DEFAULT_MIN_CHECKOUT_LINES: Int = 1
const val DEFAULT_MAX_CHECKOUT_LINES: Int = 5

/** Default inclusive bounds for a per-line quantity (Req 6.14, 16.3; aligns with cart cap Req 3.3). Overridable via HOCON. */
const val DEFAULT_MIN_LINE_QUANTITY: Int = 1
const val DEFAULT_MAX_LINE_QUANTITY: Int = 99

/**
 * Runtime-configurable checkout line/quantity bounds. Populated once at start-up from HOCON
 * (`accessory.checkout.*`) in `metaticket.accessory.Application.module`, defaulting to the `DEFAULT_*`
 * values when a key is absent or malformed. Held in a mutable singleton (mirroring
 * [metaticket.accessory.configuration.Environment]) so the pure [CheckoutValidator] stays stateless
 * and reads the current, environment-specific bounds.
 */
object CheckoutLimits {
    var minCheckoutLines: Int = DEFAULT_MIN_CHECKOUT_LINES
    var maxCheckoutLines: Int = DEFAULT_MAX_CHECKOUT_LINES
    var minLineQuantity: Int = DEFAULT_MIN_LINE_QUANTITY
    var maxLineQuantity: Int = DEFAULT_MAX_LINE_QUANTITY
}

/** Inclusive bounds for the recipient full name length (Req 5.1). */
const val MIN_CUSTOMER_NAME_LENGTH: Int = 1
const val MAX_CUSTOMER_NAME_LENGTH: Int = 100

/** Inclusive bounds for the recipient email length (Req 5.1, 16.3). */
const val MIN_CUSTOMER_EMAIL_LENGTH: Int = 1
const val MAX_CUSTOMER_EMAIL_LENGTH: Int = 254

/** Inclusive bounds for the number of phone digits, excluding an optional single leading `+` (Req 5.5). */
const val MIN_PHONE_DIGITS: Int = 8
const val MAX_PHONE_DIGITS: Int = 15

/**
 * Machine-readable classification of a single payload-validation failure. Lets the controller map a
 * failure to the correct HTTP status (Req 16.4) — e.g. [EMPTY_CART] ⇒ `400`, [OUT_OF_RANGE_LINES] ⇒
 * `400`, [SHIPPING_NOT_SUPPORTED] ⇒ `400` — while still surfacing the exact rule that failed.
 */
enum class CheckoutValidationCode {
    EMPTY_CART,               // lines empty (Req 6.2)
    OUT_OF_RANGE_LINES,       // lines.size outside 1..50 (Req 6.1, 16.3)
    INVALID_QUANTITY,         // quantity outside 1..99 (Req 6.14, 16.3)
    INVALID_ACCESSORY_ID,     // accessoryId does not decode to a valid ObjectId (Req 16.3)
    INVALID_VARIANT_SELECTION,// supplied variantKey is blank (structural; Req 6.5, 16.3)
    MISSING_CUSTOMER_FIELD,   // name/phone/email blank or whitespace-only (Req 5.3)
    INVALID_CUSTOMER_NAME,    // name length outside 1..100 (Req 5.1)
    INVALID_EMAIL,            // email malformed or > 254 chars (Req 5.4, 16.3)
    INVALID_PHONE,            // phone not 8..15 digits with optional single leading '+' (Req 5.5)
    SHIPPING_NOT_SUPPORTED,   // a legacy shipping* top-level field was supplied
    INVALID_FULFILLMENT,      // unknown fulfillment value or mode-specific field missing
}

/**
 * A single structured validation failure.
 *
 * @property code the machine-readable failure classification.
 * @property field a JSON-path-ish pointer to the offending field (e.g. `lines[2].quantity`,
 *   `customerEmail`), so the client can highlight it and retain the entered values (Req 5.3–5.5).
 * @property message a human-readable description of the failed constraint.
 */
data class CheckoutValidationError(
    val code: CheckoutValidationCode,
    val field: String,
    val message: String,
)

/**
 * Raised when a checkout payload fails validation. Carries every [CheckoutValidationError] found so
 * the caller can report all failing constraints at once (Req 16.4).
 */
class CheckoutValidationException(val errors: List<CheckoutValidationError>) :
    IllegalArgumentException("checkout payload validation failed: ${errors.joinToString("; ") { it.message }}")

/** Default / storefront labels for allowed fulfillment modes. */
const val PICKUP_AT_SHOW: String = "PickupAtShow"
const val META_TICKET_PICKUP: String = "MetaTicketPickup"
const val DELIVERY: String = "Delivery"

val ALLOWED_FULFILLMENT_TYPES: Set<String> = setOf(PICKUP_AT_SHOW, META_TICKET_PICKUP, DELIVERY)

/**
 * Lower-cased top-level request field names that indicate a legacy shipping concept that is not
 * part of the accessory checkout contract. Nested `deliveryAddress` is allowed for Delivery mode.
 */
val FORBIDDEN_SHIPPING_FIELDS: Set<String> = setOf(
    "shippingaddress", "shipping_address",
    "shippingmethod", "shipping_method",
    "shipping",
)

/** Local-part, an `@` separator, and a domain with at least one dot; no whitespace (Req 5.4). */
private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Optional single leading `+` followed by 8..15 digits and nothing else (Req 5.5). */
private val PHONE_REGEX = Regex("^\\+?\\d{$MIN_PHONE_DIGITS,$MAX_PHONE_DIGITS}$")

/**
 * The pure, standalone checkout-payload validator. Stateless; safe to call before any DB work.
 */
object CheckoutValidator {

    /**
     * Validate a [CheckoutInput] payload and return every structural violation found (empty ⇒ valid).
     *
     * @param input the submitted checkout payload.
     * @param accessoryIdDecoder resolves a public `accessoryId` to its raw `ObjectId` hex string, or
     *   `null` when the id is malformed. Defaults to [PublicId.decode]; injectable so the validator
     *   stays pure and unit-testable without service configuration.
     * @param presentTopLevelFields the lower-cased names of the top-level fields actually present in
     *   the raw request body. When supplied by the controller (task 12.3), any [FORBIDDEN_SHIPPING_FIELDS]
     *   member present is rejected. Defaults to empty (pure DTO validation).
     * @param fulfillment the raw `fulfillment` value if the request body carried one; falls back to
     *   [CheckoutInput.fulfillment], then [PICKUP_AT_SHOW].
     */
    fun validate(
        input: CheckoutInput,
        accessoryIdDecoder: (String) -> String? = PublicId::decode,
        presentTopLevelFields: Set<String> = emptySet(),
        fulfillment: String? = null,
    ): List<CheckoutValidationError> {
        val errors = mutableListOf<CheckoutValidationError>()

        validateLines(input.lines, accessoryIdDecoder, errors)
        validateCustomer(input, errors)
        validateFulfillment(input, presentTopLevelFields, fulfillment, errors)

        return errors
    }

    /**
     * Validate a payload and throw [CheckoutValidationException] if any constraint fails; returns the
     * input unchanged when valid. Convenience for the controller's "reject before DB work" gate.
     */
    fun validateOrThrow(
        input: CheckoutInput,
        accessoryIdDecoder: (String) -> String? = PublicId::decode,
        presentTopLevelFields: Set<String> = emptySet(),
        fulfillment: String? = null,
    ): CheckoutInput {
        val errors = validate(input, accessoryIdDecoder, presentTopLevelFields, fulfillment)
        if (errors.isNotEmpty()) throw CheckoutValidationException(errors)
        return input
    }

    private fun validateLines(
        lines: List<CheckoutLine>,
        accessoryIdDecoder: (String) -> String?,
        errors: MutableList<CheckoutValidationError>,
    ) {
        // Line-count bounds (Req 6.1, 6.2, 16.3). Empty is called out distinctly for a 400 empty-cart.
        if (lines.isEmpty()) {
            errors += CheckoutValidationError(
                CheckoutValidationCode.EMPTY_CART, "lines",
                "cart must contain at least ${CheckoutLimits.minCheckoutLines} line",
            )
            return
        }
        if (lines.size > CheckoutLimits.maxCheckoutLines) {
            errors += CheckoutValidationError(
                CheckoutValidationCode.OUT_OF_RANGE_LINES, "lines",
                "cart must contain between ${CheckoutLimits.minCheckoutLines} and ${CheckoutLimits.maxCheckoutLines} lines (was ${lines.size})",
            )
        }

        lines.forEachIndexed { index, line ->
            // Quantity: a whole number in 1..99 (Req 6.14, 16.3). `quantity` is already an Int, so
            // integer-ness is guaranteed by the type; only the range needs checking here.
            if (line.quantity < CheckoutLimits.minLineQuantity || line.quantity > CheckoutLimits.maxLineQuantity) {
                errors += CheckoutValidationError(
                    CheckoutValidationCode.INVALID_QUANTITY, "lines[$index].quantity",
                    "quantity must be an integer between ${CheckoutLimits.minLineQuantity} and ${CheckoutLimits.maxLineQuantity} (was ${line.quantity})",
                )
            }

            // accessoryId must decode to a valid ObjectId (Req 16.3). Catalog resolution is task 6.2.
            if (line.accessoryId.isBlank() || accessoryIdDecoder(line.accessoryId) == null) {
                errors += CheckoutValidationError(
                    CheckoutValidationCode.INVALID_ACCESSORY_ID, "lines[$index].accessoryId",
                    "accessoryId is not a well-formed encoded identifier",
                )
            }

            // Structural variant consistency only (Req 6.5, 16.3): a *supplied* variantKey must be
            // non-blank. Whether a key is required (has-variants) or forbidden (single-SKU) is
            // re-derived against the catalog at checkout (task 6.2).
            if (line.variantKey != null && line.variantKey.isBlank()) {
                errors += CheckoutValidationError(
                    CheckoutValidationCode.INVALID_VARIANT_SELECTION, "lines[$index].variantKey",
                    "variantKey, when provided, must be non-blank",
                )
            }
        }
    }

    private fun validateCustomer(input: CheckoutInput, errors: MutableList<CheckoutValidationError>) {
        // Name: non-blank (Req 5.3) and 1..100 characters (Req 5.1).
        if (input.customerName.isBlank()) {
            errors += CheckoutValidationError(
                CheckoutValidationCode.MISSING_CUSTOMER_FIELD, "customerName",
                "customerName must not be blank",
            )
        } else if (input.customerName.trim().length !in MIN_CUSTOMER_NAME_LENGTH..MAX_CUSTOMER_NAME_LENGTH) {
            errors += CheckoutValidationError(
                CheckoutValidationCode.INVALID_CUSTOMER_NAME, "customerName",
                "customerName must be $MIN_CUSTOMER_NAME_LENGTH..$MAX_CUSTOMER_NAME_LENGTH characters",
            )
        }

        // Phone: non-blank (Req 5.3), then 8..15 digits with an optional single leading '+' (Req 5.5).
        if (input.customerPhone.isBlank()) {
            errors += CheckoutValidationError(
                CheckoutValidationCode.MISSING_CUSTOMER_FIELD, "customerPhone",
                "customerPhone must not be blank",
            )
        } else if (!PHONE_REGEX.matches(input.customerPhone.trim())) {
            errors += CheckoutValidationError(
                CheckoutValidationCode.INVALID_PHONE, "customerPhone",
                "customerPhone must be $MIN_PHONE_DIGITS..$MAX_PHONE_DIGITS digits with an optional single leading '+'",
            )
        }

        // Email: non-blank (Req 5.3), <= 254 chars (Req 5.1, 16.3), well-formed (Req 5.4).
        val email = input.customerEmail
        if (email.isBlank()) {
            errors += CheckoutValidationError(
                CheckoutValidationCode.MISSING_CUSTOMER_FIELD, "customerEmail",
                "customerEmail must not be blank",
            )
        } else {
            val trimmed = email.trim()
            if (trimmed.length !in MIN_CUSTOMER_EMAIL_LENGTH..MAX_CUSTOMER_EMAIL_LENGTH || !EMAIL_REGEX.matches(trimmed)) {
                errors += CheckoutValidationError(
                    CheckoutValidationCode.INVALID_EMAIL, "customerEmail",
                    "customerEmail must be a well-formed address of at most $MAX_CUSTOMER_EMAIL_LENGTH characters",
                )
            }
        }
    }

    private fun validateFulfillment(
        input: CheckoutInput,
        presentTopLevelFields: Set<String>,
        fulfillment: String?,
        errors: MutableList<CheckoutValidationError>,
    ) {
        val offendingShipping = presentTopLevelFields
            .map { it.lowercase() }
            .filter { it in FORBIDDEN_SHIPPING_FIELDS }
        offendingShipping.forEach { field ->
            errors += CheckoutValidationError(
                CheckoutValidationCode.SHIPPING_NOT_SUPPORTED, field,
                "legacy shipping field is not supported (field '$field'); use fulfillment + deliveryAddress",
            )
        }

        val resolved = (fulfillment ?: input.fulfillment)?.trim()?.takeIf { it.isNotEmpty() }
            ?: PICKUP_AT_SHOW
        if (resolved !in ALLOWED_FULFILLMENT_TYPES) {
            errors += CheckoutValidationError(
                CheckoutValidationCode.INVALID_FULFILLMENT, "fulfillment",
                "fulfillment must be one of ${ALLOWED_FULFILLMENT_TYPES.joinToString()} (was '$resolved')",
            )
            return
        }

        when (resolved) {
            META_TICKET_PICKUP -> {
                if (input.pickupPointId.isNullOrBlank()) {
                    errors += CheckoutValidationError(
                        CheckoutValidationCode.INVALID_FULFILLMENT, "pickupPointId",
                        "pickupPointId is required when fulfillment is $META_TICKET_PICKUP",
                    )
                }
            }
            DELIVERY -> {
                val addr = input.deliveryAddress
                if (addr == null) {
                    errors += CheckoutValidationError(
                        CheckoutValidationCode.INVALID_FULFILLMENT, "deliveryAddress",
                        "deliveryAddress is required when fulfillment is $DELIVERY",
                    )
                } else {
                    fun requireField(value: String?, field: String) {
                        if (value.isNullOrBlank()) {
                            errors += CheckoutValidationError(
                                CheckoutValidationCode.INVALID_FULFILLMENT, "deliveryAddress.$field",
                                "deliveryAddress.$field must not be blank",
                            )
                        }
                    }
                    requireField(addr.city, "city")
                    requireField(addr.ward, "ward")
                    requireField(addr.address, "address")
                }
            }
        }
    }
}
