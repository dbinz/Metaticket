package metaticket.jobrunr.utils

import metaticket.jobrunr.datastructure.JobExecutorServiceName
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.request.JobRequest
import metaticket.jobrunr.jobrunr.JobrunrBackgroundService
import metaticket.jobrunr.jobrunr.JobrunrRecurrentlyService
import org.jobrunr.scheduling.BackgroundJob
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID


fun JobrunrMessage.JobrunrRequest.toJobRequest(): JobRequest {
    return JobRequest(
        jobId = UUID.fromString(jobId),
        service = JobExecutorServiceName.valueOf(service),
        jobName = jobName,
        jobDesc = jobDesc,
        retry = retry,
        delayInSecond = delayInSecond,
        intervalInSecond = if (hasIntervalInSecond()) intervalInSecond else null
    )
}

fun scheduleRequest(jr: JobRequest, retryDelayInS: Int = 0) {
    if (jr.intervalInSecond != null) {
        // submit to recurrent service to schedule request after a delay time
        BackgroundJob.schedule<JobrunrRecurrentlyService>(
            jr.jobId,
            Instant.now().plus(jr.delayInSecond, ChronoUnit.SECONDS)
        ) { service ->
            service.schedule(jr)
        }
    } else {
        BackgroundJob.schedule<JobrunrBackgroundService>(
            jr.jobId,
            Instant.now().plus(jr.delayInSecond + retryDelayInS, ChronoUnit.SECONDS)
        ) { service ->
            service.submit(jr)
        }
    }
}
