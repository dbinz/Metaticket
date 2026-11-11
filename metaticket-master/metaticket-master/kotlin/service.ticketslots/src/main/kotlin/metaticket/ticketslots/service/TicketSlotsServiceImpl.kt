package metaticket.ticketslots.service

import com.google.protobuf.ByteString
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.delay
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.order.datastructure.OrderStatus
import metaticket.show.datastructure.CalendarRules
import metaticket.ticketslots.configuration.Environment
import metaticket.ticketslots.constants.prefixSlotQRCode
import metaticket.ticketslots.dbservice.TicketSlotDatabaseService
import metaticket.ticketslots.grpc.TicketSlotsMessage
import metaticket.ticketslots.grpc.TicketSlotsServiceGrpcKt
import metaticket.ticketslots.pojo.db.TicketSlotOptStatus
import metaticket.ticketslots.pojo.db.TicketSlotStatus
import java.util.Base64


import metaticket.ticketslots.gateway.OrderGateway

class TicketSlotsServiceImpl(
    private val dbS: TicketSlotDatabaseService,
    private val orderGateway: OrderGateway,
): TicketSlotsServiceGrpcKt.TicketSlotsServiceCoroutineImplBase(), Logging {
    override suspend fun getQRCode(request: TicketSlotsMessage.GetQRCodeRequest): TicketSlotsMessage.GetQRCodeResponse {
        val orderStatus = orderGateway.getOrderStatus(request.orderId)
        if (orderStatus != OrderStatus.PaymentSucceed) {
            throw StatusException(Status.FAILED_PRECONDITION.withDescription("Order is not successful"))
        }
        var ticketSlots = dbS.findTicketSlotByOrderId(request.orderId)
            val numberOfQR = ticketSlots.count { doc ->
                doc.states.firstOrNull() != null && doc.states.first().status != TicketSlotStatus.CREATED
            }
            if (request.hasTimeoutFull() && numberOfQR != request.requiredQrcode) {
                delay(request.timeoutFull)
                ticketSlots = dbS.findTicketSlotByOrderId(request.orderId)
                val numberOfQR = ticketSlots.count { doc ->
                    doc.states.firstOrNull() != null && doc.states.first().status != TicketSlotStatus.CREATED
                }
                if (numberOfQR < request.requiredQrcode) {
                    logger.warn("TicketSlot for order {} not fully generated QR: {}/{}", request.orderCode, ticketSlots.size, request.requiredQrcode)
                    throw StatusException(Status.INVALID_ARGUMENT.withDescription("Not fully qrcode"))
                }
            }
            val qrcodes = ticketSlots.mapNotNull { doc ->
                doc.states.firstOrNull()?.let { state ->
                    TicketSlotsMessage.TicketSlotQRCodeProto.newBuilder()
                        .setSlotId(doc.id)
                        .setTicketId(doc.ticketId)
                        .setPriceCat(doc.priceCat)
                        .setSid(doc.sid)
                        .setTicketIdx(doc.ticketIdx.toString())
                        .setSlotIdx(doc.slotIdx.toString())
                        .setSlotStateIdx(state.stateIdx.toString())
                        .setSlotStatus(state.status.toString())
                        .apply {
                            state.qrcodeString?.let { qrCode ->
                                try {
                                    // Database stores as Base64 String, convert to Bytes for gRPC transfer
                                    this.qrCode = ByteString.copyFrom(Base64.getDecoder().decode(qrCode))
                                } catch (e: Exception) {
                                    logger.error("Failed to decode QR code for slot {}", doc.id, e)
                                    throw StatusException(Status.INVALID_ARGUMENT.withDescription("invalid qrcode of slot ${doc.id}"))
                                }
                            }
                        }
                        .build()
                }
            }

            return TicketSlotsMessage.GetQRCodeResponse.newBuilder()
                .setOrderId(request.orderId)
                .setOrderCode(request.orderCode)
                .addAllTicketSlots(qrcodes)
                .build()
        }

        override suspend fun scanTicketSlot(request: TicketSlotsMessage.ScanTicketSlotRequest): TicketSlotsMessage.ScanTicketSlotResponse {
        if (!request.verifyCode.startsWith(prefixSlotQRCode))
            throw StatusException(Status.INVALID_ARGUMENT.withDescription("Mã QR không hợp lệ"))

        // A forged or damaged code fails decoding/parsing; report it as invalid instead of an UNKNOWN gRPC error.
        val (slotId, stateIdx) = runCatching {
            val parts = Checksum.decode(request.verifyCode.substring(prefixSlotQRCode.length), Environment.qrcodeSecretKey).split(":")
            parts[0] to parts[1].toInt()
        }.getOrElse { throw StatusException(Status.INVALID_ARGUMENT.withDescription("Mã QR không hợp lệ")) }

        val doc = dbS.findByIdWithCurrentStateOnly(slotId)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Mã QR không tồn tại"))

        if (doc.sessionId != request.sessionId)
            throw StatusException(Status.PERMISSION_DENIED.withDescription("Mã QR không thuộc sự kiện này"))

        val orderStatus = orderGateway.getOrderStatus(doc.orderId)
        if (orderStatus != OrderStatus.PaymentSucceed) {
            throw StatusException(Status.FAILED_PRECONDITION.withDescription("Đơn hàng chưa thanh toán thành công"))
        }

        if (doc.currentStateIdx != stateIdx) {
            logger.error("scanTicketSlot, order {}, ticket: cat {}, sid {}, tkIdx {}, slotIdx {} not match stateIdx {}-{}", doc.orderCode, doc.priceCat, doc.sid, doc.ticketIdx, doc.slotIdx, doc.currentStateIdx, stateIdx)
            throw StatusException(Status.PERMISSION_DENIED.withDescription("Mã QR đã được chuyển nhượng"))
        }

        logger.info("scanTicketSlot, order {}, ticket: cat {}, sid {}, tkIdx {}, slotIdx {} stateIdx {}, status {}, opt status {}", doc.orderCode, doc.priceCat, doc.sid, doc.ticketIdx, doc.slotIdx, doc.currentStateIdx, doc.currentStatus, doc.optStatus)
        if (doc.optStatus != TicketSlotOptStatus.OPENED) {
            throw StatusException(Status.FAILED_PRECONDITION.withDescription("Mã QR chưa được mở! vui lòng liên hệ hỗ trợ"))
        }
        CalendarRules.checkInRejection(doc.visitDate)?.let {
            logger.info("scanTicketSlot, order {}, slot {} rejected, visit date {}", doc.orderCode, doc.id, doc.visitDate)
            throw StatusException(Status.FAILED_PRECONDITION.withDescription(it))
        }
        if (doc.currentStatus != TicketSlotStatus.QR_GENERATED) {
            when(doc.currentStatus) {
                TicketSlotStatus.CHECKED_IN -> throw StatusException(Status.ALREADY_EXISTS.withDescription("Mã QR đã được quét! Vui lòng quét mã khác"))
                TicketSlotStatus.CREATED -> throw StatusException(Status.FAILED_PRECONDITION.withDescription("Mã QR chưa có! Vui lòng liên hệ hỗ trợ"))
                else -> throw StatusException(Status.FAILED_PRECONDITION.withDescription("Mã QR chưa sẵn sàng"))
            }
        }

        logger.info("scanTicketSlot, order {}, ticket: cat {}, sid {}, tkIdx {}, slotIdx {} stateIdx {}, updating new status {}", doc.orderCode, doc.priceCat, doc.sid, doc.ticketIdx, doc.slotIdx, doc.currentStateIdx, TicketSlotStatus.CHECKED_IN)
        dbS.updateStatusTicketSlot(slotId, stateIdx, TicketSlotStatus.QR_GENERATED, TicketSlotStatus.CHECKED_IN)
            ?: run {
                logger.error("scanTicketSlot, order {}, ticket: cat {}, sid {}, tkIdx {}, slotIdx {} stateIdx {}, failed to update status {}", doc.orderCode, doc.priceCat, doc.sid, doc.ticketIdx, doc.slotIdx, doc.currentStateIdx, TicketSlotStatus.CHECKED_IN)
                throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống trong quá trình quét mã, vui lòng liên hệ hỗ trợ"))
            }

        logger.info("scanTicketSlot, order {}, ticket: cat {}, sid {}, tkIdx {}, slotIdx {} stateIdx {}, updated status {}", doc.orderCode, doc.priceCat, doc.sid, doc.ticketIdx, doc.slotIdx, doc.currentStateIdx, TicketSlotStatus.CHECKED_IN)

        return TicketSlotsMessage.ScanTicketSlotResponse.newBuilder()
            .setOrderCode(doc.orderCode)
            .setSid(doc.sid)
            .setCat(doc.priceCat)
            .setTicketIdx(doc.ticketIdx)
            .setSlotIdx(doc.slotIdx)
            .build()
    }
}