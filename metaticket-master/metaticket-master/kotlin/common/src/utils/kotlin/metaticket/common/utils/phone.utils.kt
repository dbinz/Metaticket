package metaticket.common.utils

/**
 * Normalizes a phone number string to the format "[countryCode][nationalNumber]".
 *
 * Handles various input formats:
 * - Removes whitespace.
 * - Removes leading '+' if present.
 * - Validates that the remaining characters are digits.
 * - Handles numbers starting with the country code (with or without '+').
 * - Handles numbers starting with '0' (national format) by replacing '0' with the country code.
 * - Assumes numbers not starting with the country code or '0' are national numbers and prepends the country code.
 *
 * @param phoneNumber The phone number string to normalize.
 * @param countryCode The country code to use (default is "84"). Can optionally start with '+'.
 * @return The normalized phone number (e.g., "84912345678"), or null if the input is empty,
 *         contains invalid characters (other than digits, whitespace, and an optional leading '+'),
 *         or represents an invalid format (e.g., "+123" when countryCode is "84").
 */
fun normalizePhoneNumber(phoneNumber: String, countryCode: String = "84"): String? {
    try {
        // 1. Prepare the country code (remove leading '+' and ensure it's digits only)
        val code = countryCode.removePrefix("+")
        // Basic validation for the country code itself (optional, depends on requirements)
        if (code.isEmpty() || code.any { !it.isDigit() }) {
            return null // Invalid country code
        }

        // 2. Clean the input number: remove whitespace
        val cleanedNumber = phoneNumber.filter { !it.isWhitespace() }
        if (cleanedNumber.isEmpty()) {
            return null // Empty after removing whitespace
        }

        // 3. Handle potential leading '+'
        val (numberPortion, hadPlus) = if (cleanedNumber.startsWith("+")) {
            cleanedNumber.drop(1) to true // Get number without '+' and flag that '+' was present
        } else {
            cleanedNumber to false // Use the cleaned number as is
        }

        // 4. Validate: remaining part must be all digits and not empty
        if (numberPortion.isEmpty() || numberPortion.any { !it.isDigit() }) {
            // Invalid if empty after removing '+' (e.g., "+") or contains non-digits
            return null
        }

        // 5. Normalization logic based on prefixes
        if (numberPortion.startsWith(code)) {
            // Case 1: Number already includes country code (e.g., "849..." or "+849...")
            // This is the target format (or was after removing '+').
            return numberPortion // Format: [code][nationalNumber]
        }

        if (hadPlus) {
            // Case 2: Started with '+' but NOT the expected country code (e.g., "+123..." for code "84")
            // This implies an international number with a *different* country code, which we treat as invalid for *this* function's purpose.
            return null
        }

        // From here, we know 'hadPlus' is false.
        if (numberPortion.startsWith("0")) {
            // Case 3: National format with leading '0' (e.g., "09...")
            // Replace '0' with the country code. Ensure there's something after '0'.
            val nationalNumber = numberPortion.drop(1)
            return if (nationalNumber.isNotEmpty()) {
                code + nationalNumber // Format: [code][nationalNumber]
            } else {
                null // Input was just "0"
            }
        }

        // Case 4: No '+', doesn't start with 'code', doesn't start with '0'.
        // Assume it's a local/national number needing the code prepended (e.g., "912345678").
        return code + numberPortion // Format: [code][nationalNumber]
    } catch (t: Throwable) {
        println("normalizePhoneNumber failed ... $t")
        return null
    }
}