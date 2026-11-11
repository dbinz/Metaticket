package metaticket.admin.gateway

import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.admin.exception.PeerServiceException
import metaticket.common.logger.Logging
import metaticket.ticket.grpc.ReservationServiceGrpcKt
import metaticket.ticket.grpc.TicketMessage
import java.io.IOException

class ReservationGateway(
    private val stub: ReservationServiceGrpcKt.ReservationServiceCoroutineStub
): Logging {
    /**
     * Creates a ticket reservation using gRPC.
     *
     * @param showId The ID of the show.
     * @param sessionId The ID of the session.
     * @param details Details of the ticket to reserve.
     * @param customerUserId The ID of the customer making the reservation.
     * @param opUserId The ID of the operator/system performing the action (for logging/auditing).
     * @return The created ReservationResponse containing the reservation ID.
     * @throws PeerServiceException wrapping gRPC communication errors.
     */
    suspend fun createReservation(
        opUserId: String,
        customerUserId: String,
        showId: String,
        sessionId: String,
        details: TicketMessage.ReservationDetailProto,
    ): String {
        val request = TicketMessage.CreateReservationRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .setCustomerUserId(customerUserId)
            .setOpUserId(opUserId)
            .addDetails(details)
            .build()

        return executeGrpcCall("createReservation") {
            stub.createReservation(request).value
        }
    }

    /**
     * Confirms (reserves) a previously created reservation using gRPC.
     *
     * @param reservationId The ID of the reservation to confirm.
     * @param customerUserId The ID of the customer owning the reservation.
     * @param opUserId The ID of the operator/system performing the action.
     * @return The updated ReservationResponse containing the reservation ID.
     * @throws PeerServiceException wrapping gRPC communication errors.
     */
    suspend fun reserveTicket(
        opUserId: String,
        customerUserId: String,
        reservationId: String,
    ): String {
        val request = TicketMessage.ReserveReservationRequest.newBuilder()
            .setReservationId(reservationId)
            .setCustomerUserId(customerUserId) // Added parameter
            .setOpUserId(opUserId)             // Added parameter
            .build()

        return executeGrpcCall("reserveTicket") {
            stub.reserveReservation(request).value
        }
    }

    /**
     * Gets a reservation by ID using gRPC.
     *
     * @param reservationId The ID of the reservation to retrieve.
     * @return The ReservationProto containing reservation details and status.
     * @throws PeerServiceException wrapping gRPC communication errors.
     */
    suspend fun getReservation(
        reservationId: String,
    ): TicketMessage.ReservationProto {
        val request = TicketMessage.GetReservationRequest.newBuilder()
            .setId(reservationId)
            .build()

        return executeGrpcCall("getReservation") {
            stub.getReservation(request).reservation
        }
    }

    /**
     * Releases (cancels) a previously created reservation using gRPC.
     *
     * @param reservationId The ID of the reservation to release.
     * @param customerUserId The ID of the customer owning the reservation.
     * @param opUserId The ID of the operator/system performing the action.
     * @throws PeerServiceException wrapping gRPC communication errors.
     */
    suspend fun releaseTicket(
        opUserId: String,
        customerUserId: String,
        reservationId: String,
    ) { // Returns Unit on success
        val request = TicketMessage.ReleaseReservationRequest.newBuilder()
            .setReservationId(reservationId)
            .setCustomerUserId(customerUserId) // Added parameter
            .setOpUserId(opUserId)             // Added parameter
            .build()

        // No specific return value needed, just check for exceptions
        executeGrpcCall("releaseTicket") {
            stub.releaseReservation(request) // Returns Empty
            // If no exception, the call succeeded. Return Unit implicitly.
        }
    }

    /**
     * Executes a gRPC suspend call, handling StatusException and converting it to PeerServiceException.
     * @param T The expected return type of the gRPC call block.
     * @param operationName A descriptive name of the operation for logging/error messages.
     * @param grpcCall The suspend lambda function representing the gRPC call.
     * @return The result of the gRPC call block of type T.
     * @throws PeerServiceException wrapping gRPC communication errors.
     */
    private suspend inline fun <T> executeGrpcCall(
        operationName: String,
        crossinline grpcCall: suspend () -> T
    ): T {
        return try {
            // Execute within IO dispatcher context if calls are blocking,
            // though gRPC stubs are often non-blocking. Adjust if needed.
            withContext(Dispatchers.IO) {
                grpcCall()
            }
        } catch (e: StatusException) {
            // Log the gRPC status error
            val description = e.status.description ?: "Lỗi không xác định"
            logger.error("gRPC error during '$operationName': ${e.status.code} - $description", e.cause)
            // Map gRPC status to a user-friendly message or keep the description
            val errorMessage = when (e.status.code) {
                Status.Code.NOT_FOUND -> "Không tồn tại '$operationName': $description"
                Status.Code.FAILED_PRECONDITION -> "Không hợp lệ '$operationName': $description"
                Status.Code.UNAVAILABLE -> "Hệ thống chưa sẵn sàng '$operationName'. Please try again later."
                Status.Code.INTERNAL -> "Lỗi hệ thống '$operationName': $description"
                Status.Code.PERMISSION_DENIED -> "Không có quyền truy cập '$operationName': $description"
                // Add other specific mappings as needed
                else -> "Lỗi chưa xác định '$operationName': ${e.status.code} - $description"
            }
            throw PeerServiceException(errorMessage, e)
        } catch (e: IOException) { // Catch potential lower-level network issues
            logger.error("Network error during '$operationName': ${e.message}", e)
            throw PeerServiceException("Lỗi kết nối '$operationName': ${e.message}", e)
        } catch (e: Throwable) {
            // Catch any other unexpected exceptions
            logger.error("Unexpected error during '$operationName': ${e.message}", e)
            // Avoid wrapping PeerServiceException again if it was somehow thrown inside grpcCall
            throw PeerServiceException("Lỗi không xác định '$operationName': ${e.message}", e)
        }
    }
}