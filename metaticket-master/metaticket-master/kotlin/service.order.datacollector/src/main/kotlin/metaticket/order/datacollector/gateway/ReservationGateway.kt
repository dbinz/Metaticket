package metaticket.order.datacollector.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticket.grpc.ReservationServiceGrpcKt
import metaticket.ticket.grpc.TicketMessage

class ReservationGateway(
    private val stub: ReservationServiceGrpcKt.ReservationServiceCoroutineStub,
): Logging {
    suspend fun getReservation(resId: String): TicketMessage.ReservationProto? {
        val requestBuilder = TicketMessage.GetReservationRequest.newBuilder()
            .setId(resId)

        return try {
            withContext(Dispatchers.IO) {
                stub.getReservation(requestBuilder.build()).reservation
            }
        } catch (t: Throwable) {
            logger.error("reservation {}, failed to fetch", resId, t)
            null
        }
    }
}