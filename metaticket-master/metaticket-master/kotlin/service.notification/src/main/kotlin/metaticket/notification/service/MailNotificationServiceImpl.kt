package metaticket.notification.service

import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CompletableDeferred
import metaticket.common.logger.Logging
import metaticket.notification.cache.JobRequestCache
import metaticket.notification.cache.JobRunning
import metaticket.notification.mail.grpc.MailMessage
import metaticket.notification.mail.grpc.MailNotificationServiceGrpcKt
import metaticket.notification.processor.SendMailProcessor
import java.util.*

class MailNotificationServiceImpl(
    private val jobRequestCache: JobRequestCache,
    private val processor: SendMailProcessor,
): MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineImplBase(), Logging {
    override suspend fun sendMailOrder(request: MailMessage.SendMailOrderRequest): Empty {
        val orderSummary = request.orderSummary

        jobRequestCache.add(UUID.fromString(request.reqId), JobRunning(htmlJob = CompletableDeferred()))

        try {
            logger.info("order {} processing mail to {}", orderSummary.orderCode, orderSummary.email)
            processor.submit(request).await()
            jobRequestCache.getAndRelease(UUID.fromString(request.reqId)).complete(true)
            logger.info("order {} success sent mail to {}", orderSummary.orderCode, orderSummary.email)
            return Empty.getDefaultInstance()
        } catch (t: Throwable) {
            jobRequestCache.getAndRelease(UUID.fromString(request.reqId)).complete(false)
            logger.error("order {} fail to send mail to {} ...", orderSummary.orderCode, orderSummary.email, t)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống, vui lòng thử lại sau"))
        }
    }

    override suspend fun sendMailAccessoryOrder(request: MailMessage.SendMailAccessoryOrderRequest): Empty {
        try {
            logger.info("accessory order {} processing mail to {}", request.orderCode, request.recipientEmail)
            processor.submit(request).await()
            logger.info("accessory order {} success sent mail to {}", request.orderCode, request.recipientEmail)
            return Empty.getDefaultInstance()
        } catch (t: Throwable) {
            logger.error(
                "accessory order {} fail to send mail to {} ...",
                request.orderCode, request.recipientEmail, t
            )
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống, vui lòng thử lại sau"))
        }
    }

    override suspend fun sendMailOTP(request: MailMessage.SendMailOTPRequest): Empty {
        jobRequestCache.add(UUID.fromString(request.reqId), JobRunning(htmlJob = CompletableDeferred()))

        try {
            logger.info("[{}] processing mail to {}", request.reqId, request.email)
            processor.submit(request).await()
            jobRequestCache.getAndRelease(UUID.fromString(request.reqId)).complete(true)
            logger.info("[{}] success sent mail to {}", request.reqId, request.email)
            return Empty.getDefaultInstance()
        } catch (t: Throwable) {
            jobRequestCache.getAndRelease(UUID.fromString(request.reqId)).complete(false)
            logger.error("[{}] fail to send mail to {} ...", request.reqId, request.email, t)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống, vui lòng thử lại sau"))
        }
    }
}