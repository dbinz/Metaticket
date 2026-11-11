package metaticket.jobrunr.service

import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.StatusException
import metaticket.common.logger.Logging
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.jobrunr.utils.scheduleRequest
import metaticket.jobrunr.utils.toJobRequest

class JobrunrServiceImpl: JobrunrServiceGrpcKt.JobrunrServiceCoroutineImplBase(), Logging {

    override suspend fun scheduleJob(request: JobrunrMessage.JobrunrRequest): Empty {
        try {
            val jrq = request.toJobRequest()
            logger.info("job {} scheduling job request {}", request.jobId, jrq)
            scheduleRequest(jrq)
            return Empty.getDefaultInstance()
        } catch (t: Throwable) {
            logger.error("job {} failed to schedule job {} ...", request.jobId, request, t)
            throw StatusException(Status.INTERNAL.withDescription(t.message ?: "Lỗi jobrunr"))
        }
    }
}