package metaticket.order.gateway

import com.google.protobuf.Empty
import metaticket.common.logger.Logging
import metaticket.order.datastructure.OrderStatus
import metaticket.ticket.grpc.ReservationServiceGrpcKt
import metaticket.ticket.grpc.TicketMessage

class ReservationGateway(
    private val stub: ReservationServiceGrpcKt.ReservationServiceCoroutineStub
): Logging {
    suspend fun notifyOrderStatusChanged(resId: String, status: OrderStatus): Empty {
        return stub.notifyOrderStatusChanged(TicketMessage.NotifyOrderStatusChangedRequest.newBuilder()
            .setReservationId(resId)
            .setOrderStatus(status.name)
            .build()
        )
    }

    suspend fun getReservation(resId: String): TicketMessage.ReservationProto? {
        return stub.getReservation(TicketMessage.GetReservationRequest.newBuilder()
            .setId(resId)
            .build()
        ).reservation
    }
}