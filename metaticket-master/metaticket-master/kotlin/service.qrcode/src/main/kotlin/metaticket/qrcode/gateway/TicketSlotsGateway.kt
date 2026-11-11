package metaticket.qrcode.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticketslots.grpc.TicketSlotsMessage
import metaticket.ticketslots.grpc.TicketSlotsServiceGrpcKt

class TicketSlotsGateway(
    private val stub: TicketSlotsServiceGrpcKt.TicketSlotsServiceCoroutineStub
): Logging {
    suspend fun scanTicketSlot(request: TicketSlotsMessage.ScanTicketSlotRequest): TicketSlotsMessage.ScanTicketSlotResponse {
        return withContext(Dispatchers.IO) {
            stub.scanTicketSlot(request)
        }
    }
}