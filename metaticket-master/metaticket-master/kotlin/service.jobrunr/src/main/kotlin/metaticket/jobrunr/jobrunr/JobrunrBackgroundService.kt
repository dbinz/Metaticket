package metaticket.jobrunr.jobrunr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import metaticket.common.logger.Logging
import metaticket.jobrunr.datastructure.JobExecutorServiceName
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.gateway.*
import metaticket.jobrunr.request.JobRequest
import metaticket.jobrunr.utils.scheduleRequest
import org.jobrunr.jobs.states.IllegalJobStateChangeException
import org.jobrunr.scheduling.BackgroundJob
import org.jobrunr.storage.JobNotFoundException
import org.jobrunr.storage.nosql.mongo.MongoDBStorageProvider


/**
 *
 * @author dongcatchay
 */
class JobrunrBackgroundService(
    private val showS: ShowGateway,
    private val ticketS: TicketGateway,
    private val orderS: OrderGateway,
    private val paymentS: PaymentGateway,
    private val notificationS: NotificationGateway,
    private val accessoryS: AccessoryGateway,
    private val storageProvider: MongoDBStorageProvider,
): Logging {

    fun submit(jr: JobRequest) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val client = when (jr.service) {
                    JobExecutorServiceName.TicketService -> ticketS
                    JobExecutorServiceName.OrderService -> orderS
                    JobExecutorServiceName.PaymentService -> paymentS
                    JobExecutorServiceName.NotificationService -> notificationS
                    JobExecutorServiceName.ShowService -> showS
                    JobExecutorServiceName.AccessoryService -> accessoryS
                }

                val jobNextAction = client.executeJob(jr)

                logger.info("job {} will {} executing job request {}",jr.jobId, jobNextAction, jr)

                if (jobNextAction == JobrunrMessage.JobNextAction.DO_NOTHING) return@launch

                if (jobNextAction == JobrunrMessage.JobNextAction.STOP) {
                    logger.info("job {} remove job {}", jr.jobId, jr)
                    BackgroundJob.delete(jr.jobId.toString())
                    storageProvider.deletePermanently(jr.jobId)
                } else if (jr.intervalInSecond == null && jobNextAction == JobrunrMessage.JobNextAction.CONTINUE) {
                    // with non-interval job, need to remove the old job one before execute the new job that same id
                    storageProvider.deletePermanently(jr.jobId)
                    scheduleRequest(jr)
                }
            } catch (ex: IllegalJobStateChangeException) {
                logger.debug("job {} deleted already", jr.jobId)
                storageProvider.deletePermanently(jr.jobId)
            } catch (ex: JobNotFoundException) {
                logger.debug("job {} isn't found", jr.jobId)
            } catch (t: Throwable) {
                logger.info("job {} retry schedule job exception... ", jr.jobId, t.cause)
                logger.trace("job {} trace log retry schedule job {} exception... ", jr.jobId, jr, t)
                if (jr.intervalInSecond == null && (jr.retry < 0 || jr.retry-- > 0)) {
                    storageProvider.deletePermanently(jr.jobId)
                    scheduleRequest(jr)
                }
            }
        }
    }
}
