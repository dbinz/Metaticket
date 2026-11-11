package metaticket.ticket.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.show.grpc.ShowMessage
import metaticket.show.grpc.ShowServiceGrpcKt

class ShowGateway(
    private val stub: ShowServiceGrpcKt.ShowServiceCoroutineStub,
): Logging {
    suspend fun updateSoldTicket(resId: String, showId: String, soldTicket: Int): Boolean {
        try {
            val requestBuilder = ShowMessage.UpdateSoldTicketRequest.newBuilder()
                .setShowId(showId)
                .setSoldTicket(soldTicket)
            logger.info("reservation {} sending update sold ticket {} for show {}", resId, soldTicket, showId)
            withContext(Dispatchers.IO) { stub.updateSoldTicket(requestBuilder.build()) }
            logger.info("reservation {} send update sold ticket {} for show {}", resId, soldTicket, showId)
            return true
        } catch (t: Throwable) {
            logger.error("reservation {} send update sold ticket {} for show {} exception... ", resId, soldTicket, showId, t)
            return false
        }
    }

    suspend fun getShowSession(showId: String, sessionId: String): ShowMessage.GetSessionResponse? {
        val requestBuilder = ShowMessage.GetSessionByIdRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)

        return try {
            withContext(Dispatchers.IO) { stub.getSessionById(requestBuilder.build()) }
        } catch (t: Throwable) {
            logger.error("load show {} session {} exception... ", showId, sessionId, t)
            return null
        }
    }
}