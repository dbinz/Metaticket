package metaticket.payment.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.payment.cache.PGAccountCache
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.database.service.PaymentDBService
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.gateway.JobrunrGateway
import metaticket.payment.gateway.OrderNotificationGateway
import metaticket.payment.gateway.worker.OnePayConstants
import metaticket.payment.gateway.worker.OnePayPaymentGatewayWorker
import metaticket.payment.pojo.db.PGOnePayAccount
import metaticket.payment.pojo.response.*
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */
class OnePayGatewayController(
    private val mapper: ObjectMapper,
    private val db: PaymentDBService,
    private val pgAccountCache: PGAccountCache,
    private val paymentConfig: PaymentConfig,
    private val orderNotificationGateway: OrderNotificationGateway,
    private val jobrunrGateway: JobrunrGateway,
    private val onepayPaymentGatewayWorker: OnePayPaymentGatewayWorker,
): Logging {
    suspend fun onepayUpdatePaymentStatusFromResult(call: ApplicationCall) {
        logger.info(
            "[pay-flow] RETURN-URL onepayUpdatePaymentStatusFromResult received: vpc_MerchTxnRef={} vpc_TxnResponseCode={} params={}",
            call.parameters["vpc_MerchTxnRef"], call.parameters["vpc_TxnResponseCode"], call.parameters
        )

        runCatching {
            val pC = call.parameters["pC"] ?: run {
                logger.error("[pay-flow] RETURN-URL STOP: missing payment checksum 'pC' — responding BadRequest")
                return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
            }

            val pD = call.parameters["pD"] ?: run {
                logger.error("[pay-flow] RETURN-URL STOP: missing payment checksum data 'pD' — responding BadRequest")
                return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
            }

            val pgAccountCode = call.parameters["pgaCode"] ?: run {
                logger.error("[pay-flow] RETURN-URL STOP: missing pg account code 'pgaCode' — responding BadRequest")
                return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
            }

            val paymentId = Checksum.decode(pD)
            logger.info("[pay-flow] RETURN-URL step: decoded paymentId={} pgaCode={}", paymentId, pgAccountCode)

            if (!Checksum.check(paymentId, pC, paymentConfig.hashSecret)) {
                logger.error("[pay-flow] RETURN-URL STOP: checksum check failed for paymentId={} — responding Forbidden", paymentId)
                return call.respond(HttpStatusCode.Forbidden, redirectFailedResponse)
            }

            val pgAccount = pgAccountCache.get(pgAccountCode) as PGOnePayAccount? ?: run {
                logger.error("[pay-flow] RETURN-URL STOP: payment {} pg account {} not found — responding BadRequest", paymentId, pgAccountCode)
                return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
            }

            val parameters = call.parameters.entries().map { (a, b) ->
                a to b
            }.associateBy({ it.first }, { it.second.first() })
                .filterKeys { k -> k.startsWith("vpc_") || k.startsWith("user_") }

            val isRequestValid =
                onepayPaymentGatewayWorker.onepayVerifySecureHash(parameters, pgAccount.merchantHashCode)

            if (!isRequestValid) {
                logger.error("[pay-flow] RETURN-URL STOP: payment {} secure-hash verification FAILED — responding BadRequest. verifiedParams={}", paymentId, parameters)
                return call.respond(HttpStatusCode.BadRequest, redirectFailedResponse)
            }
            logger.info("[pay-flow] RETURN-URL step: payment {} secure-hash OK", paymentId)

            val vpc_MerchTxnRef = call.parameters["vpc_MerchTxnRef"] // payment id
            val vpc_TxnResponseCode = call.parameters["vpc_TxnResponseCode"]
            val vpc_Message = call.parameters["vpc_Message"] ?: OnePayConstants.onepayMessages[vpc_TxnResponseCode]

            if (paymentId != vpc_MerchTxnRef) {
                logger.warn(
                    "[pay-flow] RETURN-URL STOP: paymentId {} != vpc_MerchTxnRef {} — responding BadRequest",
                    paymentId,
                    vpc_MerchTxnRef
                )
                return call.respond(HttpStatusCode.BadRequest, redirectFailedMsgResponse(vpc_Message))
            }

            logger.info("[pay-flow] RETURN-URL step: payment {} passed all guards — entering onepayValidateUpdatePaymentStatusFromResult", paymentId)
            onepayValidateUpdatePaymentStatusFromResult(call)
        }.onFailure {
            logger.error("[pay-flow] RETURN-URL STOP: unexpected exception while handling onepay redirect result — responding Processing", it)
            return call.respond(HttpStatusCode.Processing, redirectPendingResponse)
        }
    }

    suspend fun onepayUpdatePaymentStatusFromIPNResult(call: ApplicationCall) {
        runCatching {
            logger.info(
                "[pay-flow] IPN onepayUpdatePaymentStatusFromIPNResult received: vpc_MerchTxnRef={} vpc_TxnResponseCode={} params={}",
                call.parameters["vpc_MerchTxnRef"], call.parameters["vpc_TxnResponseCode"], call.parameters
            )

            val pgAccountCode = call.parameters["pgaCode"]!!
            val paymentId = call.parameters["vpc_MerchTxnRef"] ?: kotlin.run {
                logger.error("receive onepay return result missing vpc_MerchTxnRef")
                return call.respond(HttpStatusCode.OK, onepayIPNResponse)
            }

            val pgAccount = pgAccountCache.get(pgAccountCode) as PGOnePayAccount? ?: run {
                logger.error("payment {} cannot update from IPN, not found pg account", paymentId)
                return call.respond(HttpStatusCode.OK, onepayIPNResponse)
            }

            val parameters = call.parameters.entries().map { (a, b) ->
                a to b
            }.associateBy({ it.first }, { it.second.first() })
                .filterKeys { k -> k.startsWith("vpc_") || k.startsWith("user_") }

            val isRequestValid = onepayPaymentGatewayWorker.onepayVerifySecureHash(parameters, pgAccount.merchantHashCode)

            if (!isRequestValid) {
                logger.error("payment {} onepay could not update payment cause invalid request", paymentId)
                return call.respond(HttpStatusCode.OK, onepayIPNResponse)
            }

            onepayValidateUpdatePaymentStatusFromResult(call, onepayIPNResponse)

        }.onFailure {
            logger.error("onepay could not update payment cause unknown error")
            return call.respond(HttpStatusCode.OK, "unknown error")
        }
    }

    private suspend fun onepayValidateUpdatePaymentStatusFromResult(call: ApplicationCall, onepayIPNResponse: String? = null) {
        val isIPN = onepayIPNResponse != null
        logger.info("[pay-flow] onepayValidate ENTER (isIPN={}) params={}", isIPN, call.parameters)
        val vpc_Amount = call.parameters["vpc_Amount"]?.toLong()
        val vpc_MerchTxnRef = call.parameters["vpc_MerchTxnRef"] ?: ""
        val vpc_TxnResponseCode = call.parameters["vpc_TxnResponseCode"]
        val vpc_TransactionNo = call.parameters["vpc_TransactionNo"]
        val vpc_Message = call.parameters["vpc_Message"] ?: OnePayConstants.onepayMessages[vpc_TxnResponseCode]

        val parameters = call.parameters.entries().map { (a, b) ->
            a to b
        }.associateBy({ it.first }, { it.second }).let { mapper.writeValueAsString(it) }

        val paymentId = vpc_MerchTxnRef

        if (!ObjectId.isValid(paymentId)) {
            logger.error("[pay-flow] onepayValidate STOP: invalid payment id '{}' (isIPN={})", vpc_MerchTxnRef, isIPN)
            onepayIPNResponse?.let { return call.respond(HttpStatusCode.OK, it) }
            return call.respond(HttpStatusCode.Processing, redirectPendingMsgResponse(vpc_Message))
        }

        val paymentDoc = db.loadPayment(paymentId) ?: run {
            logger.error("[pay-flow] onepayValidate STOP: payment {} not found in db (isIPN={})", paymentId, isIPN)
            onepayIPNResponse?.let { return call.respond(HttpStatusCode.OK, it) }
            return call.respond(HttpStatusCode.Processing, redirectPendingMsgResponse(vpc_Message))
        }
        logger.info("[pay-flow] onepayValidate step: loaded payment {} currentStatus={} orderType='{}' amount={}", paymentId, paymentDoc.status, paymentDoc.orderType, paymentDoc.amount)

        // have to multi amount with 100, this is required from onepay
        if (paymentDoc.amount * 100 != vpc_Amount) {
            logger.error("[pay-flow] onepayValidate STOP: payment {} amount mismatch — onepay vpc_Amount={} expected={} (isIPN={})", paymentId, vpc_Amount, paymentDoc.amount * 100, isIPN)
            onepayIPNResponse?.let { return call.respond(HttpStatusCode.OK, it) }
            return call.respond(HttpStatusCode.BadRequest, redirectFailedMsgResponse(vpc_Message))
        }

        val paymentStatusNew: PaymentStatus
        logger.info(
            "[pay-flow] onepay result payment {} orderType='{}' vpc_TxnResponseCode={} message={} (isIPN={})",
            paymentId, paymentDoc.orderType, vpc_TxnResponseCode, vpc_Message, onepayIPNResponse != null
        )
        if (vpc_TxnResponseCode == OnePayConstants.Approved) {
            paymentStatusNew = PaymentStatus.Succeed
        } else {
            paymentStatusNew = PaymentStatus.Failed
        }

        if (paymentStatusNew == paymentDoc.status) {
            logger.info("[pay-flow] payment {} status {} already applied — skipping notify", paymentId, paymentStatusNew)
            onepayIPNResponse?.let { return call.respond(HttpStatusCode.OK, it) }
            return if (paymentStatusNew === PaymentStatus.Succeed) call.respond(HttpStatusCode.OK, redirectSuccessMsgResponse(vpc_Message))
            else call.respond(HttpStatusCode.Processing, redirectPendingMsgResponse(vpc_Message))
        }

        db.updateOnePayPaymentResult(vpc_MerchTxnRef, paymentStatusNew, vpc_TransactionNo, parameters) ?: run {
            logger.error("[pay-flow] onepayValidate STOP: payment {} db updateOnePayPaymentResult returned null (status not persisted) — skipping notify (isIPN={})", paymentId, isIPN)
            return call.respond(HttpStatusCode.Processing, redirectPendingMsgResponse(vpc_Message))
        }
        logger.info("[pay-flow] onepayValidate step: payment {} db updated {} -> {} — proceeding to notify", paymentId, paymentDoc.status, paymentStatusNew)

        try {
            logger.info("[pay-flow] onepay result payment {} notifying DIRECT gRPC status {} to order {}", paymentId, paymentStatusNew, paymentDoc.orderId)
            orderNotificationGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
            logger.info("[pay-flow] onepay result payment {} DIRECT gRPC notify {} succeeded", paymentId, paymentStatusNew)
        } catch (t: Throwable) {
            logger.error("[pay-flow] payment {} cannot notify DIRECT gRPC when onepay return result {} — falling back to JobRunr ... ", paymentId, paymentStatusNew, t)
            val job = jobrunrGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
            if (!job) {
                logger.error("[pay-flow] payment {} JobRunr fallback also FAILED to schedule when onepay return result {}", paymentId, paymentStatusNew)
                // TODO implement logic handle cannot send notification payment status change
            } else {
                logger.info("[pay-flow] payment {} JobRunr fallback scheduled when onepay return result {}", paymentId, paymentStatusNew)
            }
        }

        logger.info("[pay-flow] payment {} onepay updated status {}", paymentDoc.id, paymentStatusNew)
        if (paymentStatusNew === PaymentStatus.Succeed) {
            onepayIPNResponse?.let { return call.respond(HttpStatusCode.OK, it) }
            return call.respond(HttpStatusCode.OK, redirectSuccessMsgResponse(vpc_Message))
        } else {
            onepayIPNResponse?.let { return call.respond(HttpStatusCode.OK, it) }
            return call.respond(HttpStatusCode.BadRequest, redirectFailedMsgResponse(vpc_Message))
        }
    }
}
