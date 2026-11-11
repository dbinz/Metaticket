package metaticket.jobrunr.jobrunr

import metaticket.common.logger.Logging
import metaticket.jobrunr.request.JobRequest
import org.jobrunr.scheduling.BackgroundJob
import java.time.Duration


/**
 *
 * @author dongcatchay
 */
class JobrunrRecurrentlyService(

): Logging {

    fun schedule(jr: JobRequest) {
        try {
            logger.info("job {} scheduling job request {}", jr.jobId, jr)
            processRequest(jr)
        } catch (t: Throwable) {
            logger.error("job {} failed to schedule job {} ...", jr.jobId, jr, t)
        }
    }

    private fun processRequest(jr: JobRequest) {
        BackgroundJob.scheduleRecurrently<JobrunrBackgroundService>(
            jr.jobId.toString(),
            Duration.ofSeconds(jr.intervalInSecond!!)
        ) { service ->
            service.submit(jr)
        }
    }
}
