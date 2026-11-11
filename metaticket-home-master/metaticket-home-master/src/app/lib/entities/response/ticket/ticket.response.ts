export interface TicketResponse {
  id: string;
  sid: string;
  priceCat: string;
  basePrice: number;
  remCap: number; // remaining capacity
  policy?: string;
}

export type TicketsWithVersionResponse = {
  tickets: TicketResponse[];
  version: number;
};
