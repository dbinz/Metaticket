package metaticket.order.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.jobrunr.datastructure.JobExecutorServiceName
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.notification.jobrunr.NotificationJobType
import metaticket.notification.mail.grpc.MailMessage
import metaticket.order.cache.ShowCache
import metaticket.order.datastructure.OrderStatus
import metaticket.order.pojo.db.OrderDocument
import metaticket.order.utils.buildOrderSummary
import metaticket.order.utils.toOrderSummaryProto
import metaticket.ticket.datastructure.jobrunr.TicketJobType
import java.util.*

class JobrunrGateway(
    private val env : ApplicationEnvironment,
    private val paymentGateway: PaymentGateway,
    private val showCache: ShowCache,
    private val objectMapper: ObjectMapper,
    private val stub: JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub,
): Logging {
    private val sendEmailRetry = env.config.property("jrunr.sendEmail.retry").getString().toInt()

    /**
     * Send a job request to jobrunr to schedule.
     *
     */
    suspend fun notifyOrderStatusChanged(resId: String, orderStatus: OrderStatus): Boolean {
        val jobType = when(orderStatus) {
            OrderStatus.PaymentProcessing,
            OrderStatus.PaymentPending -> TicketJobType.NotifyOrderPaying
            OrderStatus.PaymentSucceed -> TicketJobType.NotifyOrderSuccessful

            OrderStatus.Canceled,
            OrderStatus.PaymentCancelled -> TicketJobType.NotifyOrderCancelled

            OrderStatus.PaymentFailed,
            OrderStatus.Failed,
            OrderStatus.Expired -> TicketJobType.NotifyOrderFailed

            else -> return false
        }
        val jr = JobrunrMessage.JobrunrRequest.newBuilder()
            .setJobId(UUID.randomUUID().toString())
            .setService(JobExecutorServiceName.TicketService.name)
            .setJobName(jobType.name)
            .setJobDesc(resId)
            .setRetry(sendEmailRetry)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                stub.scheduleJob(jr)
            }
            true
        } catch (t: Throwable) {
            logger.error("reservation {}, jobrunr failed to notify to job {} ... ", resId, jobType, t)
            false
        }
    }

    suspend fun resendEmailOrder(order: OrderDocument, reqId: UUID): Boolean {
        val show = showCache.getShow(order.showId) ?: run {
            logger.error("order {}, fail to send mail, cannot get show {}", order.id, order.showId)
            return false
        }
        val session = show.sessionsList.firstOrNull { it.sessionId == order.sessionId } ?: run {
            logger.error("order {}, fail to send mail, cannot get show session {}", order.id, order.sessionId)
            return false
        }

        val m = session.mailPluginTemplateList.firstOrNull {it.orderStatus == order.status.name} ?: run {
            logger.info("order {}, no need to send mail cause not found mail plugin for order status {}", order.id, order.status)
            return true
        }

        val orderSummary = buildOrderSummary(paymentGateway, show, session, order) ?: run {
            logger.error("order {}, fail to send mail, cannot build mail request", order.id)
            return false
        }

        val mailPluginTemplate = MailMessage.MailPluginTemplateProto.newBuilder()
            .setOrderStatus(order.status.name)
            .setMailPlugin(m.mailPlugin)
            .setMailTemplate(m.mailTemplate)
            .build()

        val sendMailRequest = MailMessage.SendMailOrderRequest.newBuilder()
            .setReqId(reqId.toString())
            .setShowId(session.showId)
            .setSessionId(session.sessionId)
            .setEventSquareImageUrl(session.eventSquareImageUrl)
            .setEventNameImageUrl(session.eventNameImageUrl)
            .setMailPluginTemplate(mailPluginTemplate)
            .setOrderSummary(orderSummary.toOrderSummaryProto())
            .apply {
                if (session.hasQrcodeEmail()) this.qrcodeEmail = session.qrcodeEmail
            }
            .build()

        val jr = JobrunrMessage.JobrunrRequest.newBuilder()
            .setJobId(reqId.toString())
            .setService(JobExecutorServiceName.NotificationService.name)
            .setJobName(NotificationJobType.ResendMailOrder.name)
            .setJobDesc(objectMapper.writeValueAsString(sendMailRequest))
            .setRetry(sendEmailRetry)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                stub.scheduleJob(jr)
            }
            true
        } catch (t: Throwable) {
            logger.error("order {}, jobrunr failed to notify to job {} ... ", order.id, jr.jobName, t)
            false
        }
    }
}