package metaticket.ticketslots.gateway

import metaticket.common.logger.Logging
import metaticket.show.grpc.ShowServiceGrpcKt
import metaticket.show.grpc.ShowMessage
import io.grpc.StatusException

/**
 * Implementation của ShowGateway sử dụng gRPC để gọi show service
 */
class ShowGateway constructor(
    private val showServiceStub: ShowServiceGrpcKt.ShowServiceCoroutineStub
) : Logging {
    
    suspend fun getShowById(showId: String): ShowMessage.GetShowResponse? {
        return try {
            val request = ShowMessage.GetShowByIdRequest.newBuilder()
                .setShowId(showId)
                .build()

            showServiceStub.getShowById(request)

        } catch (e: StatusException) {
            error("Error calling show service for showId: $showId - ${e.message}")
        } catch (e: Exception) {
            error("Unexpected error calling show service for showId: $showId - ${e.message}")
        }
    }
}