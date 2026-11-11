export interface CreateReservationRequest {
  showId: string;
  sessionId: string;
  details: CreateReservationTicketRequest[];
}

export interface CreateReservationTicketRequest {
  ticketId: string;
  quantity: number;
  priceCat: string;
  sid: string;
}
