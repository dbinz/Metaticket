package metaticket.admin.gateway

import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.admin.exception.PeerServiceException
import metaticket.common.logger.Logging
import metaticket.ticket.grpc.TicketMessage
import metaticket.ticket.grpc.TicketServiceGrpcKt
import java.io.IOException

class TicketGateway(
    private val stub: TicketServiceGrpcKt.TicketServiceCoroutineStub
): Logging {
    /**
     * Fetches ticket details for a specific show and session using gRPC.
     *
     * @param showId The ID of the show.
     * @param sessionId The ID of the session.
     * @return List of TicketResponse objects.
     * @throws PeerServiceException wrapping gRPC communication errors.
     */
    suspend fun loadTicketByShowAndSession(
        showId: String,
        sessionId: String,
    ): List<TicketMessage.TicketProto> {
        val request = TicketMessage.GetTicketByShowAndSessionRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .build()

        return executeGrpcCall("loadTicketByShowAndSession") {
            val response = stub.getTicketByShowAndSession(request)
            response.ticketsList
        }
    }

    suspend fun loadTicketBySids(showId: String, sessionId: String, sids: List<String>): List<TicketMessage.TicketProto> {
        val request = TicketMessage.GetTicketBySidsRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .addAllSids(sids)
            .build()

        return executeGrpcCall("loadTicketBySids") {
            val response = stub.getTicketBySids(request)
            response.ticketsList
        }
    }

    suspend fun loadTicketByIds(ids: List<String>): List<TicketMessage.TicketProto> {
        val request = TicketMessage.GetTicketByIdsRequest.newBuilder()
            .addAllIds(ids)
            .build()

        return executeGrpcCall("loadTicketByIds") {
            stub.getTicketByIds(request).ticketsList
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
            val description = e.status.description ?: "No description"
            logger.error("gRPC error during '$operationName': ${e.status.code} - $description", e.cause)
            // Map gRPC status to a user-friendly message or keep the description
            val errorMessage = when (e.status.code) {
                Status.Code.NOT_FOUND -> "Resource not found for '$operationName': $description"
                Status.Code.FAILED_PRECONDITION -> "Precondition failed for '$operationName': $description"
                Status.Code.UNAVAILABLE -> "Ticket service unavailable for '$operationName'. Please try again later."
                Status.Code.INTERNAL -> "Lỗi hệ thống during '$operationName': $description"
                Status.Code.PERMISSION_DENIED -> "Permission denied for '$operationName': $description"
                // Add other specific mappings as needed
                else -> "Ticket service request '$operationName' failed: ${e.status.code} - $description"
            }
            throw PeerServiceException(errorMessage, e)
        } catch (e: IOException) { // Catch potential lower-level network issues
            logger.error("Network error during '$operationName': ${e.message}", e)
            throw PeerServiceException("Network error during '$operationName': ${e.message}", e)
        } catch (e: Throwable) {
            // Catch any other unexpected exceptions
            logger.error("Unexpected error during '$operationName': ${e.message}", e)
            // Avoid wrapping PeerServiceException again if it was somehow thrown inside grpcCall
            if (e is PeerServiceException) throw e
            throw PeerServiceException("Unexpected error during '$operationName': ${e.message}", e)
        }
    }
}
