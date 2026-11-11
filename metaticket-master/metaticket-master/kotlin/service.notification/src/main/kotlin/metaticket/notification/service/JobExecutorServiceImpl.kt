package metaticket.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CompletableDeferred
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrMessage.JobNextAction
import metaticket.notification.cache.JobRequestCache
import metaticket.notification.cache.JobRunning
import metaticket.notification.jobrunr.NotificationJobType
import metaticket.notification.mail.grpc.MailMessage
import metaticket.notification.processor.SendMailProcessor
import java.util.*
import org.slf4j.LoggerFactory

class JobExecutorServiceImpl(
    private val processor: SendMailProcessor,
    private val objectMapper: ObjectMapper,
    private val jobRequestCache: JobRequestCache,
): JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase(), Logging {
    private val emailFailureLogger = LoggerFactory.getLogger("EmailPermanentFailure")

    fun JobExecutionResponse(jobId: String, nextAction: JobrunrMessage.JobNextAction): JobrunrMessage.JobExecutionResponse {
        return JobrunrMessage.JobExecutionResponse.newBuilder()
            .setJobId(jobId)
            .setNextAction(nextAction)
            .build()
    }

    override suspend fun executeJob(request: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        return when (NotificationJobType.valueOf(request.jobName)) {
            NotificationJobType.ResendMailOrder -> executeResendMailConfirmOrderSuccess(request)
            NotificationJobType.ResendMailAccessoryOrder -> executeResendMailConfirmOrderSuccess(request)
        }
    }

    private suspend fun executeResendMailConfirmOrderSuccess(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val cf = CompletableDeferred<JobNextAction>()
        val exist = jobRequestCache.getOrAdd(UUID.fromString(jr.jobId), JobRunning(runnrJob = cf))
        if (exist != null) {
            logger.info("job {} is executing already", jr.jobId)
            if (exist.runnrJob != null) {
                if (exist.isActive()) {
                    val action = exist.awaitRunnrJob()
                    cf.complete(action)
                } else {
                    cf.complete(JobNextAction.DO_NOTHING)
                }
            } else if (exist.htmlJob != null) {
                if (exist.isActive()) {
                    val action = exist.awaitHtmlJob()
                    if (action) cf.complete(JobNextAction.STOP)
                    else cf.complete(JobNextAction.CONTINUE)
                } else {
                    cf.complete(JobNextAction.DO_NOTHING)
                }
            }
        } else {
            submitMail(jr)
        }

        val action = cf.await()
        return JobExecutionResponse(jr.jobId, action)
    }

    private suspend fun submitMail(jr: JobrunrMessage.JobExecutionRequest) = launch {
        // The job carries the request as JSON in jobDesc; the concrete proto type depends on the
        // job name so the ticket (SendMailOrderRequest) and accessory (SendMailAccessoryOrderRequest)
        // resend paths share the same executor while keeping their request contracts separate.
        val (reqData, orderCode, email) = when (NotificationJobType.valueOf(jr.jobName)) {
            NotificationJobType.ResendMailOrder -> {
                val req = objectMapper.readValue(jr.jobDesc, MailMessage.SendMailOrderRequest::class.java)
                Triple(req as Any, req.orderSummary.orderCode, req.orderSummary.email)
            }
            NotificationJobType.ResendMailAccessoryOrder -> {
                val req = objectMapper.readValue(jr.jobDesc, MailMessage.SendMailAccessoryOrderRequest::class.java)
                Triple(req as Any, req.orderCode, req.recipientEmail)
            }
        }

        try {
            logger.info("job {}, order {} processing mail to {}", jr.jobId, orderCode, email)
            processor.submit(reqData).await()

            logger.info("job {}, order {} success sent mail to {}", jr.jobId, orderCode, email)
            val c = jobRequestCache.getAndRelease(UUID.fromString(jr.jobId))
            c.complete(JobNextAction.STOP)

        } catch (t: Throwable) {
            if (isPermanentFailure(t)) {
                emailFailureLogger.error("job {}, order {} permanent fail to send mail to {} ... ", jr.jobId, orderCode, email, t)
                val c = jobRequestCache.getAndRelease(UUID.fromString(jr.jobId))
                c.complete(JobNextAction.STOP)
            } else {
                logger.error("job {}, order {} fail to send mail to {} ... ", jr.jobId, orderCode, email, t)
                val c = jobRequestCache.getAndRelease(UUID.fromString(jr.jobId))
                c.complete(JobNextAction.CONTINUE)
            }
        }
    }

    private fun isPermanentFailure(t: Throwable): Boolean {
        var cause: Throwable? = t
        while (cause != null) {
            val name = cause.javaClass.name
            val message = cause.message ?: ""
            if (name.contains("SMTPAddressFailedException") ||
                (name.contains("SendFailedException") && message.contains("Invalid Addresses"))) {
                return true
            }
            if (message.contains("550") && message.contains("does not exist")) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}