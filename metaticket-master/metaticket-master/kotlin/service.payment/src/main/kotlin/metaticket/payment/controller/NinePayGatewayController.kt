package metaticket.payment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.payment.cache.PGAccountCache
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.database.service.PaymentDBService
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.gateway.JobrunrGateway
import metaticket.payment.gateway.OrderNotificationGateway
import metaticket.payment.gateway.worker.NinePayConstants
import metaticket.payment.gateway.worker.NinePayPaymentGatewayWorker
import metaticket.payment.pojo.db.PGNinePayAccount
import metaticket.payment.pojo.response.*


/**
 *
 * @author dongcatchay
 */
class NinePayGatewayController(
    private val db: PaymentDBService,
    private val pgAccountCache: PGAccountCache,
    private val paymentConfig: PaymentConfig,
    private val orderNotificationGateway: OrderNotificationGateway,
    private val jobrunrGateway: JobrunrGateway,
    private val ninePayPaymentGatewayWorker: NinePayPaymentGatewayWorker,
    private val mapper: ObjectMapper,
): Logging {
    suspend fun ninePayUpdatePaymentStatusFromIPNResult(call: ApplicationCall) {
        val parameters = call.receiveParameters()
        val result = parameters["result"] ?: run {
            logger.error("receive 9pay return result missing result")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val checksum = parameters["checksum"] ?: run {
            logger.error("receive 9pay return result missing checksum")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val pgAccountCode = call.parameters["pgaCode"] ?: run {
            logger.error("receive 9pay return result missing pg account code")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val pgAccount: PGNinePayAccount = pgAccountCache.get(pgAccountCode) as PGNinePayAccount? ?: run {
            logger.error("receive 9pay return result not found pg account {}", pgAccountCode)
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        ninePayValidateUpdatePaymentStatusFromResult(call, result, checksum, pgAccount.checksumKey)
    }

    suspend fun ninePayUpdatePaymentStatusFromResult(call: ApplicationCall) {
        logger.debug("received nine pay updating status returned {}", call.parameters)

        val result = call.parameters["result"] ?: run {
            logger.error("receive 9pay return result missing result")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val checksum = call.parameters["checksum"] ?: run {
            logger.error("receive 9pay return result missing checksum")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val pgAccountCode = call.parameters["pgaCode"] ?: run {
            logger.error("receive 9pay return result missing pg account code")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val pC = call.parameters["pC"] ?: run {
            logger.error("receive 9pay return result missing payment checksum")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val pD = call.parameters["pD"] ?: run {
            logger.error("receive 9pay return result missing payment checksum data")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val paymentId = Checksum.decode(pD)

        if (!Checksum.check(paymentId, pC, paymentConfig.hashSecret)) {
            logger.error("receive 9pay return result checksum data failed")
            return call.respond(HttpStatusCode.Forbidden, redirectFailedResponse)
        }

        val pgAccount: PGNinePayAccount = pgAccountCache.get(pgAccountCode) as PGNinePayAccount? ?: run {
            logger.error("payment {} cannot start, not found pg account {}", paymentId, pgAccountCode)
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        ninePayValidateUpdatePaymentStatusFromResult(call, result, checksum, pgAccount.checksumKey)
    }

    private suspend fun ninePayValidateUpdatePaymentStatusFromResult(
        call: ApplicationCall, result: String, checksum: String, checksumKey: String
    ) {
        val responseString = ninePayPaymentGatewayWorker.getPaymentInfoFromResult(result, checksum, checksumKey)
            ?: run {
                logger.error("9pay result {} checksum {} cannot update payment status", result, checksum)
                return call.respond(HttpStatusCode.Forbidden, redirectFailedResponse)
            }

        logger.info("received payment info {}", responseString)

        val responseObj = mapper.readTree(responseString)

        val paymentId = responseObj.get("invoice_no")?.asText() ?: run {
            logger.error("result {} 9pay missing invoice_no (payment id)", responseObj)
            return call.respond(HttpStatusCode.Forbidden, redirectFailedResponse)
        }

        val paymentDoc = db.loadPayment(paymentId) ?: run {
            logger.error("payment {} 9pay not found for a returned result {}", paymentId, responseObj)
            return call.respond(HttpStatusCode.NotFound, redirectFailedResponse)
        }

        val status = responseObj.get("status")?.asInt()
        val failureReason = responseObj.get("failure_reason")?.asText()

        val paymentStatus = when (status) {
            NinePayConstants.NINEPAY_STATUS_CREATED,
            NinePayConstants.NINEPAY_STATUS_PREVIEWING,
            NinePayConstants.NINEPAY_STATUS_PROCESSING,
            NinePayConstants.NINEPAY_STATUS_SUCCESS_NOT_PUBLIC -> PaymentStatus.Processing

            NinePayConstants.NINEPAY_STATUS_SUCCEED -> PaymentStatus.Succeed

            NinePayConstants.NINEPAY_STATUS_CANCELED -> PaymentStatus.Cancelled

            NinePayConstants.NINEPAY_STATUS_REFUND -> PaymentStatus.Refunded

            NinePayConstants.NINEPAY_STATUS_FAILED,
            NinePayConstants.NINEPAY_STATUS_KEEP_MONEY,
            NinePayConstants.NINEPAY_STATUS_ERROR,
            NinePayConstants.NINEPAY_STATUS_REJECT-> PaymentStatus.Failed

            else -> {
                logger.error("payment {}, not handle 9pay updated status {}", paymentId, status)
                return call.respond(HttpStatusCode.Processing, redirectPendingMsgResponse(failureReason))
            }
        }

        if (paymentStatus == PaymentStatus.Processing) {
            logger.info("payment {} not updated with 9pay gateway status {}", paymentId, status)
            return call.respond(HttpStatusCode.Processing, redirectPendingResponse)
        }

        val createdAt = responseObj.get("created_at")?.asText()
        val paymentNo = responseObj.get("payment_no")?.asText()

        db.updateNinePayPaymentResult(paymentId, paymentStatus, createdAt, paymentNo, responseString)
            ?: run {
                logger.error("payment {} 9pay fail to update status to db ", paymentId)
                return call.respond(HttpStatusCode.Processing, redirectPendingResponse)
            }

        try {
            orderNotificationGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatus)
        } catch (e: Throwable) {
            logger.info("payment {} cannot notify to order when 9pay return result {}", paymentId, status)
            val job = jobrunrGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatus)
            if (!job) {
                logger.error("payment {} cannot send notify when 9pay return result {}", paymentId, status)
                // TODO implement logic handle cannot send notification payment status change
            }
        }

        logger.info("payment {} updated status to {}", paymentDoc.id, paymentStatus)
        if (paymentStatus == PaymentStatus.Succeed) return call.respond(HttpStatusCode.OK, redirectSuccessResponse)
        if (paymentStatus == PaymentStatus.Cancelled) return call.respond(HttpStatusCode.BadRequest, redirectFailedMsgResponse(failureReason))
        if (paymentStatus == PaymentStatus.Failed) return call.respond(HttpStatusCode.BadRequest, redirectFailedMsgResponse(failureReason))
        return call.respond(HttpStatusCode.Processing, redirectPendingResponse)
    }
}
