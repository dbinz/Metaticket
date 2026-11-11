package metaticket.order.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.notification.mail.grpc.MailMessage
import metaticket.notification.mail.grpc.MailNotificationServiceGrpcKt
import metaticket.order.cache.ShowCache
import metaticket.order.pojo.db.OrderDocument
import metaticket.order.utils.buildOrderSummary
import metaticket.order.utils.toOrderSummaryProto
import java.util.*

class NotificationGateway(
    private val stub: MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineStub,
    private val paymentGateway: PaymentGateway,
    private val showCache: ShowCache,
): Logging {
    /**
     * Send a job request to job runr to schedule.
     *
     */
    suspend fun sendMailOrder(order: OrderDocument, reqId: UUID): Boolean {
        try {
            logger.info("order {} processing send mail for status {}", order.id, order.status)

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
                logger.error("order {}, fail to send mail, cannot build mai request", order.id)
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

            withContext(Dispatchers.IO) { stub.sendMailOrder(sendMailRequest) }
            logger.info("order {} sent success for status {}", order.id, order.status)
            return true
        } catch (t: Throwable) {
            logger.error("order {}, failed to send email for status {}, exception... ", order.id, order.status, t)
            return false
        }
    }
}