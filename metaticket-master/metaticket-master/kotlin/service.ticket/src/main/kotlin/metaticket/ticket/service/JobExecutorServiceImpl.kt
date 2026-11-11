package metaticket.ticket.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import metaticket.common.checksum.Checksum
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrMessage.JobNextAction
import metaticket.ticket.ReservationExecutionRetryable
import metaticket.ticket.configuration.Environment
import metaticket.ticket.database.service.TicketReservationDBService
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.datastructure.TicketReservationStatus.*
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import metaticket.ticket.pojo.db.TicketReservationDocument
import metaticket.ticket.datastructure.jobrunr.TicketJobType
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.redis.UpdateRedisFailedConstants
import org.bson.types.ObjectId

class JobExecutorServiceImpl(
    private val ticketResDB: TicketReservationDBService,
    private val redisAsyncClient : RedisAsyncClient,
    private val resExeRetryable: ReservationExecutionRetryable,
    private val mapper: ObjectMapper,
) : JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase(), Logging {
    fun jobrunrResponse(jobId: String, nextAction: JobrunrMessage.JobNextAction): JobrunrMessage.JobExecutionResponse {
        return JobrunrMessage.JobExecutionResponse.newBuilder()
            .setJobId(jobId)
            .setNextAction(nextAction)
            .build()
    }
    
    override suspend fun executeJob(request: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        return when (TicketJobType.valueOf(request.jobName)) {
            TicketJobType.NotifyOrderPaying,
            TicketJobType.NotifyOrderSuccessful,
            TicketJobType.NotifyOrderCancelled,
            TicketJobType.NotifyOrderFailed -> executeJobUpdateReservationStatus(request)

            TicketJobType.CreateReservationDb -> executeJobCreateReservationDb(request)

            TicketJobType.ReleaseReservation -> executeJobReleaseReservation(request)

            TicketJobType.UpdateReservationStatusDb -> executeJobUpdateReservationStatusDb(request)

            TicketJobType.RemoveReservationRedis -> executeJobRemoveReservationRedis(request)

            TicketJobType.RollbackRemainingCapRedis -> executeJobRollbackRemainingCapRedis(request)

            TicketJobType.RollbackReservedCapDb -> executeJobRollbackReservedCapDb(request)
        }
    }

    private suspend fun executeJobRollbackReservedCapDb(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val (resId, tickets) = try {
            val typeRef: TypeReference<Pair<String, List<TicketReservationDetailsDocument>>> =
                object : TypeReference<Pair<String, List<TicketReservationDetailsDocument>>>() {}
            val data = mapper.readValue(jr.jobDesc, typeRef)
            data.first to data.second
        } catch (t: Throwable) {
            logger.error("job {} exception when executing {} ... ", jr.jobId, jr, t)
            return jobrunrResponse(jr.jobId, JobNextAction.STOP)
        }

        resExeRetryable.releaseReservedCapInDb(resId, tickets)

        logger.info("reservation {} is rollback reserved capacity {} in db after execute job {}", resId, tickets, jr)
        return jobrunrResponse(jr.jobId, JobNextAction.STOP)
    }

    private suspend fun executeJobRollbackRemainingCapRedis(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val (resId, tickets) = try {
            val typeRef: TypeReference<Pair<String, List<TicketReservationDetailsDocument>>> =
                object : TypeReference<Pair<String, List<TicketReservationDetailsDocument>>>() {}
            val data = mapper.readValue(jr.jobDesc, typeRef)
            data.first to data.second
        } catch (t: Throwable) {
            logger.error("job {} exception when executing {} ... ", jr.jobId, jr, t)
            return jobrunrResponse(jr.jobId, JobNextAction.STOP)
        }

        resExeRetryable.releaseRemainingCapOnRedis(resId, tickets)

        logger.info("reservation {} is rollback remaining capacity {} on redis after execute job {}", resId, tickets, jr)
        return jobrunrResponse(jr.jobId, JobNextAction.STOP)
    }

    private suspend fun executeJobRemoveReservationRedis(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val resId =jr.jobDesc

        resExeRetryable.removeReservationOnRedis(resId)

        logger.info("reservation {} is removing after execute job {}", resId, jr)
        return jobrunrResponse(jr.jobId, JobNextAction.STOP)
    }

    private suspend fun executeJobUpdateReservationStatusDb(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val (resId, status) = try {
            val typeRef: TypeReference<Pair<String, TicketReservationStatus>> =
                object : TypeReference<Pair<String, TicketReservationStatus>>() {}
            val data = mapper.readValue(jr.jobDesc, typeRef)
            data.first to data.second
        } catch (t: Throwable) {
            logger.error("job {} exception when executing {} ... ", jr.jobId, jr, t)
            return jobrunrResponse(jr.jobId, JobNextAction.STOP)
        }

        val oldStatus = ticketResDB.loadReservationById(resId)?.status ?: status
        resExeRetryable.updateReservationStatusDb(resId, oldStatus, status)

        logger.info("reservation {} is updating to {} after execute job {}", resId, status, jr)
        return jobrunrResponse(jr.jobId, JobNextAction.STOP)
    }

    private suspend fun executeJobCreateReservationDb(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val reservation = try {
            mapper.readValue(jr.jobDesc, TicketReservationDocument::class.java)
        } catch (t: Throwable) {
            logger.error("job {} exception when executing {} ... ", jr.jobId, jr, t)
            return jobrunrResponse(jr.jobId, JobNextAction.STOP)
        }

        resExeRetryable.createReservationDb(reservation)

        logger.info("reservation {} is creating after execute job {}", reservation.id, jr)
        return jobrunrResponse(jr.jobId, JobNextAction.STOP)
    }

    private suspend fun executeJobReleaseReservation(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val resId = jr.jobDesc

        resExeRetryable.releaseReservation(resId)

        logger.info("reservation {} is releasing after execute job {}", resId, jr)
        return jobrunrResponse(jr.jobId, JobNextAction.STOP)
    }

    private suspend fun executeJobUpdateReservationStatus(jr: JobrunrMessage.JobExecutionRequest): JobrunrMessage.JobExecutionResponse {
        val newResStatus = when (TicketJobType.valueOf(jr.jobName)) {
            TicketJobType.NotifyOrderPaying -> OrderPaying
            TicketJobType.NotifyOrderSuccessful -> OrderSuccessful
            TicketJobType.NotifyOrderCancelled -> OrderCancelled
            TicketJobType.NotifyOrderFailed -> OrderFailed
            else -> {
                logger.error("Not handle job {}", jr.jobName)
                return jobrunrResponse(jr.jobId, JobNextAction.STOP)
            }
        }

        val resId = if (ObjectId.isValid(jr.jobDesc)) jr.jobDesc else Checksum.decode(jr.jobDesc, Environment.secretKey)

        val reservation = try {
            redisAsyncClient.getReservation(resId)
                ?: run {
                    return@run ticketResDB.loadReservationById(resId) ?: run {
                        logger.error("reservation {} not found when executing the job {}.. ", resId, jr)
                        return jobrunrResponse(jr.jobId, JobNextAction.STOP)
                    }
                }
        } catch (t: Throwable) {
            logger.error("reservation {} exception when executing the job {} ... ", resId, jr, t)
            return jobrunrResponse(jr.jobId, JobNextAction.CONTINUE)
        }

        val oldResStatus = reservation.status

        if (oldResStatus == newResStatus && oldResStatus in listOf(OrderCancelled, OrderFailed, OrderSuccessful, OrderPaying)) {
            logger.info("reservation {} status is updated already {} when executing job {}", resId, newResStatus, jr)
            return jobrunrResponse(jr.jobId, JobNextAction.STOP)
        }

        if (oldResStatus !in listOf(Reserved, OrderPaying)) {
            logger.error("reservation {} not allow updating from {} to {} when executing job {}", resId, oldResStatus, newResStatus, jr)
            return jobrunrResponse(jr.jobId, JobNextAction.STOP)
        }
        if (oldResStatus == Reserved && newResStatus != OrderPaying) {
            logger.error("reservation {} not allow updating from {} to {} when executing job {}", resId, oldResStatus, newResStatus, jr)
            return jobrunrResponse(jr.jobId, JobNextAction.STOP)
        }
        if (oldResStatus == OrderPaying && newResStatus !in listOf(OrderSuccessful, OrderFailed, OrderCancelled)) {
            logger.error("reservation {} not allow updating from {} to {} when executing job {}", resId, oldResStatus, newResStatus, jr)
            return jobrunrResponse(jr.jobId, JobNextAction.STOP)
        }

        val updateRedis = try {
            redisAsyncClient.updateReservationStatus(resId, oldResStatus, newResStatus)
        } catch (t: Throwable) {
            logger.error("reservation {} exception when executing the job {} ... ", resId, jr, t)
            return jobrunrResponse(jr.jobId, JobNextAction.CONTINUE)
        }

        if (updateRedis == UpdateRedisFailedConstants.NOT_EXIST) {
            logger.error("reservation {} not found when executing the job {}", resId, jr)
            resExeRetryable.updateReservationStatusDb(resId, oldResStatus, newResStatus)
            return jobrunrResponse(jr.jobId, JobNextAction.STOP)
        }
        if (updateRedis == UpdateRedisFailedConstants.FAILED) {
            logger.error("reservation {} cannot update on redis when executing the job {}", resId, jr)
            return jobrunrResponse(jr.jobId, JobNextAction.CONTINUE)
        }

        if (newResStatus in listOf(OrderCancelled, OrderFailed)) {
            launch { resExeRetryable.releaseRemainingCapOnRedis(resId, reservation.details) }
            launch { resExeRetryable.releaseReservedCapInDb(resId, reservation.details) }
        }

        logger.info("reservation {} updated to {} on redis", resId, newResStatus)

        resExeRetryable.updateReservationStatusDb(resId, oldResStatus, newResStatus)

        logger.info("reservation {} is updating to {} after execute job {}", resId, newResStatus, jr)
        return jobrunrResponse(jr.jobId, JobNextAction.STOP)
    }
}