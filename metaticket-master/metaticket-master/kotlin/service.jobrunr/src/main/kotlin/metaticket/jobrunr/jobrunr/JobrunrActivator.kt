package metaticket.jobrunr.jobrunr

import org.jobrunr.server.JobActivator

class JobrunrActivator(
    private val jobrunrBackgroundService: JobrunrBackgroundService,
    private val jobrunrRecurrentlyService: JobrunrRecurrentlyService,
) : JobActivator {

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> activateJob(type: Class<T>): T {
        if (type == JobrunrBackgroundService::class.java) return jobrunrBackgroundService as T
        if (type == JobrunrRecurrentlyService::class.java) return jobrunrRecurrentlyService as T

        throw RuntimeException("Unsupported background service class " + type.name)
    }
}
