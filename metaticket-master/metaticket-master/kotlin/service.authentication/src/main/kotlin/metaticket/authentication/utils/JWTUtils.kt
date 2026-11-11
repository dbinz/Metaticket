package metaticket.authentication.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.ObjectMapper
import metaticket.authentication.pojo.db.AuthInformationDocument
import java.util.*

data class JWTPayload constructor(
    val userId: String,
    val username: String,
    val hash: String,
    val autoRenew: Boolean,
    val aud: String,
    val iss: String,
    val exp: Double
)

class JWTUtils {
    companion object {

        fun generateToken(authInfo: AuthInformationDocument, hash: String, autoRenew: Boolean, secret: String, audience: String, issuer: String, expirationPeriod: Long): String {

            return JWT.create()
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .withClaim("userId", authInfo.id)
                    .withClaim("username", authInfo.email)
                    .withClaim("autoRenew", autoRenew)
                    .withClaim("hash", hash)
                    //hash claim userid + time + random number
                    //autorenew - true / false
                    .withExpiresAt(Date(System.currentTimeMillis() + expirationPeriod))
                    .sign(Algorithm.HMAC256(secret))
        }

        /**
         * Verifies signature, issuer, audience and expiry before any claim is trusted: the payload itself is
         * just base64, so an unverified decode lets anyone mint a token for any userId/username.
         */
        fun verifyToken(token: String, secret: String, audience: String, issuer: String, jsonMapper: ObjectMapper): JWTPayload {
            val decoded = JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
                .verify(token)
            val payload = String(Base64.getUrlDecoder().decode(decoded.payload), Charsets.UTF_8)
            return jsonMapper.readValue(payload, JWTPayload::class.java)
        }

    }



}