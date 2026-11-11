package metaticket.jobrunr.request

import metaticket.jobrunr.datastructure.JobExecutorServiceName
import java.util.*


/**
 * @author dongcatchay
 */

/**
 * Declare request that one service want to submit to jobrunr to schedule an execution
 * Timer configuration is in second
 */
data class JobRequest(
    val jobId: UUID,                        // set job id
    val service: JobExecutorServiceName,    // service submit this request
    val jobName: String,                    // job type that service want to execute
    val jobDesc: String,                    // object that was serialized witch is included all info to execute a job

    /**
     * retry schedules this job when execution failed
     * if value < 0 meanings retries forever
     */
    var retry: Int = 0,

    /**
     * schedule to run this job after a time
     */
    val delayInSecond: Long = 0,

    /**
     * run in recurrently, don't care about execution failed or not.
     * only stop recurrent when execution returns nextAction = Stop.
     */
    val intervalInSecond: Long? = null,
)