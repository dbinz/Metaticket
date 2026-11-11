package metaticket.authentication.controller
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import metaticket.authentication.DatabaseService
import metaticket.authentication.pojo.db.AuthInformationDocument
import metaticket.authentication.pojo.request.LoginRequest
import metaticket.authentication.pojo.request.RefreshTokenRequest
import metaticket.authentication.pojo.request.RegisterRequest
import metaticket.authentication.pojo.response.LoginResponse
import metaticket.authentication.pojo.response.RefreshTokenResponse
import metaticket.authentication.pojo.response.OperatorAccountResponse
import metaticket.authentication.pojo.session.UserSession
import metaticket.authentication.redis.RedisClient
import metaticket.authentication.utils.BcryptUtils
import metaticket.authentication.utils.JWTUtils
import metaticket.authentication.utils.RSAEncryption
import metaticket.authentication.utils.Utils
import metaticket.common.logger.Logging
import java.util.*

class AuthenticationController(
    private val ktorEnv: ApplicationEnvironment,
    private val db : DatabaseService,
    private val jsonMapper: ObjectMapper,
    private val redisClient: RedisClient,
) : Logging {

    private val jwtSecret = ktorEnv.config.property("jwt.secret").getString()
    private val jwtAudience = ktorEnv.config.property("jwt.audience").getString()
    private val jwtIssuer = ktorEnv.config.property("jwt.issuer").getString()

    private fun verifyToken(token: String) = JWTUtils.verifyToken(token, jwtSecret, jwtAudience, jwtIssuer, jsonMapper)

    // Helper function to generate tokens
    private fun generateTokens(
        userDoc: AuthInformationDocument, // User document containing necessary info
        userId: String // Explicit userId, usually from userDoc.id
    ): Pair<String, String>? { // Returns Pair(accessToken, refreshToken) or null on failure
        val stringToEncrypt = Utils.buildStringHashToEncrypt(userId)
        val publicKey = RSAEncryption.getPublicKeyFromString(ktorEnv.config.property("rsaEncypt.publicKey").getString())
        val rsaEncryptedHash = RSAEncryption.encrypt(publicKey!!, stringToEncrypt)
            ?: run {
                logger.error("Failed to create secure hash for userId: $userId")
                return null
            }

        val accessToken = JWTUtils.generateToken(
            userDoc.copy(id = userId), // Ensure AuthInformationDocument in JWT has the ID
            rsaEncryptedHash,
            true, // Assuming 'loggedIn' status
            ktorEnv.config.property("jwt.secret").getString(),
            ktorEnv.config.property("jwt.audience").getString(),
            ktorEnv.config.property("jwt.issuer").getString(),
            ktorEnv.config.property("jwt.expiration_time").getString().toLong(),
        )
        val newRefreshToken = UUID.randomUUID().toString()
        return Pair(accessToken, newRefreshToken)
    }


    // Helper function to get token from Session or Header
    private fun getTokenFromCall(call: ApplicationCall): String? {
        var token = call.sessions.get<UserSession>()?.token
        if (token == null) {
            val authHeader = call.request.header(HttpHeaders.Authorization)
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7)
            }
        }
        return token
    }

    /**
     * Operator accounts can only be created by an already-authenticated operator (route sits under
     * `/private/`, nginx `auth_request` sets `X-OP-User-Id`). Every operator is effectively a full admin
     * (no role check downstream), so this must never be reachable anonymously. Unlike the old public
     * register it issues no token and does not touch the caller's session.
     */
    suspend fun createOperatorAccount(call: ApplicationCall) {
        val creatorId = call.request.header("X-OP-User-Id")?.takeIf { it.isNotBlank() }
            ?: return call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

        val input = try {
            call.receive<RegisterRequest>()
        } catch (e: Exception) {
            return call.respond(HttpStatusCode.BadRequest, "Dữ liệu không hợp lệ")
        }
        val email = input.email.trim()
        if (email.length > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matches(email)) {
            return call.respond(HttpStatusCode.BadRequest, "Email không hợp lệ")
        }
        // bcrypt only uses the first 72 bytes, so a longer password would silently be truncated.
        if (input.password.length < MIN_PASSWORD_LENGTH || input.password.toByteArray(Charsets.UTF_8).size > 72) {
            return call.respond(HttpStatusCode.BadRequest, "Mật khẩu phải từ $MIN_PASSWORD_LENGTH ký tự và không quá 72 byte")
        }

        if (db.getUserByEmail(email) != null) {
            return call.respond(HttpStatusCode.Conflict, "Tên đăng nhập đã tồn tại")
        }

        val accountDoc = AuthInformationDocument(
            email = email,
            password = BcryptUtils.hashPassword(input.password, ktorEnv.config.property("bcrypt.salt").getString()),
        )
        val userId = db.registerUser(accountDoc)

        logger.info("Operator account {} ({}) created by operator {}", userId, email, creatorId)
        call.respond(HttpStatusCode.Created, OperatorAccountResponse(userId, email))
    }

    suspend fun login(call: ApplicationCall) {
        val input = call.receive<LoginRequest>()

        val userDoc = db.getUserByEmail(input.email) ?: return call.respond(HttpStatusCode.BadRequest, "User invalid")

        if(BcryptUtils.verifyPassword(input.password, userDoc.password)) {
//            db.updateUserLoggedIn(userDoc.id)

            val tokens = generateTokens(userDoc, userDoc.id)
                ?: return call.respond(HttpStatusCode.InternalServerError, "Failed to generate tokens during login")

            redisClient.saveUserTokens(userDoc.id, tokens.first, tokens.second)
            call.sessions.set(UserSession(email = userDoc.email, token = tokens.first)) // Store accessToken in Ktor session
            // Consider returning both accessToken and refreshToken
            val loginResponse = LoginResponse(tokens.first, tokens.second)
            return call.respond(loginResponse)
        }
        return call.respond(HttpStatusCode.BadRequest, "User invalid")
    }

    suspend fun refreshToken(call: ApplicationCall) {
        val request = try {
            call.receive<RefreshTokenRequest>()
        } catch (e: Exception) {
            logger.warn("Invalid refresh token request payload: ${e.message}")
            return call.respond(HttpStatusCode.BadRequest, "Invalid request payload for refresh token.")
        }

        val sessionToken = getTokenFromCall(call) ?: run {
            logger.debug("No token in session or header for checkLogin");
            return call.respond(HttpStatusCode.Unauthorized, "Unauthorized: No session token")
        }

        val payload = try {
            verifyToken(sessionToken)
        } catch (e: Exception) {
            logger.error("Token decoding failed for checkLogin: ${e.message}")
            call.sessions.clear<UserSession>()
            return call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Invalid token")
        }

        val storedUserTokens = redisClient.getUserTokens(payload.userId)
        if (storedUserTokens == null) {
            logger.warn("No stored tokens found for userId: ${payload.userId} during refresh attempt.")
            return call.respond(HttpStatusCode.Unauthorized, "Invalid session or refresh token.")
        }

        if (storedUserTokens.refreshToken != request.refreshToken) {
            logger.warn("Provided refresh token does not match stored token for userId: ${payload.userId}.")
            // CRITICAL: Potential refresh token misuse. Invalidate all sessions for this user.
            redisClient.removeUserTokens(payload.userId)
            return call.respond(HttpStatusCode.Unauthorized, "Invalid refresh token.")
        }

        // Refresh token is valid, proceed to issue new tokens
        val userDoc = db.loadUser(payload.userId)
            ?: run {
                logger.error("User not found for ID: ${payload.userId} during token refresh, though refresh token was valid.")
                // This case (valid refresh token but no user) should ideally not happen if DB and Redis are consistent.
                // Invalidate the tokens as a precaution.
                redisClient.removeUserTokens(payload.userId)
                return call.respond(HttpStatusCode.Unauthorized, "User not found, session invalidated.")
            }

        val newTokens = generateTokens(userDoc, payload.userId)
            ?: return call.respond(HttpStatusCode.InternalServerError, "Failed to generate new tokens during refresh.")

        logger.info("Tokens refreshed successfully for userId: ${payload.userId}")
        redisClient.saveUserTokens(payload.userId, newTokens.first, newTokens.second)
        call.sessions.set(UserSession(email = userDoc.email, token = newTokens.first)) // Store accessToken in Ktor session
        call.respond(RefreshTokenResponse(token = newTokens.first, refreshToken = newTokens.second))
    }


    suspend fun logout(call: ApplicationCall) {
        val sessionToken = getTokenFromCall(call) ?: return call.respond(HttpStatusCode.OK, "Already logged out or session expired")
        try {
            val payload = verifyToken(sessionToken)
//            db.updateUserLogout(payload.userId)
            redisClient.removeUserTokens(payload.userId)
        } catch (e: Exception) {
            logger.warn("Failed to decode token during logout, but proceeding to clear session: ${e.message}")
        }
        call.sessions.clear<UserSession>()
        return call.respond(HttpStatusCode.OK,"Logged out successfully")
    }

    suspend fun checkLogin(call: ApplicationCall) {
        val sessionToken = getTokenFromCall(call) ?: run {
            logger.debug("No token in session for checkLogin");
            return call.respond(HttpStatusCode.Unauthorized, "Unauthorized: No session token")
        }

        val payload = try {
            verifyToken(sessionToken)
        } catch (e: Exception) {
            logger.error("Token decoding failed for checkLogin: ${e.message}")
            call.sessions.clear<UserSession>()
            return call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Invalid token")
        }

        val storedUserTokens = redisClient.getUserTokens(payload.userId)
        if (storedUserTokens == null || storedUserTokens.accessToken != sessionToken) {
            logger.error("Token for user ${payload.userId} is not valid or does not match stored token")
            call.sessions.clear<UserSession>()
            return call.respond(HttpStatusCode.Unauthorized, "Unauthorized: Session expired or token mismatch")
        }

        call.response.headers.append("X-OP-User-Id", payload.userId)
        return call.respond(HttpStatusCode.OK, "OK: Session is valid")
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_EMAIL_LENGTH = 254
        val EMAIL_PATTERN = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    }
}
