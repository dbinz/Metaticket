package metaticket.order.gateway

import metaticket.ticket.grpc.TicketMessage
import metaticket.ticket.grpc.TicketServiceGrpcKt


class TicketGateway constructor(
    private val stub: TicketServiceGrpcKt.TicketServiceCoroutineStub,
) {
    suspend fun getTicketsByIds(ids: List<String>): List<TicketMessage.TicketProto> {
        return stub.getTicketByIds(TicketMessage.GetTicketByIdsRequest.newBuilder()
            .addAllIds(ids)
            .build()
        ).ticketsList
    }
}