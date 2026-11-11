package metaticket.accessory.service

import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.jobrunr.AccessoryJobType
import metaticket.common.logger.Logging
import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrMessage.JobNextAction
import metaticket.order.datastructure.jobrunr.OrderJobType
import metaticket.payment.datastructure.PaymentStatus
import org.bson.types.ObjectId
import java.util.Date

/** Slack so a JobRunr fire a second or two early still matches `saleOpenAt`. */
private const val OPEN_SHOW_EARLY_FIRE_SLACK_MS = 2_000L

/**
 * Accessory-side implementation of the shared `JobExecutorService` gRPC contract that
 * `service.jobrunr` calls back when a scheduled job fires.
 *
 * It handles:
 * - [AccessoryJobType.OpenShow] — one-shot auto-open of a Waiting accessory show. Reloads Mongo
 *   and CAS-updates Waiting → Open only when `saleOpenAt` is due; otherwise STOP (stale job).
 * - The **asynchronous** payment-status callback that `service.payment` schedules on JobRunr as a
 *   fallback (and from its own expiry sweep). Those jobs are carried as `service.order`'s
 *   `OrderJobType.NotifyPayment*` names with `jobDesc = accessory order id`.
 *
 * The pre-terminal expiry sweep (Req 11.2) is NOT handled here: the accessory service runs its own
 * periodic sweep in-process at start-up (see [metaticket.accessory.StartUp]), exactly like
 * `service.order` expires its created orders — so no recurring job is scheduled on `service.jobrunr`.
 *
 * Registration of this impl on the accessory gRPC server is done in the application wiring (task 16.2).
 */
class AccessoryJobExecutorServiceImpl(
    private val orderService: AccessoryOrderService,
    private val catalogDb: CatalogDatabaseService,
) : JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase(), Logging {

    private fun response(jobId: String, nextAction: JobNextAction): JobrunrMessage.JobExecutionResponse =
        JobrunrMessage.JobExecutionResponse.newBuilder()
            .setJobId(jobId)
            .setNextAction(nextAction)
            .build()

    override suspend fun executeJob(
        request: JobrunrMessage.JobExecutionRequest,
    ): JobrunrMessage.JobExecutionResponse {
        if (request.jobName == AccessoryJobType.OpenShow.name) {
            return executeOpenShow(request)
        }
        return executePaymentStatus(request)
    }

    /**
     * Open the show in [request.jobDesc] iff Mongo still says Waiting and `saleOpenAt` is due.
     * Always STOP: CONTINUE would re-apply the original multi-day delay.
     */
    private suspend fun executeOpenShow(
        request: JobrunrMessage.JobExecutionRequest,
    ): JobrunrMessage.JobExecutionResponse {
        val showId = request.jobDesc
        return try {
            if (!ObjectId.isValid(showId)) {
                logger.error("OpenShow job {} invalid showId {}", request.jobId, showId)
                return response(request.jobId, JobNextAction.STOP)
            }
            val dueAt = Date(System.currentTimeMillis() + OPEN_SHOW_EARLY_FIRE_SLACK_MS)
            val opened = catalogDb.openShowIfWaitingAndDue(showId, dueAt)
            if (opened != null) {
                logger.info("OpenShow job {} opened accessory show {}", request.jobId, showId)
            } else {
                logger.info(
                    "OpenShow job {} no-op accessory show {} (not Waiting or not due)",
                    request.jobId, showId,
                )
            }
            response(request.jobId, JobNextAction.STOP)
        } catch (t: Throwable) {
            logger.error("OpenShow job {} failed for accessory show {}", request.jobId, showId, t)
            response(request.jobId, JobNextAction.STOP)
        }
    }

    /**
     * Payment-status callback service.payment scheduled on JobRunr, carried as an OrderJobType
     * name (jobDesc = accessory order id). Mirror service.order's executor: map the OrderJobType
     * to the terminal payment status the accessory flow acts on.
     */
    private suspend fun executePaymentStatus(
        request: JobrunrMessage.JobExecutionRequest,
    ): JobrunrMessage.JobExecutionResponse {
        val paymentStatus = when (runCatching { OrderJobType.valueOf(request.jobName) }.getOrNull()) {
            OrderJobType.NotifyPaymentSucceed -> PaymentStatus.Succeed
            OrderJobType.NotifyPaymentFailed -> PaymentStatus.Failed
            OrderJobType.NotifyPaymentCancelled -> PaymentStatus.Cancelled
            OrderJobType.NotifyPaymentProcessing -> return response(request.jobId, JobNextAction.STOP)
            null -> {
                logger.error("job {} unknown accessory job name {}", request.jobId, request.jobName)
                return response(request.jobId, JobNextAction.STOP)
            }
        }

        val orderId = request.jobDesc
        return try {
            logger.info(
                "[pay-flow] JOBRUNR executeJob applying payment status {} to accessory order {} (jobId={} jobName={})",
                paymentStatus, orderId, request.jobId, request.jobName,
            )
            orderService.onPaymentStatusChanged(orderId, paymentStatus)
            logger.info("[pay-flow] JOBRUNR executeJob done for accessory order {} (jobId={}) — STOP", orderId, request.jobId)
            response(request.jobId, JobNextAction.STOP)
        } catch (t: Throwable) {
            logger.error("[pay-flow] JOBRUNR executeJob failed to apply payment status {} to accessory order {} (jobId={}) — CONTINUE ...", paymentStatus, orderId, request.jobId, t)
            response(request.jobId, JobNextAction.CONTINUE)
        }
    }
}
