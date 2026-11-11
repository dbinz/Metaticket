package metaticket.ticket.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.ticket.ReservationExecutionRetryable
import metaticket.ticket.configuration.Environment
import metaticket.ticket.database.service.TicketReservationDBService
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.datastructure.TicketReservationStatus.*
import metaticket.ticket.exception.HttpBadRequestException
import metaticket.ticket.exception.HttpUnauthorizedException
import metaticket.ticket.pojo.request.CreateTicketReservationRequest
import metaticket.ticket.pojo.request.LoadReservationByTicketId
import metaticket.ticket.pojo.request.ReleaseExpiredReservationRequest
import metaticket.ticket.pojo.request.TicketReservationDetailDTO
import metaticket.ticket.pojo.response.ReservationResponse
import metaticket.ticket.pojo.response.toResponse
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.service.*
import java.util.*


/**
 *
 * @author dongcatchay
 */
class TicketReservationController constructor(
    private val ticketResDB : TicketReservationDBService,
    private val redisClient : RedisAsyncClient,
    private val resExeRetryable: ReservationExecutionRetryable,
    private val coreService: ReservationCoreService,
): Logging {

    // --- Helper Functions ---

    private fun getCustomerUserIdIdOrThrow(call: ApplicationCall): String {
        return call.request.headers["X-Customer-User-Id"] ?: throw HttpUnauthorizedException("Không xác định người dùng")
    }

    private fun getReservationIdFromParamOrThrow(call: ApplicationCall): String {
        val encodedId = call.parameters["id"] ?: throw HttpBadRequestException("Thiếu reservation id")
        return Checksum.decode(encodedId, Environment.secretKey) // Assuming Environment holds the key
    }

    private fun mapCoreExceptionToHttp(e: ReservationException): Pair<HttpStatusCode, String> {
        val statusCode = when (e) {
            is ReservationException.NotFound -> HttpStatusCode.NotFound
            is ReservationException.PreconditionFailed -> HttpStatusCode.PreconditionFailed // Or PreconditionFailed (412)
            is ReservationException.PermissionDenied -> HttpStatusCode.Forbidden
            is ReservationException.Conflict -> HttpStatusCode.Conflict
            is ReservationException.InternalError -> HttpStatusCode.InternalServerError
        }
        // Simple error response format
        return Pair(statusCode, e.message ?: "Lỗi không xác định")
    }


    /**
     * Create reservation then return back to user right after.
     * Client need to make another request to reserve ticket
     */
    suspend fun createTicketReservation(call: ApplicationCall) {
        try {
            val customerUserId = getCustomerUserIdIdOrThrow(call)
            val reqData = call.receive<CreateTicketReservationRequest>() // Use HTTP-specific request model

            // Basic HTTP-level validation (optional, core service also validates)
            if (reqData.details.isEmpty()) {
                throw HttpBadRequestException("Yêu cầu phải có ít nhất một loại vé")
            }
            if (reqData.details.any { it.quantity <= 0 }) {
                throw HttpBadRequestException("Số lượng vé yêu cầu phải lớn hơn 0")
            }

            // Map HTTP Request to Core Service Command DTO
            val command = CreateReservationCommand(
                showId = reqData.showId,
                sessionId = reqData.sessionId,
                customerUserId = customerUserId,
                details = reqData.details.map {
                    TicketReservationDetailDTO( // Map to the Core Service DTO
                        ticketId = it.ticketId,
                        capacityId = it.capacityId, // Pass along if provided, core service will enrich/validate
                        quantity = it.quantity,
                        priceCat = it.priceCat, // Pass along if provided, core service will enrich/validate
                        sid = it.sid // Pass along if provided, core service will enrich/validate
                    )
                }
            )

            // Call Core Service
            val reservationId = coreService.createReservation(command)

            // Encode ID for response
            val encodedId = Checksum.encode(reservationId, Environment.secretKey)

            logger.info("[api-user-$customerUserId] Reservation {} created via HTTP", reservationId)
            call.respond(HttpStatusCode.Created, ReservationResponse(encodedId)) // Use simple response DTO

        } catch (e: ReservationException) {
            logger.warn("Failed to create reservation via HTTP: ${e.message}")
            val (status, body) = mapCoreExceptionToHttp(e)
            call.respond(status, body)
        } catch (e: ContentTransformationException) {
            logger.warn("Bad request body for create reservation: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body: ${e.message}"))
        } catch (e: HttpUnauthorizedException) {
            logger.warn("Unauthorized attempt to create reservation: ${e.message}")
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to e.message))
        } catch (e: HttpBadRequestException) {
            logger.warn("Bad request for create reservation: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
        } catch (e: Throwable) {
            logger.error("Unexpected error in createTicketReservation HTTP handler", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Lỗi hệ thống không xác định"))
        }
    }

    /**
     * Schedule to reserve ticket
     */
    suspend fun reserveTicket(call: ApplicationCall) {
        try {
            val customerUserId = getCustomerUserIdIdOrThrow(call)
            val reservationId = getReservationIdFromParamOrThrow(call)

            val command = ReserveReservationCommand(
                reservationId = reservationId,
                customerUserId = customerUserId,
            )

            // Call Core Service
            val confirmedReservationId = coreService.reserveReservation(command)

            // Encode ID for response
            val encodedId = Checksum.encode(confirmedReservationId, Environment.secretKey)

            logger.info("[api-user-$customerUserId] Reservation {} scheduled to reserve via HTTP", confirmedReservationId)
            // Respond with the encoded ID, confirming the action was accepted
            call.respond(HttpStatusCode.OK, ReservationResponse(encodedId))

        } catch (e: ReservationException) {
            logger.warn("Failed to reserve ticket via HTTP: ${e.message}")
            val (status, body) = mapCoreExceptionToHttp(e)
            call.respond(status, body)
        } catch (e: HttpUnauthorizedException) {
            logger.warn("Unauthorized attempt to reserve ticket: ${e.message}")
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to e.message))
        } catch (e: HttpBadRequestException) {
            logger.warn("Bad request for reserve ticket: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
        } catch (e: Throwable) {
            logger.error("Unexpected error in reserveTicket HTTP handler", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Lỗi hệ thống không xác định"))
        }
    }

    suspend fun fetchSingleTicketReservation(call: ApplicationCall) {
        val userId = call.request.headers.get("X-Customer-User-Id") ?: kotlin.run {
            logger.error("Get user profile but missing user in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val resvId = call.parameters["id"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: return call.respond(HttpStatusCode.BadRequest, "Thiếu reservation id")

        val reservation = redisClient.getReservation(resvId)
            ?: ticketResDB.loadReservationById(resvId)
            ?: return call.respond(HttpStatusCode.NotFound, "Không tìm thấy reservation")

        if (reservation.customerUserId != userId) {
            logger.warn("fetch reservation [{}], not match user [{}] with [{}]", resvId, reservation.customerUserId, userId)
            return call.respond(HttpStatusCode.Forbidden, "Không xác định người dùng")
        }

        call.respond(HttpStatusCode.OK, reservation.toResponse())

        if (reservation.expireInS > 0 && reservation.status == Reserving) {
            if (Date().time - reservation.lastModified.time > 2 * reservation.expireInS * 1000) {
                logger.info("reservation {} force release cause take too long", resvId)
                resExeRetryable.removeReservationOnRedis(resvId)
            }
        }
    }

    suspend fun fetchSingleTicketReservationPrivate(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val resvId = call.parameters["id"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: return call.respond(HttpStatusCode.BadRequest, "Thiếu reservation id")

        val reservation = redisClient.getReservation(resvId)
            ?: ticketResDB.loadReservationById(resvId)
            ?: return call.respond(HttpStatusCode.NotFound, "Không tìm thấy reservation")

        return call.respond(HttpStatusCode.OK, reservation.toResponse())
    }

    suspend fun fetchListTicketReservation(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val ids = call.receive<List<String>>().map { Checksum.decode(it, Environment.secretKey) }
        val reservations = ticketResDB.loadListReservation(ids)
        call.respond(HttpStatusCode.OK, reservations.map { it.toResponse() })
    }

    suspend fun releaseReservation(call: ApplicationCall) {
        try {
            val customerUserId = getCustomerUserIdIdOrThrow(call)
            val reservationId = getReservationIdFromParamOrThrow(call)

            val command = ReleaseReservationCommand(
                reservationId = reservationId,
                customerUserId = customerUserId,
            )

            // Call Core Service
            coreService.releaseReservation(command)

            logger.info("[api-user-$customerUserId] Reservation {} released via HTTP", reservationId)
            call.respond(HttpStatusCode.OK, mapOf("message" to "Reservation released successfully")) // Or NoContent

        } catch (e: ReservationException) {
            logger.warn("Failed to release reservation $call.parameters['id'] via HTTP: ${e.message}")
            val (status, body) = mapCoreExceptionToHttp(e)
            call.respond(status, body)
        } catch (e: HttpUnauthorizedException) {
            logger.warn("Unauthorized attempt to release reservation: ${e.message}")
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to e.message))
        } catch (e: HttpBadRequestException) {
            logger.warn("Bad request for release reservation: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
        } catch (e: Throwable) {
            logger.error("Unexpected error in releaseReservation HTTP handler for $call.parameters['id']", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Lỗi hệ thống không xác định"))
        }
    }

    suspend fun releaseExpiredReservation(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val req = call.receive<ReleaseExpiredReservationRequest>()
        val reservations = ticketResDB.loadReservationByStatusAndUpdatedTime(
            req.showId, req.sessionId, listOf(Reserved, Reserving, Created), req.lastUpdatedTime
        )

        reservations.forEach { r ->
            redisClient.getReservation(r.id)?.let {
                if (r.status !in listOf(Reserved, Reserving, Created)) {
                    logger.error("releaseExpiredReservation, user {}, reservation {} could not release when status is {}", opUserId, r.id, r.status)
                    return call.respond(HttpStatusCode.NotFound, "Không tìm thấy reservation")
                }

                logger.info("releaseExpiredReservation, user {}, reservation {} is releasing", opUserId, r.id)

                resExeRetryable.releaseReservation(r.id)
            } ?: kotlin.run {
                logger.error("releaseExpiredReservation, user {}, reservation {} cannot release expired", opUserId, r.id)
            }
        }
        call.respond(HttpStatusCode.OK)
    }

    suspend fun unlockReservation(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val resId = call.parameters["id"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: return call.respond(HttpStatusCode.BadRequest, "Thiếu reservation id")

        try {
            coreService.unlockReservation(UnlockReservationCommand(resId, opUserId))
            call.respond(HttpStatusCode.OK)
        } catch (e: ReservationException) {
            val (status, body) = mapCoreExceptionToHttp(e)
            call.respond(status, body)
        }
    }

    suspend fun lockTicket(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val reqData = call.receive<CreateTicketReservationRequest>()

        try {
            val reservation = coreService.lockTickets(
                LockTicketsCommand(reqData.showId, reqData.sessionId, opUserId, reqData.details)
            )
            call.respond(HttpStatusCode.OK, reservation.toResponse())
        } catch (e: ReservationException) {
            val (status, body) = mapCoreExceptionToHttp(e)
            call.respond(status, body)
        }
    }

    suspend fun fetchByTicket(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val req = call.receive<LoadReservationByTicketId>()

        val reservations = ticketResDB.loadReservationByTicket(req.tkId, req.reservationStatus)

        return call.respond(HttpStatusCode.OK, reservations.map { it.toResponse() })
    }
}
