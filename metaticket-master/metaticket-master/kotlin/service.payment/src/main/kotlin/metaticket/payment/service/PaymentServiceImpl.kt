package metaticket.payment.service

import com.google.protobuf.StringValue
import io.grpc.Status
import io.grpc.StatusException
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.payment.cache.PGAccountCache
import metaticket.payment.configuration.Environment
import metaticket.payment.database.service.PaymentDBService
import metaticket.payment.datastructure.OrderType
import metaticket.payment.datastructure.PaymentType
import metaticket.payment.grpc.PaymentMessage
import metaticket.payment.grpc.PaymentServiceGrpcKt
import metaticket.payment.pojo.db.PaymentDocument
import metaticket.payment.pojo.response.toResponse
import org.bson.types.ObjectId
import java.util.*

class PaymentServiceImpl(
    private val pgAccountCache: PGAccountCache,
    private val db: PaymentDBService,
): PaymentServiceGrpcKt.PaymentServiceCoroutineImplBase(), Logging {
    override suspend fun createPayment(request: PaymentMessage.CreatePaymentRequest): StringValue {
        try {
            var pgAccountCode = if (request.hasPgAccountCode()) request.pgAccountCode else null
            if (pgAccountCode == null) {
                pgAccountCode = pgAccountCache.getDefaultAccountCode()
                if (pgAccountCode != null) {
                    logger.info("order {} using default pg account {}", request.orderId, request.pgAccountCode)
                } else {
                    logger.error("order {} not found pg account when creating payment", request.orderId)
                    throw StatusException(io.grpc.Status.NOT_FOUND.withDescription("Không tìm thấy pg account"))
                }
            }

            val pgAccount = pgAccountCache.get(pgAccountCode) ?: kotlin.run {
                logger.error("order {} not found pg account {} when creating payment", request.orderId, pgAccountCode)
                throw StatusException(Status.NOT_FOUND.withDescription("Không tìm thấy pg account"))
            }

            // Additive, backward-compatible discriminator: absent or empty order_type resolves
            // to TICKET so existing ticket payments behave identically (Req 8.3, 8.4, 18.4).
            val orderType = if (request.hasOrderType() && request.orderType.isNotBlank()) {
                request.orderType.trim()
            } else {
                OrderType.TICKET.name
            }

            val paymentId = ObjectId().toHexString()
            val paymentDoc = PaymentDocument(
                id = paymentId,
                orderId = request.orderId,
                orderCode = request.orderCode,
                orderType = orderType,
                amount = request.amount,
                refreshStatusJobId = UUID.randomUUID(),
                pgAccountCode = pgAccount.code,
                paymentMethods = request.paymentMethodsList.map { PaymentType.valueOf(it) }
            )

            db.createPayment(paymentDoc) ?: run {
                logger.error("cannot create payment cause db problem")
                throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống"))
            }

            logger.info("payment {} created for request {}, pgAccountCode {}", paymentId, request, pgAccountCode)
            return StringValue.of(paymentId)
        } catch (e: StatusException) {
            throw e
        } catch (t: Throwable) {
            logger.error("createPayment error", t)
            throw StatusException(Status.INTERNAL.withDescription(t.message))
        }
    }

    override suspend fun getPaymentById(request: PaymentMessage.GetPaymentByIdRequest): PaymentMessage.PaymentResponse {
        try {
            val document = db.loadPayment(request.paymentId)
                ?: throw StatusException(Status.NOT_FOUND.withDescription("Không tìm thấy thông tin thanh toán"))

            val pgAccount = pgAccountCache.get(document.pgAccountCode) ?: kotlin.run {
                logger.error("payment [{}], Not found pg account {}", request.paymentId, document.pgAccountCode)
                throw StatusException(Status.NOT_FOUND.withDescription("Không tìm thấy pg account"))
            }

            val resp = PaymentMessage.PaymentResponse.newBuilder().also { builder ->
                builder.setId(document.id)
                document.paymentNo?.let { builder.setPaymentNo(it) }
                document.payTime?.let { builder.setPayTime(it.time) }
                document.paymentType?.let { builder.setPaymentType(it.name) }
                builder.setStatus(document.status.name)
                builder.setAmount(document.amount)
                builder.setPgAccountCode(pgAccount.code)
                builder.setPgAccountName(pgAccount.name)
                builder.setGateway(pgAccount.gateway.name)
            }.build()

            return resp
        } catch (e: StatusException) {
            throw e
        } catch (t: Throwable) {
            logger.error("getPaymentById error", t)
            throw StatusException(Status.INTERNAL.withDescription(t.message))
        }
    }

    override suspend fun getPaymentByIds(request: PaymentMessage.GetPaymentByIdsRequest): PaymentMessage.GetPaymentByIdsResponse {

        try {
            val ids = request.paymentIdsList.map { Checksum.decode(it, Environment.secretKey) }
            val documents = db.loadPaymentBayIds(ids)
            val resps = PaymentMessage.GetPaymentByIdsResponse.newBuilder().apply {
                val pm = documents.map { document ->
                    val pgAccount = pgAccountCache.get(document.pgAccountCode) ?: kotlin.run {
                        logger.error("payment [{}], Not found pg account {}", document.id, document.pgAccountCode)
                        throw StatusException(Status.NOT_FOUND.withDescription("Không tìm thấy pg account"))
                    }
                    PaymentMessage.PaymentResponse.newBuilder().also { builder ->
                        builder.setId(Checksum.encode(document.id, Environment.secretKey),)
                        document.paymentNo?.let { builder.setPaymentNo(it) }
                        document.payTime?.let { builder.setPayTime(it.time) }
                        document.paymentType?.let { builder.setPaymentType(it.name) }
                        builder.setStatus(document.status.name)
                        builder.setAmount(document.amount)
                        builder.setPgAccountCode(pgAccount.code)
                        builder.setPgAccountName(pgAccount.name)
                        builder.setGateway(pgAccount.gateway.name)
                    }.build()
                }
                addAllPayments(pm)
            }.build()

            return resps
        } catch (t: Throwable) {
            logger.error("get payment by ids error", t)
            throw StatusException(Status.INTERNAL.withDescription(t.message))
        }
    }
}