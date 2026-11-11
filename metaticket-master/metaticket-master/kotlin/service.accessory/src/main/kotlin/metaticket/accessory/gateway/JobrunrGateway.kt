package metaticket.accessory.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.accessory.jobrunr.AccessoryJobType
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.common.logger.Logging
import metaticket.jobrunr.datastructure.JobExecutorServiceName
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.notification.jobrunr.NotificationJobType
import java.util.Date
import java.util.UUID

/**
 * Accessory-side gRPC client to `service.jobrunr` for scheduling the **durable email fallback**
 * (mirrors `service.order`'s `JobrunrGateway.resendEmailOrder`).
 *
 * When the in-process direct send through [AccessoryMailGateway] is exhausted, the accessory order
 * flow schedules a `ResendMailAccessoryOrder` job here. `service.jobrunr` then calls back into
 * `service.notification`'s `JobExecutorService`, which deserializes the carried
 * [metaticket.notification.mail.grpc.MailMessage.SendMailAccessoryOrderRequest] and keeps retrying
 * the send with JobRunr's own retry budget — so a transient notification outage no longer relies on
 * a manual resend.
 *
 * This deliberately reuses the dedicated accessory request built by
 * [AccessoryMailGateway.buildSendMailAccessoryOrderRequest]; it never touches the ticket
 * `SendMailOrder` path.
 */
class JobrunrGateway(
    env: ApplicationEnvironment,
    private val objectMapper: ObjectMapper,
    private val mailGateway: AccessoryMailGateway,
    private val stub: JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub,
) : Logging {

    private val sendEmailRetry = env.config.property("jrunr.sendEmail.retry").getString().toInt()

    /**
     * Schedule a durable JobRunr job to (re)send the confirmation email for [order] via
     * `service.notification`. Returns `true` if the job was accepted by `service.jobrunr`, `false`
     * on any scheduling error (the caller keeps the order resend-eligible on `false`).
     *
     * The `jobId` is the order code so the whole fallback path stays traceable/de-dupable per order.
     */
    suspend fun resendEmailAccessoryOrder(order: AccessoryOrderDocument, show: AccessoryShowDocument): Boolean {
        val sendMailRequest = mailGateway.buildSendMailAccessoryOrderRequest(order, show)

        val jr = JobrunrMessage.JobrunrRequest.newBuilder()
            .setJobId(order.code)
            .setService(JobExecutorServiceName.NotificationService.name)
            .setJobName(NotificationJobType.ResendMailAccessoryOrder.name)
            .setJobDesc(objectMapper.writeValueAsString(sendMailRequest))
            .setRetry(sendEmailRetry)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                stub.scheduleJob(jr)
            }
            true
        } catch (t: Throwable) {
            logger.error("accessory order {}, jobrunr failed to schedule job {} ... ", order.code, jr.jobName, t)
            false
        }
    }

    /**
     * Schedule a one-shot job that opens [showId] at [saleOpenAt]. Always uses a fresh UUID so a
     * later date change can enqueue another fire; stale jobs no-op against Mongo then STOP.
     * Returns `false` if `service.jobrunr` rejected the schedule.
     */
    suspend fun scheduleOpenShow(showId: String, saleOpenAt: Date): Boolean {
        val delaySec = ((saleOpenAt.time - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L)
        val jobId = UUID.randomUUID().toString()
        val jr = JobrunrMessage.JobrunrRequest.newBuilder()
            .setJobId(jobId)
            .setService(JobExecutorServiceName.AccessoryService.name)
            .setJobName(AccessoryJobType.OpenShow.name)
            .setJobDesc(showId)
            .setRetry(0)
            .setDelayInSecond(delaySec)
            .build()
        return try {
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info(
                "accessory show {} scheduled OpenShow jobId={} delaySec={} saleOpenAt={}",
                showId, jobId, delaySec, saleOpenAt.time,
            )
            true
        } catch (t: Throwable) {
            logger.error("accessory show {} failed to schedule OpenShow delaySec={}", showId, delaySec, t)
            false
        }
    }
}
