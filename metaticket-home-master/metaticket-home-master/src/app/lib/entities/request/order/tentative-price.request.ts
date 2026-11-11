export interface TentativePriceRequest {
  showId: string;
  sessionId: string;
  reservationId?: string;
  promotionCode?: string;
  tickets: TentativeTicketRequest[];
}

export interface TentativeTicketRequest {
  ticketId: string;
  quantity: number;
  priceCat: string;
}
