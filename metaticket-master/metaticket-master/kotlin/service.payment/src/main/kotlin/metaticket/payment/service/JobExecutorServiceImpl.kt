package metaticket.payment.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.config.*
import metaticket.common.logger.Logging
import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrMessage.*
import metaticket.payment.cache.PGAccountCache
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.database.service.PaymentDBService
import metaticket.payment.datastructure.PaymentGateway
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.gateway.JobrunrGateway
import metaticket.payment.gateway.OrderNotificationGateway
import metaticket.payment.gateway.worker.*
import metaticket.payment.jobrunr.PaymentJobType
import metaticket.payment.pojo.db.PGNinePayAccount
import metaticket.payment.pojo.db.PGOnePayAccount
import metaticket.payment.pojo.db.PGVnPayAccount
import metaticket.payment.pojo.db.VNPayPaymentDetail
import java.net.URL
import java.util.*

class JobExecutorServiceImpl(
    private val mapper: ObjectMapper,
    private val db: PaymentDBService,
    private val pgAccountCache: PGAccountCache,
    private val jobrunrGateway: JobrunrGateway,
    private val orderNotificationGateway: OrderNotificationGateway,
    private val ninePayPaymentGatewayWorker: NinePayPaymentGatewayWorker,
    private val vnPayPaymentGatewayWorker: VnPayPaymentGatewayWorker,
    private val onepaymentGatewayWorker: OnePayPaymentGatewayWorker,
    private val appConfig: ApplicationConfig,
): JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase(), Logging {
    private val ninePayMaxTimeQuery = appConfig.property("jrunr.notifyPayment.inquiryNinePayPaymentStatus.maxTimeQueryForNotfoundInM").getString().toLong()
    private val vnPayMaxTimeQuery = appConfig.property("jrunr.notifyPayment.inquiryVnPayPaymentStatus.maxTimeQueryForNotfoundInM").getString().toLong()
    private val onePayMaxTimeQuery = appConfig.property("jrunr.notifyPayment.inquiryOnePayPaymentStatus.maxTimeQueryForNotfoundInM").getString().toLong()

    fun JobExecutionResponse(jobId: String, nextAction: JobNextAction): JobExecutionResponse {
        return JobExecutionResponse.newBuilder()
            .setJobId(jobId)
            .setNextAction(nextAction)
            .build()
    }

    override suspend fun executeJob(request: JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        return when (PaymentJobType.valueOf(request.jobName)) {
            PaymentJobType.InquiryNinePayPaymentStatus -> executeJobInquiryNinePayPayment(request)
            PaymentJobType.InquiryVnPayPaymentStatus -> executeJobInquiryVnPayPayment(request)
            PaymentJobType.InquiryOnePayPaymentStatus -> executeJobInquiryOnePayPayment(request)
        }
    }

    private suspend fun executeJobInquiryNinePayPayment(jr: JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        try {
            val paymentId = jr.jobDesc

            val paymentDoc = db.loadPayment(paymentId) ?: run {
                logger.error("payment {} not found when execute job {}", paymentId, jr)
                return JobExecutionResponse(jr.jobId, JobrunrMessage.JobNextAction.STOP)
            }

            if (paymentDoc.status != PaymentStatus.Processing) {
                logger.debug("payment {} solve with status {} already when execute job {}", paymentId, paymentDoc.status, jr)
                return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
            }

            if (paymentDoc.paymentDetail?.gateway != PaymentGateway.NinePay) {
                logger.info("payment {} gateway is not 9Pay", paymentId)
                return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
            }

            val pgAccount = pgAccountCache.get(paymentDoc.pgAccountCode) as PGNinePayAccount

            val responseString = ninePayPaymentGatewayWorker.callQueryStateInquiry(pgAccount, paymentId)
            val responseObj = mapper.readTree(responseString)

            val status = responseObj.get("status")?.asInt()
            val errorCode = responseObj.get("error_code")?.asText()

            val paymentStatusNew = when (status) {
                NinePayConstants.NINEPAY_STATUS_CREATED,
                NinePayConstants.NINEPAY_STATUS_PREVIEWING,
                NinePayConstants.NINEPAY_STATUS_PROCESSING,
                NinePayConstants.NINEPAY_STATUS_SUCCESS_NOT_PUBLIC -> PaymentStatus.Processing

                NinePayConstants.NINEPAY_STATUS_SUCCEED -> PaymentStatus.Succeed

                NinePayConstants.NINEPAY_STATUS_CANCELED -> PaymentStatus.Cancelled

                NinePayConstants.NINEPAY_STATUS_FAILED,
                NinePayConstants.NINEPAY_STATUS_KEEP_MONEY,
                NinePayConstants.NINEPAY_STATUS_ERROR,
                NinePayConstants.NINEPAY_STATUS_REJECT-> {
                    if (errorCode == "221") {
                        // transaction not found, need to retry some time
                        if (Date().time - paymentDoc.lastModified.time < ninePayMaxTimeQuery * 60000L) {
                            logger.error("payment {}, not found 9pay transaction with status {}, retry to query", paymentId, status)
                            return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                        }
                        logger.error("payment {}, not found 9pay transaction with status {}, stop job query cause timeout {} minutes", paymentId, status, ninePayMaxTimeQuery)
                        return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
                    }
                    PaymentStatus.Failed
                }

                else -> {
                    logger.error("payment {}, not handle 9pay updated status {}", paymentId, status)
                    return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                }
            }

            when (paymentStatusNew) {
                PaymentStatus.Processing -> {
                    logger.info("Payment {} is processing", paymentId)
                    return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                }
                PaymentStatus.Succeed,
                PaymentStatus.Cancelled,
                PaymentStatus.Failed -> {
                    try {
                        orderNotificationGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
                    } catch (t: Throwable) {
                        logger.error("payment {}, 9pay fail to notify status changed to order {} {} ... ", paymentId, paymentDoc.orderId, responseObj, t)
                        val job = jobrunrGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
                        if (!job) {
                            logger.error("payment {}, 9pay fail to send job status changed to order {} {}", paymentId, paymentDoc.orderId, responseObj)
                            return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                        }
                    }

                    val createdAt = responseObj.get("created_at")?.asText()
                    val paymentNo = responseObj.get("payment_no")?.asText()

                    db.updateNinePayPaymentResult(paymentId, paymentStatusNew, createdAt, paymentNo, responseString) ?: run {
                        logger.error("payment {}, 9pay updated status but db update field {}", paymentId, responseObj)
                        return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                    }
                    logger.info("payment {} updated status to {}", paymentId, paymentStatusNew)
                    return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
                }

                else -> {
                    logger.error("Payment {} 9pay not handle status {}", paymentId, status)
                    return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                }
            }
        } catch (ex: Exception) {
            logger.debug("Failed to executeJobInquiryNinePayPayment when execute job {} ...", jr, ex)
            return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
        }
    }

    private suspend fun executeJobInquiryVnPayPayment(jr: JobExecutionRequest): JobExecutionResponse {
        try {
            val paymentId = jr.jobDesc

            val paymentDoc = db.loadPayment(paymentId) ?: run {
                logger.error("payment {} not found when execute job {}", paymentId, jr)
                return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
            }

            if (paymentDoc.status != PaymentStatus.Processing) {
                logger.debug("payment {} solve with status {} already when execute job {}", paymentId, paymentDoc.status, jr)
                return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
            }

            if (paymentDoc.paymentDetail?.gateway != PaymentGateway.VNPay) {
                logger.info("payment {} gateway is not VnPay when execute job {}", paymentId, jr)
                return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
            }

            val pgAccount = pgAccountCache.get(paymentDoc.pgAccountCode) as PGVnPayAccount

            val ipAddress = getPublicIp()
            val vnPayDetail = paymentDoc.paymentDetail as VNPayPaymentDetail

            val responseString = vnPayPaymentGatewayWorker.callQueryStateInquiry(
                pgAccount, ipAddress, paymentId, vnPayDetail.vnpCreateDate
            )

            val responseObj = mapper.readTree(responseString)

            val vnpResponseCode = responseObj.get("vnp_ResponseCode").asText()

            var paymentStatusNew = PaymentStatus.Processing
            if (vnpResponseCode != VnPayConstants.VNPAY_RESPONSE_CODE_SUCCEED) {
                if (vnpResponseCode == VnPayConstants.VNPAY_RESPONSE_CODE_NOT_FOUND) {
                    // transaction not found, need to retry some time
                    if (Date().time - paymentDoc.lastModified.time < vnPayMaxTimeQuery * 60000L) {
                        logger.error("payment {}, not found vnpay transaction with status {}, retry to query", paymentId, vnpResponseCode)
                        return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                    }
                    logger.error("payment {}, not found vnpay transaction with status {}, stop job query cause timeout {} minutes", paymentId, vnpResponseCode, vnPayMaxTimeQuery)
                    return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
                }
                logger.error("payment {} not handle vnpay updated status {} ", paymentId, vnpResponseCode)
                paymentStatusNew = PaymentStatus.Failed
            } else {
                val vnpTransactionStatus = responseObj.get("vnp_TransactionStatus").asText()
                paymentStatusNew = when (vnpTransactionStatus) {
                    VnPayConstants.VNPAY_TRANSACTION_STATUS_SUCCEED -> PaymentStatus.Succeed

                    VnPayConstants.VNPAY_TRANSACTION_NOT_COMPLETE_YET,
                    VnPayConstants.VNPAY_TRANSACTION_INVERSE -> PaymentStatus.Processing


                    VnPayConstants.VNPAY_TRANSACTION_REFUND_REQUEST,
                    VnPayConstants.VNPAY_TRANSACTION_FAILED,
                    VnPayConstants.VNPAY_TRANSACTION_ILLEGALLY,
                    VnPayConstants.VNPAY_TRANSACTION_REJECT -> PaymentStatus.Failed

                    else -> {
                        logger.error("payment {} not handle vnpay updated status {} ", paymentId, vnpResponseCode)
                        return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                    }
                }
            }

            when (paymentStatusNew) {
                PaymentStatus.Processing -> {
                    logger.debug("Payment {} is processing", paymentId)
                    return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                }
                PaymentStatus.Succeed,
                PaymentStatus.Cancelled,
                PaymentStatus.Failed -> {
                    try {
                        orderNotificationGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
                    } catch (t: Throwable) {
                        logger.error("payment {}, vnpay fail notify status changed to order {} {} ... ", paymentId, paymentDoc.orderId, vnpResponseCode, t)
                        val job = jobrunrGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
                        if (!job) {
                            logger.error("payment {}, vnpay fail send job status changed to order {} {}", paymentId, paymentDoc.orderId, vnpResponseCode)
                            return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
                        }
                    }
                    val vnpTxnRef: String? = responseObj.get("vnp_txnRef").asText()
                    val vnpPayDate: String? = responseObj.get("vnp_payDate").asText()
                    val vnpTransactionNo: String? = responseObj.get("vnp_transactionNo").asText()

                    db.updateVnPayPaymentResult(vnpTxnRef, paymentStatusNew, vnpPayDate, vnpTransactionNo, responseString) ?: run {
                        logger.error("payment {}, VnPay updated status but db update field {}", paymentId, vnpResponseCode)
                        return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                    }
                    logger.info("payment {} updated to status {}", paymentId, paymentStatusNew)
                    return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
                }

                else -> {
                    logger.error("payment {} not handle status {}", paymentId, vnpResponseCode)
                    return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                }
            }
        } catch (ex: Throwable) {
            logger.debug("failed to executeJobInquiryVnPayPayment when execute job {} ...", jr, ex)
            return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
        }
    }

    private suspend fun executeJobInquiryOnePayPayment(jr: JobExecutionRequest): JobExecutionResponse {
        try {
            val paymentId = jr.jobDesc

            val paymentDoc = db.loadPayment(paymentId) ?: run {
                logger.error("payment {} not found when execute job {}", paymentId, jr)
                return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
            }

            if (paymentDoc.status != PaymentStatus.Processing) {
                logger.debug("payment {} solve with status {} already when execute job {}", paymentId, paymentDoc.status, jr)
                return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
            }

            if (paymentDoc.paymentDetail?.gateway != PaymentGateway.OnePay) {
                logger.info("payment {} gateway is not onepay when execute job {}", paymentId, jr)
                return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
            }

            val pgAccount = pgAccountCache.get(paymentDoc.pgAccountCode) as PGOnePayAccount

            val responseObj = onepaymentGatewayWorker.callQueryStateInquiry(pgAccount, paymentId)

            // Xác định giao dịch tồn tại hay không (Y/N)
            val vpc_DRExists = responseObj.get("vpc_DRExists")
            val vpc_MerchTxnRef = responseObj.get("vpc_MerchTxnRef")
            val vpc_TransactionNo = responseObj.get("vpc_TransactionNo")
            /**
             * Mã trả lời, xác định giao dịch thành công hay không
             * 0: Giao dịch thanh toán thành công
             * <> 0: Giao dịch thanh toán thất bại
             * 300: Giao dịch pending
             * 100: Giao dịch đang tiến hành hoặc chưa thanh toán
             */
            val vpc_TxnResponseCode = responseObj.get("vpc_TxnResponseCode")

            var paymentStatusNew = when (vpc_TxnResponseCode) {
                "0" -> PaymentStatus.Succeed
                "300", "100" -> PaymentStatus.Processing
                else -> PaymentStatus.Failed
            }

            logger.info(
                "[pay-flow] onepay inquiry payment {} orderId={} orderType={} vpc_DRExists={} vpc_TxnResponseCode={} -> paymentStatusNew={}",
                paymentId, paymentDoc.orderId, paymentDoc.orderType, vpc_DRExists, vpc_TxnResponseCode, paymentStatusNew,
            )

            if (vpc_DRExists != "Y") {
                // transaction not found, need to retry some time
                if (Date().time - paymentDoc.lastModified.time < onePayMaxTimeQuery * 60000L) {
                    logger.debug("payment {}, not found onepay transaction, retry to query", paymentId)
                    return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                }
                logger.error("payment {}, not found onepay transaction, stop job query cause timeout {} minutes", paymentId, onePayMaxTimeQuery)
                return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
            }

            when (paymentStatusNew) {
                PaymentStatus.Processing -> {
                    logger.debug("Payment {} is processing", paymentId)
                    return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                }
                PaymentStatus.Succeed,
                PaymentStatus.Cancelled,
                PaymentStatus.Failed -> {
                    try {
                        logger.info("[pay-flow] onepay payment {} notifying DIRECT gRPC status {} to order {}", paymentId, paymentStatusNew, paymentDoc.orderId)
                        orderNotificationGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
                        logger.info("[pay-flow] onepay payment {} DIRECT gRPC notify {} succeeded to order {}", paymentId, paymentStatusNew, paymentDoc.orderId)
                    } catch (t: Throwable) {
                        logger.error("[pay-flow] payment {}, onepay DIRECT gRPC notify failed to order {} {} — falling back to JobRunr ... ", paymentId, paymentDoc.orderId, vpc_TxnResponseCode, t)
                        val job = jobrunrGateway.notifyPaymentStatusChanged(paymentDoc, paymentStatusNew)
                        if (!job) {
                            logger.error("[pay-flow] payment {}, onepay JobRunr fallback also FAILED to schedule for order {} {}", paymentId, paymentDoc.orderId, vpc_TxnResponseCode)
                            return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
                        }
                        logger.info("[pay-flow] payment {}, onepay JobRunr fallback scheduled for order {}", paymentId, paymentDoc.orderId)
                    }

                    db.updateOnePayPaymentResult(vpc_MerchTxnRef, paymentStatusNew, vpc_TransactionNo, mapper.writeValueAsString(responseObj)) ?: run {
                        logger.error("payment {}, onepay updated status but db update field {}", paymentId, vpc_TxnResponseCode)
                        return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                    }
                    logger.info("payment {} updated to status {}", paymentId, paymentStatusNew)
                    return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
                }

                else -> {
                    logger.error("payment {} not handle status {}", paymentId, vpc_TxnResponseCode)
                    return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
                }
            }
        } catch (ex: Throwable) {
            logger.debug("failed to executeJobInquiryOnePayPayment when execute job {} ...", jr, ex)
            return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
        }
    }
}

fun getPublicIp(): String {
    return try {
        URL("https://api.ipify.org").readText()
    } catch (e: Throwable) {
        "127.0.0.1"
    }
}