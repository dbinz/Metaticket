package metaticket.qrcode.controller

import io.grpc.Status
import io.grpc.StatusException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.order.datastructure.OrderStatus
import metaticket.qrcode.cofiguration.Environment
import metaticket.qrcode.constants.prefixQRCode
import metaticket.qrcode.constants.prefixSlotQRCode
import metaticket.qrcode.db.DatabaseService
import metaticket.qrcode.gateway.OrderGateway
import metaticket.qrcode.gateway.TicketSlotsGateway
import metaticket.qrcode.pojo.*
import metaticket.ticketslots.grpc.TicketSlotsMessage
import org.bson.types.ObjectId

class QRCodeController(
    private val env: ApplicationEnvironment,
    private val dbS: DatabaseService,
    private val ticketSlotsGateway: TicketSlotsGateway,
    private val orderGateway: OrderGateway,
) : Logging {

    private val linkBaseUrl = env.config.property("qrscanner.linkBaseUrl").getString()

    suspend fun generateLinkScanner(call: ApplicationCall) {
        val sessionId = call.parameters["sessionId"]
            ?: return call.respond(HttpStatusCode.BadRequest, "Missing or malformed show id")

        val sessionChecksum = Checksum.encode(sessionId, Environment.qrcodeSecretKey)
        val link = "$linkBaseUrl/$sessionId?privateCode=${sessionChecksum}"

        call.respond(HttpStatusCode.OK, link)
    }

    suspend fun verifyLinkScanner(call: ApplicationCall) {
        val sessionId = call.parameters["session"]
            ?: return call.respond(HttpStatusCode.BadRequest, "Missing or malformed show id")

        val privateCode = call.parameters["privateCode"]
            ?: return call.respond(HttpStatusCode.BadRequest, "Missing or malformed private code")

        val sessionChecksum = Checksum.encode(sessionId, Environment.qrcodeSecretKey)

        if (sessionChecksum != privateCode)
            return call.respond(HttpStatusCode.Forbidden, "Không có quyền truy cập trang")

        return call.respond(HttpStatusCode.OK)
    }

    suspend fun scanCode(call: ApplicationCall) {
        val req = call.receive<ScanQRCodeRequest>()

        val sessionChecksum = Checksum.encode(req.session, Environment.qrcodeSecretKey)

        if (sessionChecksum != req.privateCode)
            return call.respond(HttpStatusCode.Forbidden, "Không có quyền quét mã sự kiện này")

        if (!req.verifyCode.startsWith(prefixQRCode)) {
            if (req.verifyCode.startsWith(prefixSlotQRCode)) {
                logger.info("qrcode slot {}", req.verifyCode)
                try {
                    val request = TicketSlotsMessage.ScanTicketSlotRequest.newBuilder()
                        .setSessionId(req.session)
                        .setVerifyCode(req.verifyCode)
                        .build()
                    val resp = ticketSlotsGateway.scanTicketSlot(request)
                    return call.respond(HttpStatusCode.OK, QRCodeTicketInfo(
                        orderCode = resp.orderCode,
                        cat = resp.cat,
                        sid = resp.sid,
                        ticketIdx = resp.ticketIdx,
                        slotIdx = resp.slotIdx
                    ))
                } catch (ex: StatusException) {
                    logger.error("qrcode slot {} failed with error {} {}", req.verifyCode, ex.status, ex.message)
                    // Only the description reaches the scanner: ex.message carries the gRPC code prefix
                    // ("FAILED_PRECONDITION: ..."), which the staff at the gate cannot read.
                    val message = ex.status.description?.takeIf { it.isNotBlank() }
                        ?: "Lỗi hệ thống trong quá trình quét mã, vui lòng liên hệ hỗ trợ"
                    return call.respond(scanHttpStatus(ex.status.code), message)
                } catch (t: Throwable) {
                    logger.error("qrcode slot {} failed with error... ", req.verifyCode, t)
                    return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống trong quá trình quét mã, vui lòng liên hệ hỗ trợ")
                }
            }
            return call.respond(HttpStatusCode.PreconditionFailed, "Mã QR không hợp lệ")
        }

        // A forged or damaged code fails base64/AES decoding; answer 400 instead of a bare 500.
        val qrId = runCatching { Checksum.decode(req.verifyCode.substring(prefixQRCode.length), Environment.qrcodeSecretKey) }
            .getOrNull()
            ?.takeIf { ObjectId.isValid(it) }
            ?: return call.respond(HttpStatusCode.BadRequest, "Mã QR không hợp lệ")

        val doc = dbS.fetchById(qrId) ?: return call.respond(HttpStatusCode.NotFound, "Mã không tồn tại")

        if (doc.sessionId != req.session) return call.respond(HttpStatusCode.Forbidden, "Mã QR không thuộc sự kiện này")

        val orderStatus = orderGateway.getOrderStatus(doc.orderId)
        if (orderStatus != OrderStatus.PaymentSucceed) {
            return call.respond(HttpStatusCode.PreconditionFailed, "Đơn hàng chưa thanh toán thành công")
        }

        if (doc.disable == true) return call.respond(HttpStatusCode.Forbidden, "Mã QR đã bị khoá")

        val docAfter = dbS.markQRCodeScanned(doc.id, 1) ?: run {
            logger.error("qrcode order {} ticket {} {} idx {} scanned already", doc.orderCode, doc.cat, doc.sid, doc.no)
            return call.respond(HttpStatusCode.Conflict, "Mã QR đã được quét! Vui lòng quét mã khác")
        }

        logger.info("qrcode order {} ticket {} {} idx {} scanned {} times", doc.orderCode, doc.cat, doc.sid, doc.no, docAfter.scanned)

        val orderInfo = QRCodeTicketInfo(doc.orderCode, doc.cat, doc.sid, doc.no)

        return call.respond(HttpStatusCode.OK, orderInfo)
    }

    /** HTTP status for a ticket-slot scan rejected by service.ticketslots, so the scanner can tell outcomes apart. */
    private fun scanHttpStatus(code: Status.Code): HttpStatusCode = when (code) {
        Status.Code.INVALID_ARGUMENT -> HttpStatusCode.BadRequest
        Status.Code.NOT_FOUND -> HttpStatusCode.NotFound
        Status.Code.PERMISSION_DENIED -> HttpStatusCode.Forbidden
        Status.Code.ALREADY_EXISTS -> HttpStatusCode.Conflict
        Status.Code.FAILED_PRECONDITION -> HttpStatusCode.PreconditionFailed
        Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> HttpStatusCode.ServiceUnavailable
        else -> HttpStatusCode.InternalServerError
    }

    suspend fun fetchOrderQRCode(call: ApplicationCall) {
        val req = call.receive<FetchOrderQRCodeRequest>()
        val disabled = mutableListOf<String>()
        val enabled = mutableListOf<String>()
        dbS.fetchByShowAndSession(req.showId, req.sessionId, req.orderIds?.map { Checksum.decode(it, Environment.secretKey) })
            .groupBy { it.orderId }.forEach { (orderId, qrcodes) ->
                val orderIdEncoded = Checksum.encode(orderId, Environment.secretKey)
                if (qrcodes.all { it.disable == true }) disabled.add(orderIdEncoded)
                else enabled.add(orderIdEncoded)
            }
        call.respond(HttpStatusCode.OK, FetchOrderQRCodeResponse(disabled = disabled, enabled = enabled))
    }

    suspend fun disableOrderQrCode(call: ApplicationCall) {
        val req = call.receive<DisableOrderQRCodeRequest>()
        val orderId = Checksum.decode(req.orderId, Environment.secretKey)
        val count = dbS.updateDisableStateOrder(orderId, true)
        logger.info("disabled order qrcode {}, {} qrcode", orderId, count)
        call.respond(HttpStatusCode.OK, DisableEnableQRCodeResponse(orderId, count.toInt()))
    }

    suspend fun enableOrderQrCode(call: ApplicationCall) {
        val req = call.receive<EnableOrderQRCodeRequest>()
        val orderId = Checksum.decode(req.orderId, Environment.secretKey)
        val count = dbS.updateDisableStateOrder(orderId, false)
        logger.info("enabled order qrcode {}, {} qrcode", orderId, count)
        call.respond(HttpStatusCode.OK, DisableEnableQRCodeResponse(orderId, count.toInt()))
    }
}