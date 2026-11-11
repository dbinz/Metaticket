package metaticket.show.service

import com.fasterxml.jackson.databind.ObjectMapper
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrMessage.JobNextAction
import metaticket.show.cloudflare.CloudflareClient
import metaticket.show.database.service.DatabaseService
import metaticket.show.datastructure.jobrunr.ShowJobType
import metaticket.show.grpc.ShowMessage
import metaticket.show.metasearch.MetaSearchClient
import metaticket.show.metasearch.MetaSearchUpdateSoldRequest

class JobExecutorServiceImpl(
    private val metaSearchClient: MetaSearchClient,
    private val showDS: DatabaseService,
    private val objectMapper: ObjectMapper,
    private val cloudflareClient: CloudflareClient,
): JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase(), Logging {
    fun JobExecutionResponse(jobId: String, nextAction: JobrunrMessage.JobNextAction): JobrunrMessage.JobExecutionResponse {
        return JobrunrMessage.JobExecutionResponse.newBuilder()
            .setJobId(jobId)
            .setNextAction(nextAction)
            .build()
    }

    override suspend fun executeJob(request: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        return when (ShowJobType.valueOf(request.jobName)) {
            ShowJobType.CreateUpdateMetaSearch -> executeJobCreateUpdateShow(request)
            ShowJobType.UpdateSoldTicket -> executeJobUpdateSoldTicket(request)
        }
    }

    private suspend fun executeJobUpdateSoldTicket(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val reqData = objectMapper.readValue(jr.jobDesc, ShowMessage.UpdateSoldTicketRequest::class.java)
        val show = showDS.updateSoldTicket(reqData.showId, reqData.soldTicket) ?: run {
            logger.error("show {} failed to update sold ticket", reqData.showId)
            return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
        }
        val request = MetaSearchUpdateSoldRequest(
            id = reqData.showId,
            soldTicket = show.soldTicket.toLong()
        )

        if (!metaSearchClient.executeSendUpdateSoldTicket(request)) {
            logger.error("show {} cannot send request to metasearch when execute job {}", reqData.showId, jr)
            return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
        }

        logger.info("show {} update sold ticket {} success", reqData.showId, show.soldTicket)
        return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
    }

    private suspend fun executeJobCreateUpdateShow(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val showId = jr.jobDesc

        val show = showDS.loadShow(showId) ?: kotlin.run {
            logger.info("show {} not found when execute job {}", showId, jr)
            return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
        }

        val request = metaSearchClient.buildCreateUpdateShowRequest(show)

        if (!metaSearchClient.executeSendCreateUpdateShow(request)) {
            launch {
                cloudflareClient.purgeCache(show)
            }
            logger.error("show {} cannot send request to metasearch when execute job {}", showId, jr)
            return JobExecutionResponse(jr.jobId, JobNextAction.CONTINUE)
        }

        logger.info("show {} success sends request to metasearch execute job {}", showId, jr)
        return JobExecutionResponse(jr.jobId, JobNextAction.STOP)
    }
}