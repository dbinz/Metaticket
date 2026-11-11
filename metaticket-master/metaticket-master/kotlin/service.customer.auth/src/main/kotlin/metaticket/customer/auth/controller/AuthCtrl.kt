package metaticket.customer.auth.controller

import com.auth0.jwt.exceptions.JWTVerificationException
import io.grpc.StatusException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.common.utils.normalizePhoneNumber
import metaticket.customer.auth.JWTUtil
import metaticket.customer.auth.gateway.CaptchaGateway
import metaticket.customer.auth.gateway.CustomerUserGateway
import metaticket.customer.auth.pojo.LoginRequest
import metaticket.customer.auth.pojo.LoginResponse
import metaticket.customer.auth.repository.UserTokenRepository
import metaticket.customer.auth.response.CaptchaSettingResponse
import java.time.Duration
import java.util.*

class AuthCtrl constructor(
    private val config: ApplicationConfig,
    private val userGateway: CustomerUserGateway,
    private val recaptchaGateway: CaptchaGateway,
    private val tokenRepo: UserTokenRepository,
    private val jwtUtil: JWTUtil,
): Logging {
    private val tokenExpirationInM = config.property("jwt.tokenExpirationInM").getString().toLong()
    private val refreshTokenExpirationInH = config.property("jwt.refreshTokenExpirationInH").getString().toLong()
    private val enableCaptcha = config.property("captcha.enable").getString().toBoolean()
    private val captchaGateway = config.property("captcha.captchaGateway").getString()
    private val siteKeyCaptcha = config.property("captcha.${captchaGateway}.siteKey").getString()

    suspend fun getCaptchaSetting(call: ApplicationCall) = call.respond(CaptchaSettingResponse(enableCaptcha, captchaGateway, siteKeyCaptcha))

    suspend fun authenticate(call: ApplicationCall) {
        try {
            // Extract token from Authorization header
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                ?: return call.respond(HttpStatusCode.Unauthorized)

            // Extract principal from JWT
            val principal = call.principal<JWTPrincipal>()
                ?: return call.respond(HttpStatusCode.Unauthorized)

            // Extract user ID, phone, and expiration time from JWT payload
            val userId = principal.payload.getClaim("userId")?.asString()
                ?: return call.respond(HttpStatusCode.Unauthorized)
            val phone = principal.payload.getClaim("phone")?.asString()
                ?: return call.respond(HttpStatusCode.Unauthorized)

            // Retrieve token from Redis based on user ID
            val redisToken = tokenRepo.findToken(userId)
                ?: return call.respond(HttpStatusCode.Unauthorized)

            // Validate token against Redis token
            if (redisToken != token) return call.respond(HttpStatusCode.Unauthorized)

            val phoneNumber = normalizePhoneNumber(phone) ?: run {
                return call.respond(HttpStatusCode.Unauthorized, "Số điện thoại không đúng, vui lòng kiểm tra lại")
            }

            // Set user header and respond with success
            call.response.headers.append("X-Customer-Phone", phoneNumber)
            call.response.headers.append("X-Customer-User-Id", userId)
            call.respond(HttpStatusCode.OK)

        } catch (e: Throwable) {
            // Handle JWT verification exceptions
            if (e is JWTVerificationException) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid JWT")
            } else {
                // Handle other internal server errors
                call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, vui lòng thử lại sau")
            }
        }
    }

    suspend fun login(call: ApplicationCall) {
        val req = call.receive<LoginRequest>()
        val phoneNumber = normalizePhoneNumber(req.phone) ?: run {
            return call.respond(HttpStatusCode.BadRequest, "Số điện thoại không đúng, vui lòng kiểm tra lại")
        }

        if (enableCaptcha) {
            val success = recaptchaGateway.isCaptchaSuccess(req.captchaToken!!)
            if (!success) {
                logger.error("Login [{}] verify captcha failed", phoneNumber)
                return call.respond(HttpStatusCode.BadRequest, "Kiểm tra captcha không chính xác, vui lòng thử lại")
            }
        }

        // Verify user credentials using gRPC service
        val userProfile = try {
            userGateway.verifyPassword(phoneNumber, req.password)
        } catch (e: StatusException) {
            if (e.status.code == io.grpc.Status.Code.UNAUTHENTICATED || e.status.code == io.grpc.Status.Code.NOT_FOUND) {
                return call.respond(HttpStatusCode.BadRequest, e.status.description ?: "Tài khoản hoặc mật khẩu không đúng")
            } else {
                return call.respond(HttpStatusCode.BadRequest, e.status.description ?: "Lỗi hệ thống, vui lòng thử lại")
            }
        } catch (t: Throwable) {
            logger.error("Login [{}] failed ...", phoneNumber, t)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, vui lòng thử lại sau")
        }

        val ttlTokenInS = Duration.ofMinutes(tokenExpirationInM).toSeconds()
        val ttlRefreshTokenInS = Duration.ofHours(refreshTokenExpirationInH).toSeconds()

        // Generate JWT token
        val token = jwtUtil.generateToken(userProfile.id, userProfile.phone, System.currentTimeMillis() + ttlTokenInS * 1000)
        val refreshToken = jwtUtil.generateToken(userProfile.id, userProfile.phone, System.currentTimeMillis() + ttlRefreshTokenInS * 1000)

        tokenRepo.saveToken(userProfile.id, token, ttlTokenInS) ?: run {
            logger.error("Login [{}] save token failed ...", phoneNumber)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, vui lòng thử lại")
        }

        tokenRepo.saveRefreshToken(userProfile.id, refreshToken, ttlRefreshTokenInS) ?: run {
            logger.error("Login [{}] save refresh token failed ...", phoneNumber)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, vui lòng thử lại")
        }

        call.response.cookies.append(
            Cookie(
                name = "refreshToken",
                value = refreshToken,
                maxAge = ttlRefreshTokenInS.toInt(),
                httpOnly = true,
                secure = true, // Chỉ gửi qua HTTPS
                path = "/",
            )
        )

        // Respond with the token
        call.respond(HttpStatusCode.OK, LoginResponse(message = "Đăng nhập thành công", token = token))
    }

    suspend fun refreshToken(call: ApplicationCall) {
        val refreshToken = call.request.cookies["refreshToken"]
            ?: return call.respond(HttpStatusCode.Unauthorized, "Missing refresh token")

        val claims = jwtUtil.decodeToken(refreshToken)
            ?: return call.respond(HttpStatusCode.Unauthorized, "Invalid refresh token")

        // Kiểm tra các claim quan trọng
        val userId = claims["userId"]
        val phone = claims["phone"]
        val expiresAt = claims.expiresAt

        if (userId.isNullOrBlank() || phone.isNullOrBlank() || expiresAt == null || expiresAt.before(Date())) {
            return call.respond(HttpStatusCode.Unauthorized, "Invalid refresh token") // Token không hợp lệ hoặc đã hết hạn
        }

        // Kiểm tra refresh token có hợp lệ không (tồn tại trong Redis)
        val storedToken = tokenRepo.findRefreshToken(userId)
        if (storedToken != refreshToken) {
            return call.respond(HttpStatusCode.Unauthorized, "Refresh token mismatch")
        }

        val phoneNumber = normalizePhoneNumber(phone) ?: run {
            return call.respond(HttpStatusCode.BadRequest, "Số điện thoại không đúng, vui lòng kiểm tra lại")
        }

        val ttlTokenInS = Duration.ofMinutes(tokenExpirationInM).toSeconds()
        val ttlRefreshTokenInS = Duration.ofHours(refreshTokenExpirationInH).toSeconds()

        // Generate JWT token
        val token = jwtUtil.generateToken(userId, phone, System.currentTimeMillis() + ttlTokenInS * 1000)
        val newRefreshToken = jwtUtil.generateToken(userId, phone, System.currentTimeMillis() + ttlRefreshTokenInS * 1000)

        tokenRepo.saveToken(userId, token, ttlTokenInS) ?: run {
            logger.error("refreshToken [{}] save token failed ...", phoneNumber)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, vui lòng thử lại")
        }

        tokenRepo.saveRefreshToken(userId, newRefreshToken, ttlRefreshTokenInS) ?: run {
            logger.error("refreshToken [{}] save refresh token failed ...", phoneNumber)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, vui lòng thử lại")
        }

        call.response.cookies.append(
            Cookie(
                name = "refreshToken",
                value = newRefreshToken,
                maxAge = ttlRefreshTokenInS.toInt(),
                httpOnly = true,
                secure = true, // Chỉ gửi qua HTTPS
                path = "/",
            )
        )


        call.respond(HttpStatusCode.OK, LoginResponse(message = "Làm mới phiên đăng nhập thành công", token = token))
    }


    suspend fun logout(call: ApplicationCall) {
        // Extract token and user ID from headers
        val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            ?: return call.respond(HttpStatusCode.OK)

        val claims = jwtUtil.decodeToken(token)
            ?: return call.respond(HttpStatusCode.OK, "Invalid token")

        val userId = claims["userId"]
            ?: return call.respond(HttpStatusCode.OK, "Invalid token")

        // Delete token from Redis
        tokenRepo.deleteToken(userId)
        tokenRepo.deleteRefreshToken(userId)

        call.response.cookies.append(
            Cookie(
                name = "refreshToken",
                value = "",
                maxAge = 0,
                httpOnly = true,
                secure = true, // Chỉ gửi qua HTTPS
                path = "/",
            )
        )

        // Respond with success
        call.respond(HttpStatusCode.OK)
    }
}