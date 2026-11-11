export type TicketSlotStatus = 'CREATED' | 'QR_GENERATED' | 'OPENED' | 'CHECKED_IN' | 'CLOSED';

export interface TicketSlotModel {
    id: string;
    showId: string;
    sessionId: string;
    orderId: string;
    orderCode: string;
    ticketId: string;
    priceCat: string;
    sid: string;
    ticketIdx: number;
    slotIdx: number;
    originalOwnerId: string;
    currentOwnerId: string;
    currentStatus: TicketSlotStatus;
}
