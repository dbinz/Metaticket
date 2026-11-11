package metaticket.order.gateway

import metaticket.show.grpc.ShowMessage
import metaticket.show.grpc.ShowServiceGrpcKt

class ShowGateway(
    private val stub: ShowServiceGrpcKt.ShowServiceCoroutineStub
) {
    suspend fun getShow(id: String): ShowMessage.GetShowResponse {
        return stub.getShowById(ShowMessage.GetShowByIdRequest.newBuilder().setShowId(id).build())
    }

    suspend fun generateUniqueId(showId: String, sessionId: String): String {
        return stub.generateUniqueId(ShowMessage.GenerateUniqueIdRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .build()
        ).value
    }
}