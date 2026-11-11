package metaticket.payment.controller

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.payment.cache.PGAccountCache
import metaticket.payment.configuration.Environment
import metaticket.payment.database.service.PaymentDBService
import metaticket.payment.datastructure.PaymentGateway
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.gateway.JobrunrGateway
import metaticket.payment.gateway.OrderNotificationGateway
import metaticket.payment.gateway.worker.NinePayPaymentGatewayWorker
import metaticket.payment.gateway.worker.OnePayPaymentGatewayWorker
import metaticket.payment.gateway.worker.VnPayPaymentGatewayWorker
import metaticket.payment.pojo.db.*
import metaticket.payment.pojo.request.StartPaymentRequest
import metaticket.payment.pojo.response.toResponse

class PaymentController(
    private val db: PaymentDBService,
    private val pgAccountCache: PGAccountCache,
    private val jsonFactory: JsonNodeFactory,
    private val orderNotificationGateway: OrderNotificationGateway,
    private val jobrunrGateway: JobrunrGateway,
    private val ninePayPaymentGatewayWorker: NinePayPaymentGatewayWorker,
    private val vnPayPaymentGatewayWorker: VnPayPaymentGatewayWorker,
    private val onepayPaymentGatewayWorker: OnePayPaymentGatewayWorker,
) : Logging {

    suspend fun startPayment(call: ApplicationCall) {
        val input = call.receive<StartPaymentRequest>().apply {
            this.paymentId = Checksum.decode(this.paymentId, Environment.secretKey)
        }

        val paymentDoc = db.updatePaymentStatus(input.paymentId, PaymentStatus.Starting, listOf(PaymentStatus.Created))
            ?: run {
                logger.error("payment {} cannot start cause not found or started already", input.paymentId)
            return call.respond(HttpStatusCode.NotFound, "Không tìm thấy thông tin thanh toán")
        }

        if (paymentDoc.status != PaymentStatus.Starting) {
            logger.error("payment {} cannot start with status {}", paymentDoc.id, paymentDoc.status)
            return call.respond(HttpStatusCode.BadRequest, "Thông tin thanh toán bị lỗi")
        }

        if (!paymentDoc.paymentMethods!!.contains(input.paymentType)) {
            logger.error("payment {} cannot start with payment method {} is not in {}", paymentDoc.id, input.paymentType, paymentDoc.paymentMethods)
            return call.respond(HttpStatusCode.BadRequest, "Thông tin thanh toán bị lỗi")
        }

        val pgAccount: PGAccount = pgAccountCache.get(paymentDoc.pgAccountCode) ?: run {
            logger.error("payment {} cannot start, not found pg account", paymentDoc.id)
            return call.respond(HttpStatusCode.InternalServerError, "Thông tin thanh toán bị lỗi")
        }

        val paymentDetail: PaymentDetail? =try {
            when (pgAccount.gateway) {
                PaymentGateway.NinePay -> {
                    val description = "Thanh toan ve cho don hang ${paymentDoc.orderCode}"
                    ninePayPaymentGatewayWorker.createPaymentDetail(
                        pgAccount as PGNinePayAccount, paymentDoc.id, paymentDoc.amount, input.paymentType, description
                    )
                }

                PaymentGateway.VNPay -> {
                    val description = "Thanh toan ve cho don hang ${paymentDoc.orderCode}"
                    val ipAddress = call.request.origin.remoteAddress
                    vnPayPaymentGatewayWorker.createPaymentDetails(
                        pgAccount as PGVnPayAccount,
                        paymentDoc.id,
                        paymentDoc.amount,
                        ipAddress,
                        input.paymentType,
                        description
                    )
                }

                PaymentGateway.OnePay -> {
                    val description = "Thanh toan ${paymentDoc.orderCode}"
                    val ipAddress = call.request.origin.remoteAddress
                    val referenceUrl = call.request.header("referer")!!
                    onepayPaymentGatewayWorker.createPaymentDetails(
                        pgAccount as PGOnePayAccount,
                        paymentDoc.id,
                        paymentDoc.amount,
                        ipAddress,
                        input.paymentType,
                        description,
                        referenceUrl
                        )
                }
            }
        } catch (t: Throwable) {
            logger.error("payment {} cannot start, exception... ", paymentDoc.id, t)
            return call.respond(HttpStatusCode.InternalServerError, "Thông tin thanh toán bị lỗi")
        }

        paymentDetail ?: run {
            logger.error("payment {} failed to start with request {}", paymentDoc.id, input)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }

        try {
            logger.info("[pay-flow] PaymentController about to notifyPaymentStarting payment {} orderType='{}' orderId={}", paymentDoc.id, paymentDoc.orderType, paymentDoc.orderId)
            orderNotificationGateway.notifyPaymentStarting(paymentDoc)
            logger.info("[pay-flow] PaymentController notifyPaymentStarting OK for payment {}", paymentDoc.id)
        } catch (t: Throwable) {
            logger.error("[pay-flow] payment {} failed to start cause error when notify start payment to order service", paymentDoc.id, t)
            try {
                orderNotificationGateway.notifyPaymentStatusChanged(paymentDoc, PaymentStatus.Failed)
            } catch (t: Throwable) {
                logger.error("payment {} failed to start cause error when retry notify start payment to order service", paymentDoc.id, t)
                val job = jobrunrGateway.notifyPaymentStatusChanged(paymentDoc, PaymentStatus.Failed)
                if (!job) {
                    logger.error("payment {} failed to start, cannot submit job for order {}", paymentDoc.id, paymentDoc.orderId)
                    // does not need to do anything, the payment expired time will handle it as expired.
                    return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
                }
            }
                db.updatePaymentStatus(paymentDoc.id, PaymentStatus.Failed, listOf(PaymentStatus.Created, PaymentStatus.Starting))
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }

        // schedule to refresh payment status
        jobrunrGateway.inquiryPaymentStatus(paymentDoc, paymentDetail.gateway)

        val doc = db.updateWhenStartPayment(paymentDoc.id, input.paymentType, paymentDetail) ?: run {
            logger.error("payment {} failed to start with request {} cause failed to save to db", paymentDoc.id, input)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }

        logger.info("start payment {}", doc)

        logger.info("payment {} started", input.paymentId)
        call.respond(HttpStatusCode.OK, paymentDetail.redirectUrl)
    }

    suspend fun loadPaymentStatus(call: ApplicationCall) {
        val id = call.parameters["id"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Missing or malformed id")
        )

        val document = db.loadPayment(id) ?: return call.respond(HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Cannot find document $id")
        )

        call.respond(mapOf("status" to document.status))
    }

    suspend fun loadPayment(call: ApplicationCall) {
        val id = call.parameters["id"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Missing or malformed id")
        )

        val document = db.loadPayment(id) ?: return call.respond(HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Cannot find document $id")
        )

        val pgAccount = pgAccountCache.get(document.pgAccountCode) ?: kotlin.run {
            logger.error("Not found pg account {}", document.pgAccountCode)
            return call.respond(HttpStatusCode.InternalServerError, "Cannot find pg account")
        }

        call.respond(document.toResponse(pgAccount))
    }

    suspend fun loadPaymentByIds(call: ApplicationCall) {
        val ids = call.receive<List<String>>().map { Checksum.decode(it, Environment.secretKey) }

        val documents = db.loadPaymentBayIds(ids)
        call.respond(documents.map {
            val pgAccount = pgAccountCache.get(it.pgAccountCode) ?: kotlin.run {
                logger.error("Not found pg account {}", it.pgAccountCode)
                return call.respond(HttpStatusCode.InternalServerError, "Cannot find pg account")
            }
            it.toResponse(pgAccount)
        })
    }
}
