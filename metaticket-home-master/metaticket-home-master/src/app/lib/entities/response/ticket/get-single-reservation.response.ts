import { TicketReservationStatus } from '../../../enums/ticket-reservation-status';

export interface GetSingleReservationResponse {
  id: string;
  showId: string;
  sessionId: string;
  expireInS: number;
  status: TicketReservationStatus;
  message?: string;
}
