package metaticket.ticketslots.controller

import io.grpc.StatusException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.delay
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.common.utils.OTPGenerator
import metaticket.common.utils.normalizePhoneNumber
import metaticket.ticketslots.cache.ShowCache
import metaticket.ticketslots.configuration.Environment
import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.dbservice.TicketSlotDatabaseService
import metaticket.ticketslots.gateway.CaptchaGateway
import metaticket.ticketslots.gateway.CustomerUserGateway
import metaticket.ticketslots.gateway.NotificationGateway
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeEvent
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeProducer
import metaticket.ticketslots.model.*
import metaticket.ticketslots.pojo.db.TicketSlotStatus
import metaticket.ticketslots.repository.SessionRepository
import metaticket.ticketslots.dlq.storage.QRCodeEventProducerDLQStorage
import metaticket.ticketslots.pojo.db.TicketSlotDoc
import metaticket.ticketslots.pojo.db.TicketSlotOptStatus
import org.bson.types.ObjectId
import java.time.Duration
import java.util.Date
import java.util.*
import kotlin.math.pow

class TicketSlotsCtrl constructor(
    private val db: TicketSlotDatabaseService,
    private val config: ApplicationConfig,
    private val captchaClient: CaptchaGateway,
    private val notificationGateway: NotificationGateway,
    private val znsConfig: Map<ZNSType, ZNSConfig>,
    private val sessionRepo: SessionRepository,
    private val userGateway: CustomerUserGateway,
    private val generateTicketSlotQRCodeProducer: GenerateTicketSlotQRCodeProducer,
    private val qrCodeProducerDLQStorage: QRCodeEventProducerDLQStorage,
    private val showCacheService: ShowCache,
): Logging {
    private val topicGenerateTicketSlotQRCode = config.property("kafka.topicGenerateTicketSlotQRCode").getString()
    private val enableCaptcha = config.property("captcha.enable").getString().toBoolean()
    private val captchaGateway = config.property("captcha.captchaGateway").getString()
    private val siteKeyCaptcha = config.property("captcha.${captchaGateway}.siteKey").getString()

    suspend fun getCaptchaSetting(call: ApplicationCall) = call.respond(
        CaptchaSettingResponse(
            enableCaptcha,
            captchaGateway,
            siteKeyCaptcha
        )
    )

    suspend fun initSessionId(call: ApplicationCall) {
        val sessionKey = "${ZNSType.transferTicketSlot}_SessionId"

        // Xác minh Captcha ngay tại bước khởi tạo session
        if (enableCaptcha) {
            val req = try { call.receive<InitSessionRequest>() } catch (e: Throwable) {
                logger.error("Init session [{}] missing request body for captcha", sessionKey, e)
                return call.respond(HttpStatusCode.BadRequest, "Vui lòng hoàn thành xác minh Captcha.")
            }

            if (req.captchaToken == null) {
                logger.error("Init session [{}] missing captcha token", sessionKey)
                return call.respond(HttpStatusCode.Forbidden, "Vui lòng hoàn thành xác minh là con người.")
            }
            if (!captchaClient.isCaptchaSuccess(req.captchaToken)) {
                logger.warn("Init session [{}] failed captcha verification.", sessionKey)
                return call.respond(HttpStatusCode.Forbidden, "Kiểm tra captcha không chính xác, vui lòng thử lại.")
            }
        }

        val sessionId = UUID.randomUUID().toString()
        sessionRepo.save(ZNSType.transferTicketSlot, sessionId, SessionStatus.CREATED, znsConfig[ZNSType.transferTicketSlot]!!.expiredTimeInS)
            ?: run {
                logger.error("Init session [{}] cannot save to redis", sessionKey)
                return call.respond(HttpStatusCode.InternalServerError,  "Lỗi hệ thống, vui lòng thử lại")
            }
        call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=$sessionId; Path=/; HttpOnly; Secure; SameSite=Lax")
        call.respond(HttpStatusCode.OK)
    }

    suspend fun fetchTicketSlotsByShow(call: ApplicationCall) {
        val (showId, sessionId) = call.receive<FetchTicketSlotsByShowRequest>()
        val slots = db.findSlotsByShow(showId, sessionId)
        return call.respond(HttpStatusCode.OK, slots.map { it.toTicketSlotResponse() })
    }

    suspend fun fetchTicketSlotsByOwner(call: ApplicationCall) {
        val userId = call.request.headers.get("X-Customer-User-Id") ?: kotlin.run {
            logger.error("Get customer user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }
        val customerPhone = call.request.headers.get("X-Customer-Phone") ?: run {
            logger.error("resendTransferOTP: Missing current owner phone in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng (người chuyển)")
        }

        try {
            val doc = db.findSlotsByOwner(userId, customerPhone)
            val ticketSlots = doc.map { it.toTicketSlotResponse() }
            return call.respond(HttpStatusCode.OK, ticketSlots)
        } catch (t: Throwable) {
            logger.error("Failed to fetch ticket slots for {}", userId, t)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }
    }

    suspend fun getQrCode(call: ApplicationCall) {
        val userId = call.request.headers.get("X-Customer-User-Id") ?: kotlin.run {
            logger.error("Get QR code: Missing user id in header. User might not be logged in.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }
        val customerPhone = call.request.headers.get("X-Customer-Phone") ?: run {
            logger.error("resendTransferOTP: Missing current owner phone in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng (người chuyển)")
        }
        val slotId = call.parameters["slotId"]?.let { Checksum.decode(it, Environment.secretKey) }
            ?: kotlin.run {
                return call.respond(HttpStatusCode.BadRequest, "Thiếu mã vé (slotId)")
            }

        if (!ObjectId.isValid(slotId)) {
            return call.respond(HttpStatusCode.BadRequest, "Mã vé (slotId) không hợp lệ")
        }
        val stateIdx = call.parameters["stateIdx"]?.toIntOrNull() ?: kotlin.run {
            return call.respond(HttpStatusCode.BadRequest, "Thiếu hoặc sai định dạng chỉ mục trạng thái vé (stateIdx)")
        }

        try {
            // db.getQRCode đã được thiết kế để throw NoSuchElementException nếu không tìm thấy
            // hoặc trả về TicketSlotDoc với chỉ một state chứa QR code.
            val docWithQrState: TicketSlotDoc = db.getQRCode(userId, customerPhone, slotId, stateIdx)
                ?: run {
                    logger.error("Get QR code: slot not found in for slotId: {}, userId: {}, stateIdx: {}", slotId, userId, stateIdx)
                    return call.respond(HttpStatusCode.NotFound, "Mã QR hiện tại chưa khả dụng")
                }

            if (docWithQrState.optStatus != TicketSlotOptStatus.OPENED) {
                logger.warn("Get QR code: Slot {} state {} for user {} is not OPENED, status is {}.", slotId, stateIdx, userId, docWithQrState.optStatus)
                return call.respond(HttpStatusCode.Conflict, "Slot hiện tại đang không mở để xem QRcode")
            }

            // docWithQrState.states sẽ chỉ chứa 1 phần tử là state có QR code nếu tìm thấy
            val qrCodeState = docWithQrState.states?.firstOrNull() // Nên là first() vì logic getQRCode đảm bảo có 1 state
            if (qrCodeState == null || qrCodeState.qrcodeString == null) {
                logger.error("Get QR code: QR code string is null or state not found in returned doc for slotId: {}, userId: {}, stateIdx: {}", slotId, userId, stateIdx)
                return call.respond(HttpStatusCode.NotFound, "Mã QR hiện tại chưa khả dụng")
            }
            if (qrCodeState.status != TicketSlotStatus.QR_GENERATED) {
                logger.warn("Get QR code: Slot {} state {} for user {} is not QR_GENERATED, status is {}.", slotId, stateIdx, userId, qrCodeState.status)
                return call.respond(HttpStatusCode.Conflict, "Mã QR hiện tại chưa khả dụng")
            }

            return call.respond(HttpStatusCode.OK, qrCodeState.qrcodeString)
        } catch (e: NoSuchElementException) {
            logger.warn("Get QR code: Not found for slotId: {}, userId: {}, stateIdx: {}. Message: {}", slotId, userId, stateIdx, e.message)
            return call.respond(HttpStatusCode.NotFound, "Mã QR hiện tại chưa khả dụng")
        } catch (e: com.mongodb.MongoException) {
            logger.error("Get QR code: MongoDB error for slotId: {}, userId: {}, stateIdx: {}", slotId, userId, stateIdx, e)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống khi truy vấn QR code")
        }
        catch (t: Throwable) {
            logger.error("Get QR code: Failed for slotId: {}, userId: {}, stateIdx: {}", slotId, userId, stateIdx, t)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống khi lấy QR code")
        }
    }

    suspend fun updateSelectedTicketSlotsOptStatus(call: ApplicationCall, optStatus: TicketSlotOptStatus) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val req = call.receive<UpdateSelectedTicketSlotsRequest>()
        try {
            // Decode IDs before passing to database service
            val decodedIds = req.ticketSlotIds.map { id ->
                Checksum.decode(id, Environment.secretKey)
            }

            db.updateSelectedTicketSlotsOptStatus(decodedIds, optStatus)
            logger.info("updateSelectedTicketSlotsStatus: User {} successfully triggered update opt status to {} selected ticket slots {}", opUserId, optStatus, req.ticketSlotIds)
            return call.respond(HttpStatusCode.OK)
        } catch (t: Throwable) {
            logger.error("Failed to update status for selected ticket slots for opUser {}. Request: {}", opUserId, req, t)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống khi cập nhật trạng thái vé")
        }
    }

    /**
     * Gửi mã OTP để xác thực việc chuyển vé.
     * Luồng này sẽ tạo hoặc sử dụng một session để quản lý trạng thái gửi OTP.
     */
    suspend fun sendTransferOTP(call: ApplicationCall) {
        val currentOwnerId = call.request.headers.get("X-Customer-User-Id") ?: run {
            logger.warn("sendTransferOTP: Missing user id in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }
        val customerPhone = call.request.headers.get("X-Customer-Phone") ?: run {
            logger.error("verifyTransferOTP: Missing current owner phone in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng (người chuyển)")
        }

        val sessionKey = "${ZNSType.transferTicketSlot}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc đã hết hạn hoặc không tồn tại, vui lòng thử lại.")

        val request = call.receive<SendTransferOTPRequest>().apply { slotId = Checksum.decode(slotId, Environment.secretKey) }

        if (!ObjectId.isValid(request.slotId)) {
            return call.respond(HttpStatusCode.Forbidden, "Mã vé không hợp lệ.")
        }

        val sessionStatus = sessionRepo.findSession(ZNSType.transferTicketSlot, sessionId)
            ?: return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc đã hết hạn hoặc không tồn tại, vui lòng thử lại.")

        if (sessionStatus != SessionStatus.CREATED.name) {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc có lỗi hoặc đã được sử dụng, vui lòng thử lại.")
        }

        // Captcha đã được xác minh ở bước initSessionId

        val slot = db.findByIdAndOwnerIdWithCurrentStateOnly(currentOwnerId, request.slotId) ?: run {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.NotFound, "Không tìm thấy vé.")
        }

        if (slot.currentOwnerId != currentOwnerId) {
            logger.warn("sendTransferOTP: User {} attempted to send OTP for slot {} owned by {}.", currentOwnerId, request.slotId, slot.currentOwnerId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Bạn không có quyền thực hiện thao tác này trên vé này.")
        }

        val userProfile = try {
            userGateway.findUserById(currentOwnerId)
        } catch (e: StatusException) {
            logger.error("sendTransferOTP: Could not find user profile for ownerId {}. ... ", currentOwnerId, e)
            if (e.status.code == io.grpc.Status.Code.NOT_FOUND) {
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
                return call.respond(HttpStatusCode.Forbidden, e.status.description ?: "User Không tồn tại")
            } else {
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
                return call.respond(HttpStatusCode.InternalServerError, e.status.description ?: "Lỗi hệ thống, vui lòng thử lại")
            }
        } catch (t: Throwable) {
            logger.error("sendTransferOTP: Failed to fetch user profile for ownerId {}.", currentOwnerId, t)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, vui lòng thử lại")
        }

        val phoneNumber = userProfile.phone

        if (customerPhone != phoneNumber) {
            logger.warn("sendTransferOTP: Phone number mismatch for user {} attempted to send OTP for slot {} owned by {}.", currentOwnerId, request.slotId, slot.currentOwnerId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Thông tin tài khoản đã bị thay đổi, vui lòng đăng nhập lại.")
        }

        val otp = OTPGenerator.generateOTP()
        val refId = UUID.randomUUID().toString()

        logger.debug("Sending transfer OTP for user {}, slot {}, phone {}. OTP: {}, RefId: {}", currentOwnerId, request.slotId, phoneNumber, otp, refId)

        val znsConfigForTransfer = znsConfig[ZNSType.transferTicketSlot]!!
        val showName = showCacheService.getShowById(slot.showId)?.showName ?: slot.showId
        val otpResp = notificationGateway.sendZaloOTP(znsConfigForTransfer, refId, phoneNumber, otp, "${slot.priceCat} ${slot.sid}", showName)

        if (otpResp == null || otpResp.code != 0) {
            logger.error("sendTransferOTP failed for user {}, phone {}. RefId: {}. Response: {}", currentOwnerId, phoneNumber, refId, otpResp)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.InternalServerError, "Không thể gửi mã OTP, vui lòng thử lại sau.")
        }

        val expiredTime = Date().time / 1000 + znsConfigForTransfer.expiredTimeInS!!
        // Lưu thông tin vào session: PHONE:OTP:FAILED_COUNT:EXP_TIME:SLOT_ID
        val sessionValue = "$phoneNumber:$otp:0:$expiredTime:${request.slotId}"
        sessionRepo.save(
            ZNSType.transferTicketSlot,
            sessionId,
            SessionStatus.OTP_SENT,
            znsConfigForTransfer.expiredTimeInS,
            sessionValue
        ) ?: run {
            logger.error("sendTransferOTP failed to save session for user {}, slot {}. RefId: {}", currentOwnerId, request.slotId, refId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, không thể lưu thông tin phiên làm việc.")
        }

        logger.info("Transfer OTP sent successfully for user {}, slot {}. RefId: {}", currentOwnerId, request.slotId, refId)
        call.respond(HttpStatusCode.OK)
    }

    /**
     * Gửi lại mã OTP để xác thực việc chuyển vé.
     * Chức năng này chỉ hoạt động khi session đang ở trạng thái đã gửi OTP
     * nhưng chưa được xác thực.
     */
    suspend fun resendTransferOTP(call: ApplicationCall) {
        val currentOwnerId = call.request.headers.get("X-Customer-User-Id") ?: run {
            logger.warn("resendTransferOTP: Missing user id in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }
        val customerPhone = call.request.headers.get("X-Customer-Phone") ?: run {
            logger.error("resendTransferOTP: Missing current owner phone in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng (người chuyển)")
        }

        val sessionKey = "${ZNSType.transferTicketSlot}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc đã hết hạn hoặc không tồn tại, vui lòng thử lại.")

        val sessionValue = sessionRepo.findSession(ZNSType.transferTicketSlot, sessionId)
            ?: return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc đã hết hạn hoặc không tồn tại, vui lòng thử lại.")

        val sessionParts = sessionValue.split(":")
        if (sessionParts.size < 6) {
            logger.error("resendTransferOTP: Invalid session value format for sessionId {}. Value: {}", sessionId, sessionValue)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc có lỗi, vui lòng thử lại.")
        }

        val status = sessionParts[0]
        val storedPhone = sessionParts[1]
        // val storedOtp = sessionParts[2] // Not needed
        // val attemptsStr = sessionParts[3] // Will be reset
        val expiredTimeStr = sessionParts[4]
        val storedSlotId = sessionParts[5]

        // Allow resend only if an OTP was already sent but not yet verified
        if (status != SessionStatus.OTP_SENT.name && status != SessionStatus.OTP_VERIFY_FAILED.name) {
            logger.warn("resendTransferOTP: Invalid session status {} for sessionId {}.", status, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Không thể gửi lại OTP ở trạng thái hiện tại. Vui lòng thử lại từ đầu.")
        }

        // Security check: ensure the phone number from the header matches the one in the session
        if (customerPhone != storedPhone) {
            logger.warn("resendTransferOTP: Phone number mismatch for sessionId {}. Header phone: {}, Session phone: {}.", sessionId, customerPhone, storedPhone)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Thông tin xác thực không khớp, vui lòng thử lại.")
        }

        val expiredTime = expiredTimeStr.toLongOrNull() ?: run {
            logger.error("resendTransferOTP: Invalid expiration time in session for sessionId {}.", sessionId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Lỗi phiên làm việc, vui lòng thử lại.")
        }

        val ttl = expiredTime - Date().time / 1000
        if (ttl <= 0) {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc đã hết hạn, vui lòng yêu cầu OTP mới.")
        }

        val slot = db.findByIdAndOwnerIdWithCurrentStateOnly(currentOwnerId, storedSlotId) ?: run {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.NotFound, "Không tìm thấy vé.")
        }

        // Generate and send a new OTP
        val newOtp = OTPGenerator.generateOTP()
        val refId = UUID.randomUUID().toString()
        val znsConfigForTransfer = znsConfig[ZNSType.transferTicketSlot]!!

        logger.debug("Resending transfer OTP for user {}, slot {}, phone {}. New OTP: {}, RefId: {}", currentOwnerId, storedSlotId, storedPhone, newOtp, refId)

        val showName = showCacheService.getShowById(slot.showId)?.showName ?: slot.showId
        val otpResp = notificationGateway.sendZaloOTP(znsConfigForTransfer, refId, storedPhone, newOtp, slot.sid, showName)

        if (otpResp == null || otpResp.code != 0) {
            logger.error("resendTransferOTP failed for user {}, phone {}. RefId: {}. Response: {}", currentOwnerId, storedPhone, refId, otpResp)
            return call.respond(HttpStatusCode.InternalServerError, "Không thể gửi mã OTP, vui lòng thử lại sau.")
        }

        // Update the session with the new OTP and reset the attempt count
        val newSessionValue = "$storedPhone:$newOtp:0:$expiredTime:$storedSlotId"
        sessionRepo.save(
            ZNSType.transferTicketSlot,
            sessionId,
            SessionStatus.OTP_SENT, // Reset status to OTP_SENT
            ttl,
            newSessionValue
        ) ?: run {
            logger.error("resendTransferOTP failed to save session for user {}, slot {}. RefId: {}", currentOwnerId, storedSlotId, refId)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, không thể cập nhật phiên làm việc.")
        }

        logger.info("Transfer OTP resent successfully for user {}, slot {}. RefId: {}", currentOwnerId, storedSlotId, refId)
        call.respond(HttpStatusCode.OK)
    }

    /**
     * Xác thực OTP cho việc chuyển vé.
     */
    suspend fun verifyTransferOTP(call: ApplicationCall) {
        val currentOwnerId = call.request.headers.get("X-Customer-User-Id") ?: run {
            logger.error("Transfer ticket slot: Missing current owner user id in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng (người chuyển)")
        }
        val customerPhone = call.request.headers.get("X-Customer-Phone") ?: run {
            logger.error("verifyTransferOTP: Missing current owner phone in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng (người chuyển)")
        }

        val sessionKey = "${ZNSType.transferTicketSlot}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc đã hết hạn hoặc không tồn tại, vui lòng thử lại.")

        val request = call.receive<VerifyOTPRequest>().apply { slotId = Checksum.decode(slotId, Environment.secretKey) }

        val sessionValue = sessionRepo.findSession(ZNSType.transferTicketSlot, sessionId)
            ?: return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc đã hết hạn hoặc không tồn tại, vui lòng thử lại.")

        val sessionParts = sessionValue.split(":")
        if (sessionParts.size < 6) {
            logger.error("verifyTransferOTP: Invalid session value format for sessionId {}. Expected 7 parts, got {}", sessionId, sessionParts.size)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc có lỗi, vui lòng thử lại.")
        }

        val status = sessionParts[0]
        val storedPhone = sessionParts[1]
        val storedOtp = sessionParts[2]
        val attemptsStr = sessionParts[3]
        val expiredTimeStr = sessionParts[4]
        val storedSlotId = sessionParts[5]

        if (storedSlotId != request.slotId) {
            logger.warn("verifyTransferOTP: SlotId mismatch for sessionId {}. Header slotId: {}, Session slotId: {}.", sessionId, request.slotId, storedSlotId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Thông tin xác thực không khớp, vui lòng thử lại.")
        }

        if (customerPhone != storedPhone) {
            logger.warn("verifyTransferOTP: Phone number mismatch for sessionId {}. Header phone: {}, Session phone: {}.", sessionId, customerPhone, storedPhone)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Thông tin xác thực không khớp, vui lòng thử lại.")
        }

        if (status != SessionStatus.OTP_SENT.name && status != SessionStatus.OTP_VERIFY_FAILED.name) {
            logger.warn("verifyTransferOTP: Invalid session status {} for sessionId {}.", status, sessionId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc có lỗi hoặc đã hết hạn, vui lòng thử lại.")
        }

        val znsConfigForTransfer = znsConfig[ZNSType.transferTicketSlot]!!
        val maxOtpAttempts = znsConfigForTransfer.maxOtpAttempts!!

        if (storedOtp != request.otp) {
            val newAttempts = attemptsStr.toInt() + 1
            if (newAttempts >= maxOtpAttempts) {
                sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
                call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
                return call.respond(HttpStatusCode.TooManyRequests, "Bạn đã nhập sai OTP quá $maxOtpAttempts lần. Vui lòng yêu cầu OTP mới.")
            } else {
                val expiredTime = expiredTimeStr.toLong()
                val ttl = expiredTime - Date().time / 1000
                if (ttl > 0) {
                    val newSessionValue = "$storedPhone:$storedOtp:$newAttempts:$expiredTime:$storedSlotId"
                    sessionRepo.save(ZNSType.transferTicketSlot, sessionId, SessionStatus.OTP_VERIFY_FAILED, ttl, newSessionValue)
                }
                return call.respond(HttpStatusCode.Forbidden, "Mã OTP không chính xác. Bạn còn ${maxOtpAttempts - newAttempts} lần thử.")
            }
        }

        // OTP chính xác, cập nhật session sang trạng thái OTP_VERIFIED
        val newSessionValue = "$storedPhone:$storedOtp:$storedSlotId"
        sessionRepo.save(
            ZNSType.transferTicketSlot,
            sessionId,
            SessionStatus.OTP_VERIFIED,
            znsConfigForTransfer.expiredTimeInS, // Reset TTL cho bước cuối cùng
            newSessionValue
        ) ?: run {
            logger.error("verifyTransferOTP: Failed to save session to OTP_VERIFIED for sessionId {}.", sessionId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống, vui lòng thử lại.")
        }

        call.respond(HttpStatusCode.OK)
    }

    /**
     * Thực hiện chuyển vé sau khi đã xác thực OTP.
     */
    suspend fun transferTicketSlot(call: ApplicationCall) {
        val currentOwnerId = call.request.headers.get("X-Customer-User-Id") ?: run {
            logger.error("Transfer ticket slot: Missing current owner user id in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng (người chuyển)")
        }
        val customerPhone = call.request.headers.get("X-Customer-Phone") ?: run {
            logger.error("Transfer ticket slot: Missing current owner phone in header.")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng (người chuyển)")
        }

        val sessionKey = "${ZNSType.transferTicketSlot}_SessionId"
        val sessionId = call.request.cookies[sessionKey]
            ?: return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc đã hết hạn hoặc không tồn tại, vui lòng thử lại.")

        val request = call.receive<TransferTicketSlotRequest>().apply { slotId = Checksum.decode(slotId, Environment.secretKey) }

        if (!ObjectId.isValid(request.slotId)) {
            return call.respond(HttpStatusCode.Forbidden, "Mã vé (slotId) không hợp lệ.")
        }
        if (request.version <= 0) {
            return call.respond(HttpStatusCode.Forbidden, "Version của vé không hợp lệ.")
        }
        if (request.currentStateIdx < 0) {
            return call.respond(HttpStatusCode.Forbidden, "Chỉ mục trạng thái của vé không hợp lệ.")
        }

        val newOwnerPhone = normalizePhoneNumber(request.newOwnerPhone) ?: run {
            logger.warn("Transfer ticket slot: Invalid phone number {}.", request.newOwnerPhone)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Số điện thoại không đúng, vui lòng kiểm tra lại")
        }

        if (newOwnerPhone == customerPhone) {
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.BadRequest, "Không thể tự chuyển vé cho chính mình.")
        }

        val sessionValue = sessionRepo.findSession(ZNSType.transferTicketSlot, sessionId)
            ?: return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc đã hết hạn hoặc không tồn tại, vui lòng thử lại.")

        // Parse session value cho trạng thái OTP_VERIFIED: STATUS:PHONE:OTP:SLOT_ID
        val sessionParts = sessionValue.split(":")
        if (sessionParts.size < 4) {
            logger.error("transferTicketSlot: Invalid session value format for OTP_VERIFIED state. SessionId: {}", sessionId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Phiên làm việc có lỗi, vui lòng thử lại.")
        }

        val status = sessionParts[0]
        val storedPhone = sessionParts[1]
        // val storedOtp = sessionParts[2] // Không cần dùng OTP ở bước này nữa
        val storedSlotId = sessionParts[3]

        // 1. Kiểm tra trạng thái session phải là OTP_VERIFIED
        if (status != SessionStatus.OTP_VERIFIED.name) {
            logger.warn("transferTicketSlot: Invalid session status {} for sessionId {}. Expected OTP_VERIFIED.", status, sessionId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Vui lòng xác thực OTP trước khi thực hiện thao tác này.")
        }

        // 2. Re-validate tất cả thông tin để đảm bảo tính toàn vẹn
        if (customerPhone != storedPhone || request.slotId != storedSlotId) {
            logger.warn("transferTicketSlot: Context mismatch for sessionId {}. Critical failure.", sessionId)
            call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
            sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Thông tin không khớp, vui lòng thực hiện lại từ đầu.")
        }

        // Mọi thứ đã hợp lệ, xóa session và thực hiện chuyển vé
        call.response.headers.append(HttpHeaders.SetCookie, "$sessionKey=; Path=/; HttpOnly; Secure; SameSite=Lax")
        sessionRepo.deleteSessionId(ZNSType.transferTicketSlot, sessionId)

        try {
            val newUser = userGateway.findUserByPhone(newOwnerPhone) ?: run {
                logger.info("User with phone {} not found.", newOwnerPhone)
                null
            }
            val newState = db.transferTicketSlotAtomic(
                slotId = request.slotId,
                stateIdx = request.currentStateIdx,
                expectedCurrentOwnerId = currentOwnerId,
                version = request.version,
                newOwnerPhone = newOwnerPhone,
                newOwnerId = newUser?.id
            )

            if (newState != null) {
                // --- BẮT ĐẦU LOGIC GỬI THÔNG BÁO ---
                // Bọc trong try-catch để đảm bảo lỗi gửi thông báo không ảnh hưởng đến kết quả trả về
                try {
                    // Gửi QR code event với cơ chế retry và fallback
                    val qrCodeEvent = GenerateTicketSlotQRCodeEvent(request.slotId, newState.stateIdx)
                    logger.info(
                        "Sending GenerateTicketSlotQRCodeEvent for newly created ticket slot state: {}",
                        newState
                    )

                    val eventSent = sendQRCodeEventWithRetry(
                        event = qrCodeEvent,
                        maxRetries = 3,
                        useSync = true // Sử dụng sync cho transfer vì đây là thao tác quan trọng
                    )

                    if (eventSent) {
                        logger.info(
                            "Successfully sent GenerateTicketSlotQRCodeEvent for newly created ticket slot state {}",
                            newState
                        )
                    } else {
                        logger.warn(
                            "Failed to send GenerateTicketSlotQRCodeEvent for newly created ticket slot state {} after retries. Event stored in fallback storage.",
                            newState
                        )
                    }

                    val znsConfigForSuccess = znsConfig[ZNSType.notifyTicketSlot]!!
                    val slot = db.findByIdWithCurrentStateOnly(request.slotId)
                    val senderProfile = userGateway.findUserById(currentOwnerId)

                    if (slot != null) {
                        // Lấy show name từ cache
                        val showName = showCacheService.getShowById(slot.showId)?.showName ?: slot.showId

                        val templateData = mapOf(
                            "ten_nguoi_gui" to senderProfile.name,
                            "ma_kh_gui" to senderProfile.phone,
                            "su_kien" to showName,
                            "ma_ve" to slot.sid,
                            "ten_nguoi_nhan_ve" to request.newOwnerName,
                        )
                        val refId = UUID.randomUUID().toString()
                        notificationGateway.sendZaloNotification(
                            znsConfig = znsConfigForSuccess,
                            refId = refId,
                            phone = newOwnerPhone,
                            templateData = templateData
                        )
                        logger.info("Successfully queued transfer success ZNS for slot {} state {} to phone {}.", request.slotId, request.currentStateIdx, newOwnerPhone)
                    } else {
                        logger.warn("Could not find slot details for slotId {} state {} to send notification.", request.slotId, request.currentStateIdx)
                    }
                } catch (e: Throwable) {
                    logger.error("An unexpected error occurred while trying to send transfer success notification for slot {} state {}.", request.slotId, request.currentStateIdx, e)
                }
                // --- KẾT THÚC LOGIC GỬI THÔNG BÁO ---

                logger.info(
                    "Ticket slot {} state {} transferred successfully from owner {} to new owner (ID: {}, Phone: {})",
                    request.slotId, request.currentStateIdx, currentOwnerId, newUser?.id, newOwnerPhone
                )
                return call.respond(HttpStatusCode.OK)
            } else {
                logger.warn(
                    "Transfer ticket slot failed for slotId: {} state {}, from: {}, to: (ID: {}, Phone: {}). Possible race condition or invalid data.",
                    request.slotId, request.currentStateIdx, currentOwnerId, newUser?.id, request.newOwnerPhone
                )
                return call.respond(HttpStatusCode.Conflict, "Không thể chuyển vé. Vé có thể không tồn tại, không thuộc sở hữu của bạn, không ở trạng thái hợp lệ hoặc đã được cập nhật bởi một giao dịch khác.")
            }
        } catch (e: com.mongodb.MongoException) {
            logger.error("MongoDB error during transfer ticket slot {} state {}", request.slotId, request.currentStateIdx, e)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống khi chuyển vé.")
        } catch (t: Throwable) {
            logger.error("Failed to transfer ticket slot {} state {}", request.slotId, request.currentStateIdx, t)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống không mong muốn khi chuyển vé.")
        }
    }
    
    /**
     * Sends QR code event with retry mechanism and fallback storage.
     * 
     * @param event The QR code event to send
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param useSync Whether to use synchronous sending (default: false)
     * @return true if sent successfully, false otherwise
     */
    suspend fun sendQRCodeEventWithRetry(
        event: GenerateTicketSlotQRCodeEvent,
        maxRetries: Int = 3,
        useSync: Boolean = false
    ): Boolean {
        val startTime = Date()
        var lastException: Exception? = null
        var retryCount = 0
        
        for (attempt in 0..maxRetries) {
            try {
                val success = if (useSync) {
                    // Use synchronous sending for critical operations
                    generateTicketSlotQRCodeProducer.sendEventSync(event)
                } else {
                    // Use asynchronous sending for better performance
                    generateTicketSlotQRCodeProducer.sendEvent(event)
                    generateTicketSlotQRCodeProducer.flush()
                    true // Assume success for async calls
                }
                
                if (success) {
                    val responseTime = Duration.ofMillis(Date().time - startTime.time)

                    logger.info(
                        "Successfully sent QR code event for slotId {} on attempt {}",
                        event.slotId, attempt + 1
                    )
                    return true
                }
            } catch (e: Exception) {
                lastException = e
                retryCount = attempt
                
                logger.warn(
                    "Failed to send QR code event for slotId {} on attempt {}: {}",
                    event.slotId, attempt + 1, e.message
                )
                
                // Don't retry on the last attempt
                if (attempt < maxRetries) {
                    // Exponential backoff: 1s, 2s, 4s, 8s...
                    val delayMs = (1000 * (2.0.pow(attempt.toDouble()))).toLong()
                    logger.info(
                        "Retrying QR code event for slotId {} in {}ms (attempt {} of {})",
                        event.slotId, delayMs, attempt + 2, maxRetries + 1
                    )
                    delay(delayMs)
                }
            }
        }
        
        try {
            val failureReason = lastException?.message ?: "Unknown error after $maxRetries retries"
            qrCodeProducerDLQStorage.store(
                event = event,
                messageKey = event.slotId,
                failureReason = failureReason,
                sourceTopic = topicGenerateTicketSlotQRCode,
                status = FailedEventStatus.PENDING,
                errorType = "",
            )

            logger.error(
                "All retry attempts failed for QR code event slotId {}. Stored in producer DLQ storage. Last error: {}",
                event.slotId, failureReason
            )
        } catch (storageException: Exception) {
            logger.error(
                "Failed to store QR code event for slotId {} in producer DLQ storage: {}",
                event.slotId, storageException.message, storageException
            )
        }
        
        return false
    }
    
    /**
     * Sends QR code event with simple retry (backward compatibility).
     * This method provides a simpler interface for existing code.
     */
    suspend fun sendQRCodeEvent(event: GenerateTicketSlotQRCodeEvent): Boolean {
        return sendQRCodeEventWithRetry(event, maxRetries = 2, useSync = false)
    }
}