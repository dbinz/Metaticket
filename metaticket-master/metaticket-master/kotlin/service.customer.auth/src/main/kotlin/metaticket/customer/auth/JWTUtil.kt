package metaticket.customer.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.jwt.*
import java.util.*

class JWTUtil(
    val secret: String,
    val issuer: String,
    val audience: String
) {
    fun generateToken(userId: String, phone: String, expiresAt: Long): String {
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("phone", phone)
            .withExpiresAt(Date(expiresAt))
            .sign(Algorithm.HMAC256(secret))
    }

    fun decodeToken(token: String): JWTPrincipal? {
        return try {
            val algorithm = Algorithm.HMAC256(secret)
            val verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()

            val decodedJWT = verifier.verify(token)
            JWTPrincipal(decodedJWT)
        } catch (e: Throwable) {
            null // Token không hợp lệ
        }
    }
}