package metaticket.customer.user.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.mongodb.MongoWriteException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.common.utils.normalizePhoneNumber
import metaticket.common.utils.OTPGenerator
import metaticket.customer.user.gateway.NotificationGateway
import metaticket.customer.user.gateway.CaptchaGateway
import metaticket.customer.user.model.*
import metaticket.customer.user.repository.SessionRepository
import metaticket.customer.user.repository.UserRepository
import metaticket.customer.user.request.*
import metaticket.customer.user.response.CaptchaSettingResponse
import metaticket.customer.user.response.EmailVerificationSettingResponse
import metaticket.customer.user.utils.hashPassword
import metaticket.notification.zns.grpc.ZnsMessage
import java.util.*

class UserCtrl constructor(
    private val config: ApplicationConfig,
    private val sessionRepo: SessionRepository,
    private val userRepo: UserRepository,
    private val captchaClient: CaptchaGateway,
    private val notificationGateway: NotificationGateway,
    private val znsConfig: Map<SessionType, ZNSConfig>,
    private val mailConfig: Map<SessionType, MailOTPConfig>,
    private val sessionConfig: SessionConfig,
) : Logging {
    private val salt = config.property("bcrypt.salt").getString()
    private val enableCaptcha = config.property("captcha.enable").getString().toBoolean()
    private val captchaGateway = config.property("captcha.captchaGateway").getString()
    private val siteKeyCaptcha = config.property("captcha.${captchaGateway}.siteKey").getString()
    // Fail-safe: nếu quên thêm key vào conf khi rollout, giữ hành vi bắt buộc xác thực email như cũ thay vì crash lúc start.
    private val enableEmailVerification =
        config.propertyOrNull("emailVerification.enable")?.getString()?.toBoolean() ?: true
    private val mapper = jacksonObjectMapper()

    suspend fun getCaptchaSetting(call: ApplicationCall) = call.respond(CaptchaSettingResponse(enableCaptcha, captchaGateway, siteKeyCaptcha))

    suspend fun getEmailVerificationSetting(call: ApplicationCall) = call.respond(EmailVerificationSettingResponse(enableEmailVerification))

    private fun getSessionData(json: String?): SessionData? {
        if (json.isNullOrBlank()) return null
        return try {
            mapper.readValue<SessionData>(json)
        } catch (e: Exception) {
            logger.error("Failed to parse session data: {}", json, e)
            null
        }
    }

    private fun saveSessionData(data: SessionData): String {
        return mapper.writeValueAsString(data)
    }

    suspend fun initSessionId(call: ApplicationCall, type: SessionType) {
        val sessionKey = "${type}_SessionId"

        // Xác minh Captcha ngay tại bước khởi tạo session để tránh bị tạo quá nhiều session trên Redis
        // Áp dụng cho cả Đăng ký (REGISTERED_USER) và Quên mật khẩu (RESET_PASSWORD)
        if (enableCaptcha && type in listOf(SessionType.REGISTERED_USER, SessionType.RESET_PASSWORD)) {
            val req = try { call.receive<InitSessionRequest>() } catch (e: Throwable) {
                logger.error("Init session [{}] missing request body for captcha", sessionKey, e)
                return call.respond(HttpStatusCode.BadRequest, "Vui lòng hoàn thành xác minh Captcha.")
            }

            val captchaToken = req.captchaToken ?: run {
                logger.error("Init session [{}] missing captcha token", sessionKey)
                return call.respond(HttpStatusCode.BadRequest, "Vui lòng hoàn thành xác minh Captcha.")
            }

            val success = captchaClient.isCaptchaSuccess(captchaToken)
            if (!success) {
                logger.error("Init session [{}] captcha verification failed", sessionKey)
                return call.respond(HttpStatusCode.BadRequest, "Kiểm tra captcha không chính xác, vui lòng thử lại")
            }
        }

        val sessionId = UUID.randomUUID().toString()
        val sessionData = SessionData(status = SessionStatus.CREATED)
        sessionRepo.save(type, sessionId, saveSessionData(sessionData), sessionConfig.expiredTimeInS)
            ?: run {
                logger.error("Init session [{}] cannot save to redis", sessionKey)
                return call.respond(HttpStatusCode.InternalServerError,  "Lỗi hệ thống, vui lòng thử lại")
            }
        call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=$sessionId; Path=/; HttpOnly; Secure; SameSite=Lax")
        call.respond(HttpStatusCode.OK)
    }

    suspend fun sendOTP(call: ApplicationCall, type: SessionType) {
        val sessionKey = "${type}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui long thử lại")

        val sessionValue = sessionRepo.findSession(type, sessionId)
        val sessionData = getSessionData(sessionValue)
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui long thử lại")

        val req = call.receive<SendOTPRequest>()
        val isUpdateProfile = type == SessionType.UPDATE_PROFILE

        val phoneNumber = if (isUpdateProfile) {
            val userId = call.request.headers["X-Customer-User-Id"] ?: return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
            val user = userRepo.findById(userId) ?: return call.respond(HttpStatusCode.Unauthorized, "Người dùng không tồn tại")
            user.phone
        } else {
            val phoneInput = req.phone ?: return call.respond(HttpStatusCode.BadRequest, "Vui lòng nhập số điện thoại")
            normalizePhoneNumber(phoneInput) ?: run {
                logger.error("Send OTP [{}] phone [{}] normalize failed", sessionId, phoneInput)
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                sessionRepo.deleteSessionId(type, sessionId)
                return call.respond(HttpStatusCode.BadRequest, "Số điện thoại không đúng, vui lòng kiểm tra lại")
            }
        }

        val refId = UUID.randomUUID().toString()
        val otp = OTPGenerator.generateOTP()

        // Handle sending Email OTP
        // Logic: 
        // 1. REGISTERED_USER: Only if Phone verified
        // 2. UPDATE_PROFILE: If we skip phone verify (since user logged in), we can proceed if session is created.
        
        // emailVerification.enable chỉ chi phối luồng đăng ký (REGISTERED_USER) — không ảnh hưởng đổi email của user đã đăng nhập (UPDATE_PROFILE)
        val canSendEmailOtp =
            (type == SessionType.REGISTERED_USER && sessionData.isPhoneVerified && enableEmailVerification) || isUpdateProfile

        if (canSendEmailOtp) {
            // For Update Profile, phoneNumber in request might be current phone, but we focus on Email.
            // If Update Profile, we don't strictly enforce phone matching session if we trust the logged-in user context from initSession?
            // Actually, initSession for Update Profile should probably require Auth.
            
            if (type == SessionType.REGISTERED_USER && sessionData.phone != phoneNumber) {
                 return call.respond(HttpStatusCode.BadRequest, "Số điện thoại không khớp với phiên làm việc")
            }

            val email = req.email
            if (email.isNullOrBlank()) {
                // If not registered user (who must provide email here), maybe update profile forgot it?
                // If update profile and just phone otp requested? (Not supported yet)
                // Assuming Update Profile = Change Email
                if (isUpdateProfile) return call.respond(HttpStatusCode.BadRequest, "Vui lòng nhập email mới")
                return call.respond(HttpStatusCode.BadRequest, "Vui lòng nhập email")
            }
            
            // Check if email exist for Update Profile
            if (isUpdateProfile) {
                userRepo.findByEmail(email)?.let {
                    return call.respond(HttpStatusCode.BadRequest, "Email đã được sử dụng bởi tài khoản khác")
                }
            } else {
                // Also check for Registration (good practice)
                userRepo.findByEmail(email)?.let {
                    return call.respond(HttpStatusCode.BadRequest, "Email đã được sử dụng")
                }
            }

            logger.debug("Send Email OTP [{}] email [{}], otp [{}], refId [{}]", sessionId, email, otp, refId)
            
            val success = notificationGateway.sendEmailOTP(mailConfig[type]!!, refId, email, otp)
            if (!success) {
                 return call.respond(HttpStatusCode.InternalServerError, "Không gửi được email OTP, vui lòng thử lại")
            }

            val expiredTime = Date().time / 1000 + sessionConfig.expiredTimeInS
            
            val newData = sessionData.copy(
                status = SessionStatus.OTP_SENT,
                otp = otp,
                email = email,
                currentTarget = OtpTarget.EMAIL,
                failedCount = 0,
                expiredTime = expiredTime,
                // For Update Profile, we consider Phone implicitly verified or not needed
                isPhoneVerified = if (isUpdateProfile) true else true
            )

            sessionRepo.save(
                type,
                sessionId,
                saveSessionData(newData),
                sessionConfig.expiredTimeInS
            )
            return call.respond(HttpStatusCode.OK)
        }

        // Session phải ở trạng thái CREATED để có thể gửi OTP (và xác minh Captcha nếu bật)
        if (sessionData.status != SessionStatus.CREATED) {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(type, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc có lỗi hoặc đã được sử dụng, vui lòng thử lại")
        }

        when(type) {
            SessionType.RESET_PASSWORD -> {
                userRepo.findByPhone(phoneNumber) ?: run {
                    logger.error("Send OTP [{}] phone [{}] not exist", sessionId, phoneNumber)
                    call.response.headers.append(
                        HttpHeaders.SetCookie,
                        "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax"
                    )
                    sessionRepo.deleteSessionId(type, sessionId)
                    return call.respond(HttpStatusCode.BadRequest, "Số điện thoại chưa đăng ký, vui lòng kiểm tra lại")
                }
            }

            SessionType.REGISTERED_USER -> {
                val user = userRepo.findByPhone(phoneNumber)
                if (user != null) {
                    logger.error("Send OTP [{}] phone [{}] exist already", sessionId, phoneNumber)
                    call.response.headers.append(
                        HttpHeaders.SetCookie,
                        "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax"
                    )
                    sessionRepo.deleteSessionId(type, sessionId)
                    return call.respond(HttpStatusCode.BadRequest, "Số điện thoại đã được sử dụng, vui lòng kiểm tra lại")                }
            }

            else -> {
                // do nothing
            }
        }

        logger.debug("Send OTP [{}] phone [{}], otp [{}], refId [{}]", sessionId, phoneNumber, otp, refId)
        val otpResp: ZnsMessage.ZaloMessageResponse = notificationGateway.sendZaloOTP(znsConfig[type]!!, refId, phoneNumber, otp)
            ?: kotlin.run {
                logger.error("Send OTP [{}] phone [{}] send failed refId [{}]", sessionId, phoneNumber, refId)
                call.response.headers.append(
                    HttpHeaders.SetCookie,
                    "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax"
                )
                sessionRepo.deleteSessionId(type, sessionId)
                return call.respond(HttpStatusCode.InternalServerError, "Phiên làm việc có lỗi, vui long thử lại")
            }

        logger.debug("Send OTP [{}] phone [{}], code [{}], message [{}], data [{}], refId [{}]", sessionId, phoneNumber, otpResp.code, otpResp.message, otpResp.data, refId)

        if (otpResp.code != 0) {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(type, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Không gửi được mã OTP, vui long thử lại")
        }

        val expiredTime = Date().time / 1000 + sessionConfig.expiredTimeInS

        val newData = sessionData.copy(
            status = SessionStatus.OTP_SENT,
            phone = phoneNumber,
            otp = otp,
            currentTarget = OtpTarget.PHONE,
            failedCount = 0,
            expiredTime = expiredTime
        )

        sessionRepo.save(
            type,
            sessionId,
            saveSessionData(newData),
            sessionConfig.expiredTimeInS,
        )
            ?: run {
                logger.error("Send OTP [{}] phone [{}] save redis failed, refId [{}]", sessionId, phoneNumber, refId)
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                sessionRepo.deleteSessionId(type, sessionId)
                return call.respond(HttpStatusCode.InternalServerError, "Phiên làm việc có lỗi, vui long thử lại")
            }

        call.respond(HttpStatusCode.OK)
    }

    suspend fun resendOTP(call: ApplicationCall, type: SessionType) {
        val sessionKey = "${type}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui lòng thử lại")

        val sessionValue = sessionRepo.findSession(type, sessionId)
        val sessionData = getSessionData(sessionValue)
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui lòng thử lại")

        if (sessionData.status != SessionStatus.OTP_SENT && sessionData.status != SessionStatus.OTP_VERIFY_FAILED) {
            logger.warn("Resend OTP [{}] called with invalid status: {}", sessionId, sessionData.status)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(type, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Yêu cầu không hợp lệ, vui lòng thử lại từ đầu.")
        }

        val otpType = sessionData.currentTarget ?: OtpTarget.PHONE
        val target = if (otpType == OtpTarget.EMAIL) sessionData.email else sessionData.phone

        if (target.isNullOrBlank()) {
            return call.respond(HttpStatusCode.BadRequest, "Không tìm thấy thông tin liên lạc, vui lòng thử lại")
        }

        // Tạo và gửi mã OTP mới
        val refId = UUID.randomUUID().toString()
        val otp = OTPGenerator.generateOTP()

        if (otpType == OtpTarget.EMAIL) {
             // Resend Email OTP
             val email = target
             logger.debug("Resend Email OTP [{}] email [{}], new otp [{}]", sessionId, email, otp)
             val success = notificationGateway.sendEmailOTP(mailConfig[type]!!, refId, email, otp)
             
             if (!success) {
                 return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, không thể gửi lại Email OTP.")
             }

             val ttlInSeconds = sessionConfig.expiredTimeInS
             val absoluteExpireTime = Date().time / 1000 + ttlInSeconds
             
             val newData = sessionData.copy(
                 status = SessionStatus.OTP_SENT,
                 otp = otp,
                 failedCount = 0,
                 expiredTime = absoluteExpireTime
             )

             sessionRepo.save(
                type,
                sessionId,
                saveSessionData(newData),
                ttlInSeconds
             )
            logger.debug("Resend Email OTP [{}] email [{}], new otp [{}]", sessionId, email, otp)
             return call.respond(HttpStatusCode.OK, "Mã OTP mới đã được gửi tới email.")

        } else {
            // Resend Phone OTP
            val phoneNumber = target
             // Kiểm tra sự tồn tại của người dùng, tương tự như trong hàm sendOTP
            when(type) {
                SessionType.RESET_PASSWORD, SessionType.UPDATE_PROFILE -> {
                    userRepo.findByPhone(phoneNumber) ?: run {
                        logger.error("Resend OTP [{}] for non-existent phone [{}]", sessionId, phoneNumber)
                        call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                        sessionRepo.deleteSessionId(type, sessionId)
                        return call.respond(HttpStatusCode.BadRequest, "Số điện thoại chưa đăng ký, vui lòng kiểm tra lại")
                    }
                }
                SessionType.REGISTERED_USER -> {
                    userRepo.findByPhone(phoneNumber)?.let {
                        logger.error("Resend OTP [{}] for already registered phone [{}]", sessionId, phoneNumber)
                        call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                        sessionRepo.deleteSessionId(type, sessionId)
                        return call.respond(HttpStatusCode.BadRequest, "Số điện thoại đã được sử dụng, vui lòng kiểm tra lại")
                    }
                }
            }

            logger.debug("Resend OTP [{}] phone [{}], new otp [{}], refId [{}]", sessionId, phoneNumber, otp, refId)
            val otpResp = notificationGateway.sendZaloOTP(znsConfig[type]!!, refId, phoneNumber, otp)
                ?: run {
                    logger.error("Resend OTP [{}] phone [{}] failed to call notification gateway, refId [{}]", sessionId, phoneNumber, refId)
                    return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, không thể gửi lại OTP. Vui lòng thử lại sau.")
                }

            logger.debug("Resend OTP response [{}] phone [{}], code [{}], message [{}], data [{}], refId [{}]", sessionId, phoneNumber, otpResp.code, otpResp.message, otpResp.data, refId)

            if (otpResp.code != 0) {
                return call.respond(HttpStatusCode.BadRequest, "Không gửi được mã OTP, vui lòng thử lại sau.")
            }

            val ttlInSeconds = sessionConfig.expiredTimeInS
            val absoluteExpireTime = Date().time / 1000 + ttlInSeconds
            
            val newData = sessionData.copy(
                status = SessionStatus.OTP_SENT,
                otp = otp,
                failedCount = 0,
                expiredTime = absoluteExpireTime
            )

            sessionRepo.save(
                type,
                sessionId,
                saveSessionData(newData),
                ttlInSeconds
            ) ?: run {
                logger.error("Resend OTP [{}] phone [{}] save to redis failed, refId [{}]", sessionId, phoneNumber, refId)
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                sessionRepo.deleteSessionId(type, sessionId)
                return call.respond(HttpStatusCode.InternalServerError, "Phiên làm việc có lỗi, vui lòng thử lại")
            }

            call.respond(HttpStatusCode.OK, "Mã OTP mới đã được gửi.")
        }
    }

    suspend fun verifyOTP(call: ApplicationCall, type: SessionType) {
        val sessionKey = "${type}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui long thử lại")

        val sessionValue = sessionRepo.findSession(type, sessionId)
        val sessionData = getSessionData(sessionValue)
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui long thử lại")

        val req = call.receive<VerifyOTPRequest>()
        
        if (sessionData.status != SessionStatus.OTP_SENT && sessionData.status != SessionStatus.OTP_VERIFY_FAILED) {
            sessionRepo.deleteSessionId(type, sessionId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc có lỗi, vui long thử lại")
        }

        val otpType = sessionData.currentTarget ?: OtpTarget.PHONE
        val target = if (otpType == OtpTarget.EMAIL) sessionData.email else sessionData.phone
        val otpStored = sessionData.otp
        val failed = sessionData.failedCount
        val time = sessionData.expiredTime

        if (otpType == OtpTarget.EMAIL) {
            val email = req.email
            if (email != target) {
                 return call.respond(HttpStatusCode.BadRequest, "Email không khớp, vui lòng thử lại")
            }
            
            if (otpStored != req.otp) {
                val otpExpiredTime = time
                val otpFailed = failed + 1

                if (otpFailed >= sessionConfig.maxOtpAttempts) {
                    sessionRepo.deleteSessionId(type, sessionId)
                    call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                    return call.respond(HttpStatusCode.TooManyRequests, "Mã OTP nhập không chính xác quá ${sessionConfig.maxOtpAttempts} lần, vui lòng thử lại")
                }

                val newData = sessionData.copy(
                    status = SessionStatus.OTP_VERIFY_FAILED,
                    failedCount = otpFailed
                )

                sessionRepo.save(
                    type,
                    sessionId,
                    saveSessionData(newData),
                    otpExpiredTime - Date().time / 1000
                ) ?: run {
                    sessionRepo.deleteSessionId(type, sessionId)
                    call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                    return call.respond(HttpStatusCode.InternalServerError, "Phiên làm việc có lỗi, vui long thử lại")
                }

                return call.respond(HttpStatusCode.Gone,"Mã OTP nhập không chính xác. Bạn còn ${sessionConfig.maxOtpAttempts - otpFailed} lần thử.")
            }
            
            // Success Email Verify
            val newData = sessionData.copy(
                status = SessionStatus.OTP_VERIFIED, // or COMPLETED since both verified?
                isEmailVerified = true
            )
            // If phone also verified (it should be), then we are effectively done with verification steps
            
            sessionRepo.save(type, sessionId, saveSessionData(newData), sessionConfig.expiredTimeInS)
                ?: run {
                    logger.error("Verify OTP [{}] email [{}] save redis failed", sessionId, target)
                    sessionRepo.deleteSessionId(type, sessionId)
                    call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                    return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc có lỗi, vui long thử lại")
                }

        } else {
            // PHONE Verify
            val phoneNumber = normalizePhoneNumber(req.phone ?: "") ?: run {
                logger.error("Verify OTP [{}] phone [{}] normalize failed", sessionId, req.phone)
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                sessionRepo.deleteSessionId(type, sessionId)
                return call.respond(HttpStatusCode.BadRequest, "Số điện thoại không đúng, vui lòng kiểm tra lại")
            }
            
            if (target != phoneNumber) {
                sessionRepo.deleteSessionId(type, sessionId)
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc có lỗi, vui long thử lại")
            }

            if (otpStored != req.otp) {
                val otpExpiredTime = time
                val otpFailed = failed + 1
                if (otpFailed >= sessionConfig.maxOtpAttempts) {
                    sessionRepo.deleteSessionId(type, sessionId)
                    call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                    return call.respond(HttpStatusCode.TooManyRequests, "Mã OTP nhập không chính xác quá ${sessionConfig.maxOtpAttempts} lần, vui lòng thử lại")
                }

                val newData = sessionData.copy(
                    status = SessionStatus.OTP_VERIFY_FAILED,
                    failedCount = otpFailed
                )

                sessionRepo.save(
                    type,
                    sessionId,
                    saveSessionData(newData),
                    otpExpiredTime - Date().time / 1000
                ) ?: run {
                    sessionRepo.deleteSessionId(type, sessionId)
                    call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                    return call.respond(HttpStatusCode.InternalServerError, "Phiên làm việc có lỗi, vui long thử lại")
                }

                return call.respond(HttpStatusCode.Gone,"Mã OTP nhập không chính xác. Bạn còn ${sessionConfig.maxOtpAttempts - otpFailed} lần thử.")
            }

            // Success Phone Verify
            val newData = sessionData.copy(
                status = SessionStatus.OTP_VERIFIED,
                isPhoneVerified = true
            )

            sessionRepo.save(type, sessionId, saveSessionData(newData), sessionConfig.expiredTimeInS)
                ?: run {
                    logger.error("Verify OTP [{}] phone [{}] save redis failed", sessionId, phoneNumber)
                    sessionRepo.deleteSessionId(type, sessionId)
                    call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                    return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc có lỗi, vui long thử lại")
                }
        }

        call.respond(HttpStatusCode.OK)
    }

    suspend fun createUser(call: ApplicationCall) {
        val type = SessionType.REGISTERED_USER
        val sessionKey = "${type}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui long thử lại")

        val sessionValue = sessionRepo.findSession(type, sessionId)
        val sessionData = getSessionData(sessionValue)
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui long thử lại")

        val req = call.receive<CreateUserRequest>()
        val phoneNumber = normalizePhoneNumber(req.phone) ?: run {
            logger.error("Create user [{}] phone [{}] normalize failed", sessionId, req.phone)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(type, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Số điện thoại không đúng, vui lòng kiểm tra lại")
        }

        // Check phone verified; email chỉ bắt buộc verified nếu emailVerification.enable=true
        if (!sessionData.isPhoneVerified || (enableEmailVerification && !sessionData.isEmailVerified)) {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(type, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc chưa hoàn thành xác thực, vui lòng thử lại")
        }

        if (sessionData.phone != phoneNumber) {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(type, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Số điện thoại không khớp, vui lòng thử lại")
        }

        if (enableEmailVerification) {
            if (sessionData.email != req.email) {
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                sessionRepo.deleteSessionId(type, sessionId)
                return call.respond(HttpStatusCode.BadRequest, "Email không khớp, vui lòng thử lại")
            }
        } else {
            // Email chưa qua OTP nên chưa được kiểm tra ở đâu khác - tự làm lại 2 kiểm tra
            // mà nhánh gửi Email OTP trong sendOTP() vốn đảm nhiệm (blank + trùng lặp).
            if (req.email.isBlank()) {
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                sessionRepo.deleteSessionId(type, sessionId)
                return call.respond(HttpStatusCode.BadRequest, "Vui lòng nhập email")
            }
            userRepo.findByEmail(req.email)?.let {
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                sessionRepo.deleteSessionId(type, sessionId)
                return call.respond(HttpStatusCode.BadRequest, "Email đã được sử dụng")
            }
        }

        if (sessionData.otp != req.otp) {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(type, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Mã OTP không khớp, vui lòng thử lại")
        }

        val user = CustomerUserPOJO(
            phone = phoneNumber,
            password = hashPassword(req.password, salt),
            name = req.name,
            email = req.email,
            avatar = req.avatar
        )

        // Clear session cookie and session data *before* attempting DB operation
        // This ensures the registration session is consumed.
        call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
        sessionRepo.deleteSessionId(type, sessionId)

        try {
            userRepo.save(user)
            // Log success as INFO level
            logger.info("Create user [{}] phone [{}] success", sessionId, phoneNumber)
            call.respond(HttpStatusCode.OK)
        } catch (e: MongoWriteException) {
            if (e.error.code == 11000) {
                logger.warn(
                    "Create user attempt failed for session [{}], phone [{}]: User already exists (duplicate key).",
                    sessionId,
                    phoneNumber,
                    e
                )
                call.respond(HttpStatusCode.Conflict, "Người dùng với số điện thoại này đã tồn tại.")
            } else {
                logger.error(
                    "Create user attempt failed for session [{}], phone [{}]: MongoDB write error.",
                    sessionId,
                    phoneNumber,
                    e
                )
                call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống khi tạo người dùng, vui lòng thử lại.")
            }
        } catch (e: Throwable) {
            logger.error(
                "Create user attempt failed for session [{}], phone [{}]: Unexpected error.",
                sessionId,
                phoneNumber,
                e
            )
            call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống không mong muốn, vui lòng thử lại.")
        }
    }

    suspend fun resetPasswordUser(call: ApplicationCall) {
        val type = SessionType.RESET_PASSWORD
        val sessionKey = "${type}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui long thử lại")

        val sessionValue = sessionRepo.findSession(type, sessionId)
        val sessionData = getSessionData(sessionValue)
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui long thử lại")

        val req = call.receive<ResetPasswordRequest>()
        val phoneNumber = normalizePhoneNumber(req.phone) ?: run {
            logger.error("Reset password [{}] phone [{}] normalize failed", sessionId, req.phone)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(type, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Số điện thoại không đúng, vui lòng kiểm tra lại")
        }

        val user = userRepo.findByPhone(phoneNumber) ?: kotlin.run {
            logger.error("Reset password [{}] phone [{}] user not exist", sessionId, phoneNumber)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(type, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc có lỗi, vui long thử lại")
        }

        // Use default OTP_VERIFIED (Phone verified) for Reset Password flow
        // Reset password usually just verifies phone
        if (sessionData.status != SessionStatus.OTP_VERIFIED) {
            // Check if phone verified
            if (!sessionData.isPhoneVerified) {
                sessionRepo.deleteSessionId(type, sessionId)
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc có lỗi, vui long thử lại")
            }
        }

        if (sessionData.phone != phoneNumber) {
            sessionRepo.deleteSessionId(type, sessionId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            return call.respond(HttpStatusCode.BadRequest, "Số điện thoại không khớp, vui lòng tải lại trang")
        }

        if (sessionData.otp != req.otp) {
            sessionRepo.deleteSessionId(type, sessionId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            return call.respond(HttpStatusCode.BadRequest, "OTP không khớp, vui lòng tải lại trang")
        }

        call.response.headers.append(HttpHeaders.SetCookie, "${sessionKey}=; Path=/; HttpOnly; Secure; SameSite=Lax")

        userRepo.updatePassword(user.phone, hashPassword(req.password, salt))

        logger.error("Reset password [{}] phone [{}] success", sessionId, phoneNumber)

        sessionRepo.deleteSessionId(type, sessionId)

        call.respond(HttpStatusCode.OK)
    }

    suspend fun updateProfile(call: ApplicationCall) {
        val userId = call.request.headers.get("X-Customer-User-Id") ?: kotlin.run {
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }
        val req = call.receive<UpdateProfileRequest>()
        val user = userRepo.findById(userId) ?: kotlin.run {
            return call.respond(HttpStatusCode.Unauthorized, "Người dùng không tồn tại")
        }

        // Case 1: Email not changed -> Update name only
        if (req.email == user.email) {
            userRepo.updateProfile(userId, req.name, req.email)
            return call.respond(HttpStatusCode.OK)
        }

        // Case 2: Email changed -> Need OTP Verification
        val type = SessionType.UPDATE_PROFILE
        val sessionKey = "${type}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui lòng thử lại")

        val sessionValue = sessionRepo.findSession(type, sessionId)
        val sessionData = getSessionData(sessionValue)
            ?: return call.respond(HttpStatusCode.BadRequest, "Phiên làm việc đã hết hạn, vui lòng thử lại")

        if (sessionData.status != SessionStatus.OTP_VERIFIED && sessionData.status != SessionStatus.COMPLETED) {
             return call.respond(HttpStatusCode.BadRequest, "Chưa xác thực email mới")
        }
        
        // Ensure the verified email matches the requested email
        if (sessionData.email != req.email) {
             return call.respond(HttpStatusCode.BadRequest, "Email xác thực không khớp")
        }

        if (sessionData.otp != req.otp) {
             return call.respond(HttpStatusCode.BadRequest, "OTP không khớp")
        }

        // Update with new email
        userRepo.updateProfile(userId, req.name, req.email)
        
        // Clean up session
        sessionRepo.deleteSessionId(type, sessionId)
        call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
        
        call.respond(HttpStatusCode.OK)
    }

    suspend fun getUserProfile(call: ApplicationCall) {
        val userId = call.request.headers.get("X-Customer-User-Id") ?: kotlin.run {
            logger.error("Get user profile but missing user in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }
        val user = userRepo.findById(userId) ?: kotlin.run {
            logger.error("Get user profile [{}], not found user", userId)
            return call.respond(HttpStatusCode.Unauthorized, "Người dùng chưa đăng ký")
        }
        call.respond(user.toUserProfile())
    }
}