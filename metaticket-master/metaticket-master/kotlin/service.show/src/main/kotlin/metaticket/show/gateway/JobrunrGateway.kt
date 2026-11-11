package metaticket.show.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.jobrunr.datastructure.JobExecutorServiceName
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.show.cloudflare.CloudflareClient
import metaticket.show.database.service.DatabaseService
import metaticket.show.datastructure.jobrunr.ShowJobType
import metaticket.show.metasearch.MetaSearchClient
import java.util.*

class JobrunrGateway(
    private val stub: JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub,
    private val metaSearchClient: MetaSearchClient,
    private val showDS: DatabaseService,
    private val cloudflareClient: CloudflareClient,
): Logging {
    private val maxRetrySend = 5

    /**
     * Send a job request to job runr to schedule.
     *
     */
    suspend fun onShowChange(showId: String) = launch {
        val show = showDS.loadShow(showId) ?: run {
            logger.error("show {} not found in db", showId)
            return@launch Unit
        }

        val request = metaSearchClient.buildCreateUpdateShowRequest(show)

        if (metaSearchClient.executeSendCreateUpdateShow(request)) {
            launch {
                cloudflareClient.purgeCache(show)
            }
            logger.info("show {} success sent create-update info to metasearch", showId)
            return@launch Unit
        }

        var noRetry = maxRetrySend
        while (--noRetry > 0) {
            logger.debug("show {} retrying {} time send info to metasearch", showId, maxRetrySend - noRetry)
            val res = sendToJobrunrOnShowChanged(showId)
            if (res) noRetry = 0
            else delay(1000)
        }
    }

    private suspend fun sendToJobrunrOnShowChanged(showId: String): Boolean {
        try {
            val requestBuilder = JobrunrMessage.JobrunrRequest.newBuilder()
                .setJobId(UUID.nameUUIDFromBytes(showId.toByteArray()).toString())
                .setService(JobExecutorServiceName.ShowService.name)
                .setJobName(ShowJobType.CreateUpdateMetaSearch.name)
                .setJobDesc(showId)
                .setRetry(-1)
            logger.info("show {} sending job to jobrunr", showId)
            withContext(Dispatchers.IO) { stub.scheduleJob(requestBuilder.build()) }
            logger.info("show {} sent job to jobrunr", showId)
            return true
        } catch (t: Throwable) {
            logger.error("show {} sending job to jobrunr has exception... ", showId, t)
            return false
        }
    }
}