import { StateReason, TicketSlotStatus, TicketOptStatus } from 'src/app/lib/enums';

export interface TicketSlotStateResponse {
  stateIdx: number;
  ownerId?: string;
  ownerPhone?: string;
  status: TicketSlotStatus;
  reason: StateReason;
  senderId?: string;
  recipientId?: string;
  timestamp: number;
}

export interface TicketSlotResponse {
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
  optStatus: TicketOptStatus;
  lastModified: number;
  createdAt: number;
  version: number;
  states: TicketSlotStateResponse[];
  /** "YYYY-MM-DD" (Vietnam): the only day a calendar ticket is valid on; absent for normal sessions. */
  visitDate?: string;
}
