package metaticket.accessory.security

import metaticket.accessory.configuration.Environment
import metaticket.common.checksum.Checksum
import org.bson.types.ObjectId

/**
 * Encode/decode helpers for the public-facing order and payment identifiers exposed by
 * `service.accessory`.
 *
 * Public responses (checkout result, order lookup) emit **only** `Checksum`-encoded strings for
 * order and payment ids, and every public input (`order/fetch?id=`, resend, `accessoryId` in
 * checkout lines) is decoded and validated here before it reaches the domain layer. A decode or
 * validation failure returns `null` so callers can map it to a `400`/`404` — a raw MongoDB
 * `ObjectId` is **never** returned to, or accepted verbatim from, the outside world (Req 16.1,
 * 16.2).
 *
 * This reuses the same [Checksum] utility and secret-key handling as `service.payment`
 * ([Environment.secretKey], populated from HOCON at start-up), keeping the accessory id posture
 * identical to the rest of the platform. Backs design Property 13 (public identifier encoding
 * round-trip).
 */
object PublicId {

    /**
     * Encode a raw MongoDB id (hex `ObjectId` string) into its public, `Checksum`-encoded form.
     * Used when building any public response that carries an order or payment id (Req 16.1).
     */
    fun encode(rawId: String): String = Checksum.encode(rawId, Environment.secretKey)

    /**
     * Decode a public, `Checksum`-encoded identifier back into its raw MongoDB `ObjectId` hex
     * string, validating both that decoding succeeds and that the result is a well-formed
     * `ObjectId`.
     *
     * @return the decoded raw id, or `null` when the input is blank, cannot be decoded, or does not
     *   decode to a valid `ObjectId`. Callers map `null` to a `400` (malformed) or `404`
     *   (not-found) response — never a `500` and never a raw-id leak (Req 16.2).
     */
    fun decode(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        val raw = try {
            Checksum.decode(encoded, Environment.secretKey)
        } catch (t: Throwable) {
            // Malformed Base64 / wrong length / bad padding / cipher failure — treat as invalid id.
            return null
        }
        return if (ObjectId.isValid(raw)) raw else null
    }
}
