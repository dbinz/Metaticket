package metaticket.payment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.payment.cache.PGAccountCache
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.database.service.PaymentDBService
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.gateway.JobrunrGateway
import metaticket.payment.gateway.OrderNotificationGateway
import metaticket.payment.gateway.worker.VnPayConstants
import metaticket.payment.gateway.worker.VnPayPaymentGatewayWorker
import metaticket.payment.pojo.db.PGVnPayAccount
import metaticket.payment.pojo.response.*
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */
class VnPayGatewayController(
    private val mapper: ObjectMapper,
    private val db: PaymentDBService,
    private val pgAccountCache: PGAccountCache,
    private val paymentConfig: PaymentConfig,
    private val orderNotificationGateway: OrderNotificationGateway,
    private val jobrunrGateway: JobrunrGateway,
    private val vnPayPaymentGatewayWorker: VnPayPaymentGatewayWorker,
): Logging {

    suspend fun vnPayUpdatePaymentStatusFromResult(call: ApplicationCall) {
        logger.info("receive a returned result from VnPay {}", call.parameters)

        val pC = call.parameters["pC"] ?: run {
            logger.error("receive vnpay return result missing payment checksum")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val pD = call.parameters["pD"] ?: run {
            logger.error("receive vnpay return result missing payment checksum data")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val pgAccountCode = call.parameters["pgaCode"] ?: run {
            logger.error("receive vnpay return result missing pg account code")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val paymentId = Checksum.decode(pD)

        if (!Checksum.check(paymentId, pC, paymentConfig.hashSecret)) {
            logger.error("receive vnpay return result checksum data failed")
            return call.respond(HttpStatusCode.Forbidden, redirectFailedResponse)
        }

        val pgAccount: PGVnPayAccount = pgAccountCache.get(pgAccountCode) as PGVnPayAccount? ?: run {
            logger.error("payment {} cannot start, not found pg account {}", paymentId, pgAccountCode)
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val vnpayParams = call.parameters.filter { s, _ -> s !in listOf("pgaCode", "pC", "pD") }

        val isRequestValid = vnPayPaymentGatewayWorker.validateHashSecure(pgAccount.hashSecret, vnpayParams)

        if (!isRequestValid) {
            logger.error("vnpay could not update payment cause invalid request")
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val vnpTxnRef = call.parameters["vnp_TxnRef"] ?: ""
        val vnpResponseCode = call.parameters["vnp_ResponseCode"]
        val vnpTransactionStatus = call.parameters["vnp_TransactionStatus"]

        if (vnpResponseCode == VnPayConstants.VNPAY_RESPONSE_CODE_SUCCEED &&
            vnpTransactionStatus == VnPayConstants.VNPAY_TRANSACTION_STATUS_SUCCEED)
        {
            logger.info("payment {} vnpay redirect result success", vnpTxnRef)
            return call.respond(HttpStatusCode.Processing, redirectPendingResponse)
        }

        if (vnpResponseCode != VnPayConstants.VNPAY_RESPONSE_CODE_SUCCEED &&
            vnpTransactionStatus != VnPayConstants.VNPAY_TRANSACTION_STATUS_SUCCEED)
        {
            logger.info("payment {} vnpay redirect result failed", vnpTxnRef)
            return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        logger.info("payment {} vnpay pending", vnpTxnRef)
        call.respond(HttpStatusCode.Processing, redirectPendingResponse)
    }

    suspend fun vnPayUpdatePaymentStatusFromIPNResult(call: ApplicationCall) {
        runCatching {
            logger.info("receive a returned result from IPN VnPay {}", call.parameters)

            val pgAccountCode = call.parameters["pgaCode"]!!
            val paymentId = call.parameters["vnp_TxnRef"] ?: kotlin.run {
                logger.error("receive vnpay return result missing vnp_TxnRef")
                return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
            }

            val pgAccount: PGVnPayAccount = pgAccountCache.get(pgAccountCode) as PGVnPayAccount? ?: run {
                logger.error("payment {} cannot update from IPN, not found pg account", paymentId)
                return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
            }

            val isRequestValid = vnPayPaymentGatewayWorker.validateHashSecure(pgAccount.hashSecret, call.parameters)

            if (!isRequestValid) {
                logger.error("payment {} vnpay could not update payment cause invalid request", paymentId)
                return call.respond(vnpayInvalidSignatureResponse)
            }

            vnPayValidateUpdatePaymentStatusFromResult(call)

        }.onFailure {
            logger.error("vnpay could not update payment cause unknown error")
            return call.respond(vnpayUnknownErrorResponse)
        }
    }

    private suspend fun vnPayValidateUpdatePaymentStatusFromResult(call: ApplicationCall, ipn: Boolean = false) {
        val vnpAmount = call.parameters["vnp_Amount"]?.toLong()
        val vnpTxnRef = call.parameters["vnp_TxnRef"] ?: ""
        val vnpResponseCode = call.parameters["vnp_ResponseCode"]
        val vnpPayDate = call.parameters["vnp_PayDate"]
        val vnpTransactionStatus = call.parameters["vnp_TransactionStatus"]
        val vnpTransactionNo = call.parameters["vnp_TransactionNo"]

        val parameters = call.parameters.entries().map { (a, b) ->
            a to b
        }.associateBy({ it.first }, { it.second }).let { mapper.writeValueAsString(it) }

        val paymentId = vnpTxnRef

        if (!ObjectId.isValid(paymentId)) {
            logger.error("vnpay result invalid payment id {}", vnpTxnRef)
            return if (ipn) call.respond(vnpayOrderNotFoundResponse)
            else call.respond(HttpStatusCode.Processing, redirectPendingResponse)
        }

        val paymentDoc = db.loadPayment(paymentId) ?: run {
            logger.error("not found payment {} from vnpay", paymentId)
            return if (ipn) call.respond(vnpayOrderNotFoundResponse)
            else call.respond(HttpStatusCode.Processing, redirectPendingResponse)
        }

        // have to multi amount with 100, this is required from vnpay
        if (paymentDoc.amount*100 != vnpAmount) {
            logger.error("payment {} amount {} from vnpay invalid with {} ", paymentId, vnpAmount, paymentDoc.amount)
            return if (ipn) call.respond(vnpayInvalidAmountResponse)
            else call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
        }

        val paymentStatusNew: PaymentStatus
        if (vnpResponseCode != VnPayConstants.VNPAY_RESPONSE_CODE_SUCCEED) {
            logger.info("payment {}, vnpay updated status {} ", paymentId, vnpResponseCode)
            paymentStatusNew = PaymentStatus.Failed
        } else {
            paymentStatusNew = when (vnpTransactionStatus) {
                VnPayConstants.VNPAY_TRANSACTION_STATUS_SUCCEED -> PaymentStatus.Succeed

                VnPayConstants.VNPAY_TRANSACTION_NOT_COMPLETE_YET,
                VnPayConstants.VNPAY_TRANSACTION_INVERSE -> PaymentStatus.Processing


                VnPayConstants.VNPAY_TRANSACTION_REFUND_REQUEST,
                VnPayConstants.VNPAY_TRANSACTION_FAILED,
                VnPayConstants.VNPAY_TRANSACTION_ILLEGALLY,
                VnPayConstants.VNPAY_TRANSACTION_REJECT -> PaymentStatus.Failed

                else -> {
                    logger.error("payment {} not handle vnpay updated status {} ", paymentId, vnpTransactionStatus)
                    return if (ipn) call.respond(vnpayConfirmSuccessResponse)
                    else call.respond(HttpStatusCode.BadRequest, redirectPendingResponse)
                }
            }
        }

        if (paymentStatusNew == PaymentStatus.Processing) {
            logger.info("payment {} not updated with vnpay gateway status {}", paymentId, vnpTransactionStatus)
            return if (ipn) call.respond(vnpayConfirmSuccessResponse)
            else call.respond(HttpStatusCode.Processing, redirectPendingResponse)
        }

        if (paymentStatusNew == paymentDoc.status) {
            logger.error("payment {} status {} updated already ", paymentId, paymentStatusNew)
            return if (ipn) call.respond(vnpayOrderAlreadyConfirmedResponse)
            else call.respond(HttpStatusCode.Processing, redirectPendingResponse)
        }

        db.updateVnPayPaymentResult(vnpTxnRef, paymentStatusNew, vnpPayDate, vnpTransactionNo, parameters) ?: run {
            logger.error("payment {} vnpay could not update state", paymentId)
            return if (ipn) call.respond(vnpayUnknownErrorResponse)
            else call.respond(HttpStatusCode.Processing, redirectPendingResponse)
        }

        try{
            orderNotificationGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
        } catch (t: Throwable) {
            logger.error("payment {} cannot notify when vnpay return result {} ... ", paymentId, paymentStatusNew, t)
            val job = jobrunrGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
            if (!job) {
                logger.error("payment {} cannot send notify when vnpay return result {}", paymentId, paymentStatusNew)
                // TODO implement logic handle cannot send notification payment status change
            }
        }

        logger.info("payment {} vnpay updated status {}", paymentDoc.id, paymentStatusNew)
        return if (ipn) call.respond(HttpStatusCode.OK, vnpayConfirmSuccessResponse)
        else if (paymentStatusNew == PaymentStatus.Succeed) call.respond(HttpStatusCode.OK, redirectSuccessResponse)
        else call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
    }
}
