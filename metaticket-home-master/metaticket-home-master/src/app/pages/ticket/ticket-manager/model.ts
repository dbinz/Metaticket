import { SessionCalendarResponse, TicketSlotResponse } from 'src/app/lib/entities';

export interface TicketSlotByOrderModel {
  showId: string;
  sessionId: string;
  evenName: string;
  eventTime: number;
  city: string;
  venus: string;
  bannerUrl: string;
  orderId: string;
  orderCode: string;
  ticketSlots: TicketSlotResponse[];
  /** Calendar orders: the visit date replaces the session start time (all slots of an order share it). */
  visitDate?: string;
  calendar?: SessionCalendarResponse | null;
}
