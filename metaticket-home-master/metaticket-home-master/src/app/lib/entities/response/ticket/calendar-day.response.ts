/**
 * Sale state of one visit date of a CALENDAR session. UNAVAILABLE covers past dates, dates beyond the booking window
 * and today after the same-day cutoff.
 */
export type CalendarDayStatus = 'AVAILABLE' | 'SOLD_OUT' | 'CLOSED' | 'UNAVAILABLE';

/** One item of `ticket/calendar/month`; only dates that exist in the session calendar are returned. */
export interface CalendarDayResponse {
  /** "YYYY-MM-DD", Vietnam time. */
  date: string;
  status: CalendarDayStatus;
  minPrice: number | null;
}
