package metaticket.notification.cache

import com.google.common.cache.*
import io.ktor.server.plugins.*
import kotlinx.coroutines.CompletableDeferred
import metaticket.common.logger.Logging
import metaticket.jobrunr.grpc.JobrunrMessage.JobNextAction
import java.util.*
import java.util.concurrent.TimeUnit


/**
 *
 * @author dongcatchay
 */

data class JobRunning(
    val runnrJob: CompletableDeferred<JobNextAction>? = null,
    val htmlJob: CompletableDeferred<Boolean>? = null,
) {
    fun isActive(): Boolean {
        if (runnrJob != null) return runnrJob.isActive
        if (htmlJob != null) return htmlJob.isActive
        return false
    }

    suspend fun awaitRunnrJob(): JobNextAction {
        if (runnrJob != null) return runnrJob.await()
        throw RuntimeException("jobrunnr is null")
    }

    suspend fun awaitHtmlJob(): Boolean {
        if (htmlJob != null) return htmlJob.await()
        throw RuntimeException("htmlRequest is null")
    }

    fun complete(act: JobNextAction) {
        runnrJob?.complete(act)
    }

    fun complete(success: Boolean) {
        htmlJob?.complete(success)
    }
}

class JobRequestCache: Logging {
    private val job: LoadingCache<UUID, JobRunning> = CacheBuilder.newBuilder()
        .maximumSize(100000)
        .expireAfterWrite(3, TimeUnit.DAYS)
        .removalListener(RemovalListener<UUID, JobRunning> {
            logger.info("cache job {} is removed", it.key)
        })
        .build(object : CacheLoader<UUID, JobRunning>() {
            @Throws(Throwable::class)
            override fun load(key: UUID): JobRunning {
                logger.error("job request {} not found", key)
                throw NotFoundException("not found $key")
            }
        })

    fun addAndGetOld(jobId: UUID, rq: JobRunning): JobRunning? {
        val old = job.getIfPresent(jobId)
        job.put(jobId, rq)
        return old
    }

    fun getOrAdd(reqId: UUID, rq: JobRunning): JobRunning? {
        val old = job.getIfPresent(reqId)
        if (old == null) job.put(reqId, rq)
        return old
    }

    fun add(reqId: UUID, rq: JobRunning) {
        job.put(reqId, rq)
    }

    fun getAndRelease(jobId: UUID): JobRunning {
        val deferred = job.get(jobId)
        job.invalidate(jobId)
        return deferred
    }
}
