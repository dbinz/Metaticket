package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import metaticket.accessory.pojo.request.CheckoutInput
import metaticket.accessory.pojo.request.CheckoutLine
import metaticket.accessory.pojo.request.CheckoutValidationCode
import metaticket.accessory.pojo.request.CheckoutValidator

/**
 * Property 16: Customer information validation (task 6.7).
 *
 * **Validates: Requirements 5.3, 5.4, 16.2**
 *
 * ∀ checkout payload: the checkout is accepted only when `customerName`, `customerPhone`, and
 * `customerEmail` are non-blank and `customerEmail` is well-formed; otherwise no order is created.
 *
 * The test drives the **real** pure gate [CheckoutValidator.validate] directly (per task 6.7). It
 * fixes a single structurally-valid cart line (so only the customer fields vary) and checks the
 * validator's customer-field decision against an INDEPENDENT reimplementation of the customer
 * predicate. When the payload is not customer-valid, the validator must report at least one error
 * (⇒ the controller creates no order).
 */
class CustomerInfoValidationPropertyTest : FunSpec({

    // Always-valid accessory id decoder so a single line never contributes a line-level error; the
    // customer fields are the only thing under test here.
    val validObjectIdHex = "0123456789abcdef01234567"
    val alwaysValidDecoder: (String) -> String? = { validObjectIdHex }
    val validLine = CheckoutLine(accessoryId = "enc", variantKey = null, quantity = 1)

    // Email regex mirroring the spec's definition (local-part, '@', domain with at least one dot).
    val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    val phoneRegex = Regex("^\\+?\\d{8,15}$")

    // Names: valid, blank, whitespace-only, and over-length.
    val nameArb = Arb.element(
        "Nguyen Van A",
        "A",
        "",
        "   ",
        "x".repeat(101),
    )
    // Phones: valid, blank, malformed, out-of-range.
    val phoneArb = Arb.element(
        "0987654321",
        "+84987654321",
        "",
        "   ",
        "12345",              // too short
        "12345678901234567",  // too long
        "09a8765432",         // non-digit
    )
    // Emails: valid, blank, whitespace-only, malformed (no dot / no @ / has space).
    val emailArb = Arb.element(
        "a@example.com",
        "user.name@sub.domain.vn",
        "",
        "   ",
        "no-at-sign",
        "missing@domain",     // no dot in domain
        "has space@x.com",
    )

    val customerCodes = setOf(
        CheckoutValidationCode.MISSING_CUSTOMER_FIELD,
        CheckoutValidationCode.INVALID_CUSTOMER_NAME,
        CheckoutValidationCode.INVALID_EMAIL,
        CheckoutValidationCode.INVALID_PHONE,
    )

    test("customer fields are accepted iff name/phone non-blank+valid and email non-blank+well-formed") {
        checkAll(100, nameArb, phoneArb, emailArb) { name, phone, email ->
            val input = CheckoutInput(
                lines = listOf(validLine),
                customerName = name,
                customerPhone = phone,
                customerEmail = email,
            )
            val errors = CheckoutValidator.validate(input, accessoryIdDecoder = alwaysValidDecoder)

            // Independent reimplementation of the customer predicate (Req 5.3, 5.4, 5.5).
            val nameOk = name.isNotBlank() && name.trim().length in 1..100
            val phoneOk = phone.isNotBlank() && phoneRegex.matches(phone.trim())
            val emailOk = email.isNotBlank() && email.trim().length in 1..254 && emailRegex.matches(email.trim())
            val expectedCustomerValid = nameOk && phoneOk && emailOk

            val customerErrors = errors.filter { it.code in customerCodes }

            // The validator's customer-field decision matches the independent predicate.
            customerErrors.isEmpty() shouldBe expectedCustomerValid

            // When any customer field is invalid, the payload is rejected overall ⇒ no order (Req 5.3).
            if (!expectedCustomerValid) {
                errors.isNotEmpty() shouldBe true
            }
        }
    }

    test("a fully-valid customer payload passes the validator with no errors") {
        val input = CheckoutInput(
            lines = listOf(validLine),
            customerName = "Nguyen Van A",
            customerPhone = "0987654321",
            customerEmail = "a@example.com",
        )
        CheckoutValidator.validate(input, accessoryIdDecoder = alwaysValidDecoder) shouldBe emptyList()
    }
})
