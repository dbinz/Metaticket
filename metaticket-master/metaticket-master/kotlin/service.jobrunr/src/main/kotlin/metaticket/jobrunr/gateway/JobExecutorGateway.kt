package metaticket.jobrunr.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.request.JobRequest


/**
 *
 * @author dongcatchay
 */
abstract class JobExecutorGateway(private val stub: JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub): Logging {
    suspend fun executeJob(jr: JobRequest): JobrunrMessage.JobNextAction {
        val request = JobrunrMessage.JobExecutionRequest.newBuilder()
            .setJobId(jr.jobId.toString())
            .setJobName(jr.jobName)
            .setJobDesc(jr.jobDesc)
            .build()

        logger.info("job {} executing {}", jr.jobId, jr)

        try {
            val response = withContext(Dispatchers.IO) {
                stub.executeJob(request)
            }

            logger.info("job {} receive response {} for job request {}", jr.jobId, jr, response)

            return response.nextAction

        } catch (t: Throwable) {
            logger.error("job {} failed to execute job request {} cause... ", jr.jobId, jr, t)
            if (jr.intervalInSecond != null) return JobrunrMessage.JobNextAction.CONTINUE

            // by default, if retry < 0 meaning retry forever
            if (jr.retry < 0 || jr.retry-- > 0) {
                return JobrunrMessage.JobNextAction.CONTINUE
            }

            return JobrunrMessage.JobNextAction.STOP
        }
    }
}

