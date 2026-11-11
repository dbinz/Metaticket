package metaticket.accessory.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import metaticket.accessory.configuration.Environment
import org.bson.types.ObjectId

/**
 * Property 13: Public identifier encoding round-trip (task 12.6).
 *
 * **Validates: Requirements 7.3, 16.1**
 *
 * ∀ order and payment identifier exposed publicly: the value is the `Checksum`-encoded form of an
 * internal `ObjectId`, and decoding the exposed value yields the original internal id. No raw
 * `ObjectId` is ever emitted. Malformed encoded ids fail decode cleanly (returning `null`, never
 * throwing).
 *
 * This drives the **real** [PublicId.encode] / [PublicId.decode] helpers (the exact pair used to
 * build every public response and to validate every public id input). The AES/CBC cipher the
 * underlying `Checksum` utility uses derives its IV from the key, so the shared secret must be
 * exactly 16 ASCII characters (AES-128).
 */
class PublicIdEncodingRoundTripPropertyTest : FunSpec({

    Environment.secretKey = "0123456789abcdef"

    // Random internal ids: well-formed 24-hex `ObjectId` strings, as produced across the service.
    val objectIdHexArb: Arb<String> = arbitrary { ObjectId().toHexString() }

    test("encode then decode yields the original ObjectId, and the encoded form is never the raw id") {
        checkAll(100, objectIdHexArb) { rawId ->
            val encoded = PublicId.encode(rawId)

            // No raw ObjectId is ever emitted: the public form differs from the internal id and is
            // not itself a bare ObjectId hex string.
            encoded shouldNotBe rawId
            ObjectId.isValid(encoded) shouldBe false

            // Round-trip: decoding the exposed value recovers the original internal id exactly.
            val decoded = PublicId.decode(encoded)
            decoded.shouldNotBeNull()
            decoded shouldBe rawId
        }
    }

    test("malformed encoded ids fail decode cleanly (null, never throwing) and never yield an invalid id") {
        // Fixed malformed inputs: blank, whitespace, non-Base64, wrong-length, and a bare ObjectId
        // hex (which is NOT a valid encrypted+encoded token).
        val fixedMalformed = listOf(
            null, "", "   ",
            "not base64!!!",
            "zzzz",
            "@@@@",
            ObjectId().toHexString(),          // a raw id is not a valid encoded token
        )
        fixedMalformed.forEach { bad ->
            // Must not throw, and must classify as invalid (null) rather than leaking a raw id.
            val decoded = PublicId.decode(bad)
            if (decoded != null) ObjectId.isValid(decoded) shouldBe true
        }
        // Concretely, every fixed malformed input above decodes to null.
        fixedMalformed.forEach { bad -> PublicId.decode(bad) shouldBe null }

        // Property: arbitrary junk strings never make decode throw; a non-null result is always a
        // well-formed ObjectId (decode never leaks a malformed raw id).
        checkAll(100, Arb.string(0..40)) { junk ->
            val decoded = PublicId.decode(junk)
            if (decoded != null) ObjectId.isValid(decoded) shouldBe true
        }
    }
})
