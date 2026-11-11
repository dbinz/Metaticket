package metaticket.qrcode.service

import com.google.protobuf.ByteString
import com.google.protobuf.BytesValue
import io.grpc.Status
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.qrcode.cofiguration.Environment
import metaticket.qrcode.constants.prefixQRCode
import metaticket.qrcode.db.DatabaseService
import metaticket.qrcode.db.pojo.QRCodeDocument
import metaticket.qrcode.grpc.QRCodeServiceGrpcKt
import metaticket.qrcode.grpc.QrcodeMessage
import qrcode.QRCode
import java.io.File
import java.util.Base64

class QRCodeServiceImpl(
    private val dbS: DatabaseService,
): QRCodeServiceGrpcKt.QRCodeServiceCoroutineImplBase(), Logging {

    override suspend fun generateQRCode(request: QrcodeMessage.GenerateQRCodeRequest): BytesValue {
        try {
            val document = dbS.fetch(request.orderCode, request.priceCat, request.sid, request.no) ?: run {
                val newDocument = QRCodeDocument(
                    showId = request.showId,
                    sessionId = request.sessionId,
                    orderId = request.orderId,
                    orderCode = request.orderCode,
                    eventName = request.eventName,
                    cat = request.priceCat,
                    sid = request.sid,
                    no = request.no,
                )
                dbS.insert(newDocument)
                logger.info("insert new qrcode [{}]", request.orderId)
                newDocument
            }

            val verifyCode = Checksum.encode(document.id, Environment.qrcodeSecretKey)
            val squareQRCode = QRCode.ofSquares().withSize(5).withInnerSpacing(0)
                .build("$prefixQRCode$verifyCode")
            val squarePngData = squareQRCode.renderToBytes()

            return BytesValue.of(ByteString.copyFrom(squarePngData))
        } catch (e: Throwable) {
            // Ghi log lỗi chi tiết để biết chính xác chuyện gì đã xảy ra
            logger.error("Failed to generate QRCode for order [${request.orderCode}]. Error: ${e.message}", e)

            // Trả về một lỗi gRPC cụ thể. INTERNAL là một lựa chọn tốt cho các lỗi server không mong muốn.
            throw Status.INTERNAL
                .withDescription("Server error while generating QRCode: ${e.message}")
                .withCause(e)
                .asRuntimeException()
        }
    }
}