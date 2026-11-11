package metaticket.ticket.service

import com.google.protobuf.Empty
import com.google.protobuf.StringValue
import io.grpc.Status
import io.grpc.StatusException
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.ticket.configuration.Environment
import metaticket.ticket.grpc.ReservationServiceGrpcKt
import metaticket.ticket.grpc.TicketMessage
import metaticket.ticket.pojo.request.TicketReservationDetailDTO
import metaticket.ticket.pojo.response.TicketReservationResponse

class ReservationServiceImpl(
    private val coreService: ReservationCoreService,
): ReservationServiceGrpcKt.ReservationServiceCoroutineImplBase(), Logging {

    /**
     * Retrieves reservation information by ID.
     * Checks Redis cache first, then falls back to database if not found.
     *
     * @param request The request containing the reservation ID
     * @return Response containing reservation details
     * @throws StatusException if reservation is not found or on internal errors
     */
    override suspend fun getReservation(request: TicketMessage.GetReservationRequest): TicketMessage.GetReservationResponse {
        return try {
            val reservationDto = coreService.getReservation(request.id)
                ?: throw StatusException(Status.NOT_FOUND.withDescription("Reservation ${request.id} not found"))

            buildGrpcGetResponse(reservationDto) // Helper to map DTO to gRPC response
        } catch (e: ReservationException) {
            throw mapCoreExceptionToGrpcStatus(e) // Helper to map core exceptions
        } catch (e: Throwable) {
            logger.error("Unexpected error in getReservation gRPC for ID ${request.id}", e)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống"))
        }
    }

    /**
     * Handles order status changes and updates reservation status accordingly.
     * Implements complex state transition logic and maintains consistency between Redis and DB.
     *
     * @param request The request containing order status change information
     * @return Empty response
     * @throws StatusException for invalid state transitions or system errors
     */
    override suspend fun notifyOrderStatusChanged(request: TicketMessage.NotifyOrderStatusChangedRequest): Empty {
        return try {
            val command = NotifyOrderStatusChangedCommand(
                reservationId = request.reservationId,
                orderStatus = request.orderStatus
            )
            coreService.notifyOrderStatusChanged(command)
            Empty.getDefaultInstance()
        } catch (e: ReservationException) {
            throw mapCoreExceptionToGrpcStatus(e)
        } catch (e: Throwable) {
            logger.error("Unexpected error in notifyOrderStatusChanged gRPC for request $request", e)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống"))
        }
    }

    override suspend fun createReservation(request: TicketMessage.CreateReservationRequest): StringValue {
        return try {
            logger.info("grpc create reservation {}", request)
            val command = mapGrpcCreateRequestToCommand(request) // Helper to map
            val reservationId = coreService.createReservation(command)
            StringValue.of(reservationId)
        } catch (e: ReservationException) {
            throw mapCoreExceptionToGrpcStatus(e)
        } catch (e: Throwable) {
            logger.error("Unexpected error in createReservation gRPC for request $request", e)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống"))
        }
    }

    override suspend fun releaseReservation(request: TicketMessage.ReleaseReservationRequest): Empty {
        return try {
            logger.info("grpc release reservation {}", request)
            val command = ReleaseReservationCommand(
                reservationId = request.reservationId,
                customerUserId = request.customerUserId,
            )
            coreService.releaseReservation(command)
            Empty.getDefaultInstance()
        } catch (e: ReservationException) {
            throw mapCoreExceptionToGrpcStatus(e)
        } catch (e: Throwable) {
            logger.error("Unexpected error in releaseReservation gRPC for request $request", e)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống"))
        }
    }

    override suspend fun reserveReservation(request: TicketMessage.ReserveReservationRequest): StringValue {
        return try {
            logger.info("grpc reserve reservation {}", request)
            val command = ReserveReservationCommand(
                reservationId = request.reservationId,
                customerUserId = request.customerUserId,
            )
            val reservationId = coreService.reserveReservation(command)
            StringValue.of(reservationId)
        } catch (e: ReservationException) {
            throw mapCoreExceptionToGrpcStatus(e)
        } catch (e: Throwable) {
            logger.error("Unexpected error in reserveReservation gRPC for request $request", e)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống"))
        }
    }

    // --- Helper mapping functions ---

    private fun mapCoreExceptionToGrpcStatus(e: ReservationException): StatusException {
        val status = when (e) {
            is ReservationException.NotFound -> Status.NOT_FOUND
            is ReservationException.PreconditionFailed -> Status.FAILED_PRECONDITION
            is ReservationException.PermissionDenied -> Status.PERMISSION_DENIED
            is ReservationException.Conflict -> Status.ABORTED // Or FAILED_PRECONDITION
            is ReservationException.InternalError -> Status.INTERNAL
        }
        // Include the original message from the core exception
        return StatusException(status.withDescription(e.message))
    }

    private fun mapGrpcCreateRequestToCommand(req: TicketMessage.CreateReservationRequest): CreateReservationCommand {
        val details = req.detailsList.map {
            TicketReservationDetailDTO(
                ticketId = it.ticketId,
                capacityId = it.capacityId.takeIf { it.isNotEmpty() }, // Handle empty string as null
                quantity = it.quantity,
                priceCat = it.priceCat.takeIf { it.isNotEmpty() },
                sid = it.sid.takeIf { it.isNotEmpty() }
            )
        }
        return CreateReservationCommand(
            showId = req.showId,
            sessionId = req.sessionId,
            customerUserId = req.customerUserId,
            details = details
        )
    }

    private fun buildGrpcGetResponse(dto: TicketReservationResponse): TicketMessage.GetReservationResponse {
        val detailsProto = dto.details.map { detail ->
            TicketMessage.ReservationDetailProto.newBuilder()
                .setSid(detail.sid)
                .setTicketId(detail.ticketId)
                .setQuantity(detail.quantity)
                .setPriceCat(detail.priceCat)
                .also {
                    detail.capacityId?.let { capId -> it.setCapacityId(capId) }
                }
                .build()
        }
        val reservationProto = TicketMessage.ReservationProto.newBuilder()
            .setId(Checksum.decode(dto.id, Environment.secretKey))
            .setShowId(dto.showId)
            .setSessionId(dto.sessionId)
            .setStatus(dto.status.name) // Use enum name
            .addAllDetails(detailsProto)
            .setExpireInS(dto.expireInS)
            .setCreatedAt(dto.createdAt)
            .setLastModified(dto.updatedAt)
            .also {
                dto.customerUserId?.let { customerUserId -> it.setCustomerUserId(customerUserId) }
            }
            .build()

        return TicketMessage.GetReservationResponse.newBuilder()
            .setReservation(reservationProto)
            .build()
    }
}