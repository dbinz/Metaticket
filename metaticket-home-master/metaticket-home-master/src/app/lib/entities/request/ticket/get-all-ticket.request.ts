export interface GetAllTicketRequest {
  showId: string;
  sessionId: string;
  /** "YYYY-MM-DD"; required by the backend for CALENDAR sessions, omitted otherwise. */
  date?: string;
}
