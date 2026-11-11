package metaticket.ticket.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.common.checksum.Checksum
import metaticket.jobrunr.datastructure.JobExecutorServiceName
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.show.datastructure.jobrunr.ShowJobType
import metaticket.show.grpc.ShowMessage
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import metaticket.ticket.pojo.db.TicketReservationDocument
import metaticket.ticket.datastructure.jobrunr.TicketJobType
import metaticket.ticket.configuration.Environment
import java.util.*

class JobrunrGateway(
    private val stub: JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub,
    private val env : ApplicationEnvironment,
    private val mapper: ObjectMapper,
): Logging {
    private val retryExpire = env.config.property("jrunr.expireReservation.retry").getString().toInt()

    /**
     * Send a job request to job runr to schedule.
     *
     */
    suspend fun scheduleExpireReservation(resId: String, expireInS: Long): Boolean {
        try {
            val jr = JobrunrMessage.JobrunrRequest.newBuilder()
                .setJobId(UUID.randomUUID().toString())
                .setService(JobExecutorServiceName.TicketService.name)
                .setJobName(TicketJobType.ReleaseReservation.name)
                .setJobDesc(resId)
                .setDelayInSecond(expireInS)
                .setRetry(retryExpire)
                .build()
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("reservation {} sent job schedule expire", resId)
            return true
        } catch (t: Throwable) {
            logger.error("reservation {} send job schedule expire exception... ", resId, t)
            return false
        }
    }

    suspend fun scheduleReleaseReservation(resId: String): Boolean {
        try {
            val jr = JobrunrMessage.JobrunrRequest.newBuilder()
                .setJobId(UUID.randomUUID().toString())
                .setService(JobExecutorServiceName.TicketService.name)
                .setJobName(TicketJobType.ReleaseReservation.name)
                .setJobDesc(resId)
                .setDelayInSecond(0)
                .setRetry(-1)
                .build()
            logger.info("reservation {} send job schedule release", resId)
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("reservation {} sent job schedule release", resId)
            return true
        } catch (t: Throwable) {
            logger.error("reservation {} send job schedule release exception... ", resId, t)
            return false
        }
    }

    suspend fun scheduleCreateReservationDb(reservation: TicketReservationDocument): Boolean {
        val resId = reservation.id
        try {
            val jr = JobrunrMessage.JobrunrRequest.newBuilder()
                .setJobId(UUID.randomUUID().toString())
                .setService(JobExecutorServiceName.TicketService.name)
                .setJobName(TicketJobType.CreateReservationDb.name)
                .setJobDesc(mapper.writeValueAsString(reservation))
                .setDelayInSecond(0)
                .setRetry(-1)
                .build()
            logger.info("reservation {} send job schedule to create in db", resId)
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("reservation {} sent job schedule to create in db", resId)
            return true
        } catch (t: Throwable) {
            logger.error("reservation {} send job schedule to create exception... ", resId, t)
            return false
        }
    }

    suspend fun scheduleUpdateReservationStatusDb(resId: String, oldStatus: TicketReservationStatus, newStatus: TicketReservationStatus): Boolean {
        try {
            val jr = JobrunrMessage.JobrunrRequest.newBuilder()
                .setJobId(UUID.randomUUID().toString())
                .setService(JobExecutorServiceName.TicketService.name)
                .setJobName(TicketJobType.UpdateReservationStatusDb.name)
                .setJobDesc(mapper.writeValueAsString(resId to newStatus))
                .setDelayInSecond(0)
                .setRetry(-1)
                .build()
            logger.info("reservation {} send job schedule update status from {} to {}", resId, oldStatus, newStatus)
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("reservation {} sent job schedule update status from {} to {}", resId, oldStatus, newStatus)
            return true
        } catch (t: Throwable) {
            logger.error("reservation {} send job schedule update status from {} to {} exception... ", resId, oldStatus, newStatus, t)
            return false
        }


    }

    suspend fun scheduleRemoveReservationRedis(resId: String): Boolean {

        try {
            val jr = JobrunrMessage.JobrunrRequest.newBuilder()
                .setJobId(UUID.randomUUID().toString())
                .setService(JobExecutorServiceName.TicketService.name)
                .setJobName(TicketJobType.RemoveReservationRedis.name)
                .setJobDesc(resId)
                .setDelayInSecond(0)
                .setRetry(-1)
                .build()
            logger.info("reservation {} send job schedule remove on redis", resId)
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("reservation {} send job schedule remove on redis", resId)
            return true
        } catch (t: Throwable) {
            logger.error("reservation {} send job schedule remove on redis exception... ", resId, t)
            return false
        }
    }

    suspend fun scheduleRollbackReservedCapDb(resId: String, tickets: List<TicketReservationDetailsDocument>): Boolean {
        try {
            val jr = JobrunrMessage.JobrunrRequest.newBuilder()
                .setJobId(UUID.randomUUID().toString())
                .setService(JobExecutorServiceName.TicketService.name)
                .setJobName(TicketJobType.RollbackReservedCapDb.name)
                .setJobDesc(mapper.writeValueAsString(resId to tickets))
                .setDelayInSecond(0)
                .setRetry(-1)
                .build()
            logger.info("reservation {} send job schedule rollback reserved capacity in db", resId)
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("reservation {} send job schedule rollback reserved capacity in db", resId)
            return true
        } catch (t: Throwable) {
            logger.error("reservation {} send job schedule rollback reserved capacity in db exception... ", resId, t)
            return false
        }
    }

    suspend fun scheduleRollbackRemainingCapRedis(resId: String, tickets: List<TicketReservationDetailsDocument>): Boolean {
        try {
            val jr = JobrunrMessage.JobrunrRequest.newBuilder()
                .setJobId(UUID.randomUUID().toString())
                .setService(JobExecutorServiceName.TicketService.name)
                .setJobName(TicketJobType.RollbackRemainingCapRedis.name)
                .setJobDesc(mapper.writeValueAsString(resId to tickets))
                .setDelayInSecond(0)
                .setRetry(-1)
                .build()
            logger.info("reservation {} send job schedule rollback remaining capacity on redis", resId)
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("reservation {} sent job schedule rollback remaining capacity on redis", resId)
            return true
        } catch (t: Throwable) {
            logger.error("reservation {} send job schedule rollback remaining capacity on redis exception... ", resId, t)
            return false
        }
    }

    suspend fun scheduleUpdateSoldTicket(resId: String, showId: String, soldTicket: Int): Boolean {
        try {
            val jr = JobrunrMessage.JobrunrRequest.newBuilder()
                .setJobId(UUID.randomUUID().toString())
                .setService(JobExecutorServiceName.ShowService.name)
                .setJobName(ShowJobType.UpdateSoldTicket.name)
                .setJobDesc(mapper.writeValueAsString(ShowMessage.UpdateSoldTicketRequest.newBuilder().setShowId(showId).setSoldTicket(soldTicket).build()))
                .setDelayInSecond(0)
                .setRetry(-1)
                .build()
            logger.info("reservation {} sending job update sold ticket {} for show {}", resId, soldTicket, showId)
            withContext(Dispatchers.IO) { stub.scheduleJob(jr) }
            logger.info("reservation {} send job update sold ticket {} for show {}", resId, soldTicket, showId)
            return true
        } catch (t: Throwable) {
            logger.error("reservation {} send job update sold ticket {} for show {} exception... ", resId, soldTicket, showId, t)
            return false
        }

    }
}