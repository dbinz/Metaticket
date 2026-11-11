package metaticket.payment.gateway

import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.jobrunr.datastructure.JobExecutorServiceName
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.order.datastructure.jobrunr.OrderJobType
import metaticket.payment.datastructure.OrderType
import metaticket.payment.datastructure.PaymentGateway
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.jobrunr.PaymentJobType
import metaticket.payment.pojo.db.PaymentDocument
import java.util.*

class JobrunrGateway(
    private val env : ApplicationEnvironment,
    private val stub: JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub
): Logging {
    private val retryNotifyPaymentStatusChanged = env.config.property("jrunr.notifyOrder.paymentStatusChanged.retry").getString().toInt()
    private val ninePayInterval = env.config.property("jrunr.notifyPayment.inquiryNinePayPaymentStatus.intervalInSecond").getString().toLong()
    private val vnPayInterval = env.config.property("jrunr.notifyPayment.inquiryVnPayPaymentStatus.intervalInSecond").getString().toLong()
    private val onePayInterval = env.config.property("jrunr.notifyPayment.inquiryOnePayPaymentStatus.intervalInSecond").getString().toLong()

    /**
     * Send a job request to job runr to schedule.
     *
     */
    suspend fun notifyPaymentStatusChanged(payment: PaymentDocument, paymentStatus: PaymentStatus): Boolean {
        val jobType = when(paymentStatus) {
            PaymentStatus.Processing -> OrderJobType.NotifyPaymentProcessing
            PaymentStatus.Succeed -> OrderJobType.NotifyPaymentSucceed
            PaymentStatus.Failed -> OrderJobType.NotifyPaymentFailed
            PaymentStatus.Cancelled -> OrderJobType.NotifyPaymentCancelled
            else -> {
                logger.error("payment {} not support notify status {}", payment.id, paymentStatus)
                return false
            }
        }
        // Route the async notify job to the order-owning service by the payment's orderType, matching
        // the synchronous OrderNotificationGateway routing. Without this, ACCESSORY payments would be
        // delivered to service.order (which has no such order) and the accessory order would never be
        // updated — e.g. when a user cancels on the gateway and the expiry sweep marks the payment
        // Failed via this JobRunr path. Null/blank/legacy => TICKET (service.order), unchanged.
        val targetService = if (payment.orderType?.trim() == OrderType.ACCESSORY.name) {
            JobExecutorServiceName.AccessoryService
        } else {
            JobExecutorServiceName.OrderService
        }

        val jr = JobrunrMessage.JobrunrRequest.newBuilder()
            .setJobId(UUID.randomUUID().toString())
            .setService(targetService.name)
            .setJobName(jobType.name)
            .setJobDesc(payment.orderId)
            .setRetry(retryNotifyPaymentStatusChanged)
            .build()

        try {
            logger.info(
                "[pay-flow] payment {} scheduling JOBRUNR job {} orderType='{}' -> targetService={} order {} (jobId={})",
                payment.id, jobType, payment.orderType, targetService, payment.orderId, jr.jobId
            )
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("[pay-flow] payment {} JOBRUNR job {} scheduled (jobId={})", payment.id, jobType, jr.jobId)
            return true
        } catch (t: Throwable) {
            logger.error("[pay-flow] payment {} scheduling JOBRUNR job {} to {} order {} exception... ", payment.id, jobType, targetService, payment.orderId, t)
            return false
        }
    }

    suspend fun inquiryPaymentStatus(payment: PaymentDocument, gateway: PaymentGateway): Boolean {
        val interval: Long
        val jobType: PaymentJobType
        when(gateway) {
            PaymentGateway.NinePay -> {
                interval = ninePayInterval
                jobType = PaymentJobType.InquiryNinePayPaymentStatus
            }
            PaymentGateway.VNPay -> {
                interval = vnPayInterval
                jobType = PaymentJobType.InquiryVnPayPaymentStatus
            }
            PaymentGateway.OnePay -> {
                interval = onePayInterval
                jobType = PaymentJobType.InquiryOnePayPaymentStatus
            }
        }

        val jr = JobrunrMessage.JobrunrRequest.newBuilder()
            .setJobId(payment.refreshStatusJobId.toString())
            .setService(JobExecutorServiceName.PaymentService.name)
            .setJobName(jobType.name)
            .setJobDesc(payment.id)
            .setIntervalInSecond(interval)
            .build()

        try {
            logger.info("payment {} sending job {} {} to refresh payment {} status", payment.id, jobType, jr.jobId, payment.id)
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("payment {} job {}", payment.id, jr.jobId)
            return true
        } catch (t: Throwable) {
            logger.error("payment {} sending job {} {} to refresh payment {} exception... ", payment.id, jobType, jr.jobId, payment.id, t)
            return false
        }
    }
}