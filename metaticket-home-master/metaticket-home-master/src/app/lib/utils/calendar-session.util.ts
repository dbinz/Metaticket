import { SearchSessionResponse, SessionCalendarResponse, SessionType } from 'src/app/lib/entities';
import { formatDateKeyWithWeekday, formatVnDate, toVnDateKey } from './vn-date.util';

/** Sessions created before calendar tickets have no `sessionType`; they are SINGLE. */
export function isCalendarSession(session?: { sessionType?: SessionType } | null): boolean {
  return session?.sessionType === 'CALENDAR';
}

/** "Mở cửa 08:00–17:00", or '' when the session has no calendar config. */
export function openingHoursLabel(calendar?: SessionCalendarResponse | null): string {
  return calendar ? `Mở cửa ${calendar.openTime}–${calendar.closeTime}` : '';
}

/** "Từ dd/MM/yyyy đến dd/MM/yyyy · Mở cửa HH:mm–HH:mm" — a calendar session has no single start time to show. */
export function calendarSessionRangeLabel(session: {
  startTime: number;
  endTime: number;
  calendar?: SessionCalendarResponse | null;
}): string {
  const range = `Từ ${formatVnDate(session.startTime)} đến ${formatVnDate(session.endTime)}`;
  const hours = openingHoursLabel(session.calendar);
  return hours ? `${range} · ${hours}` : range;
}

/**
 * "Thứ Năm, 15/10/2026 · Mở cửa HH:mm–HH:mm" of a calendar ticket — no clock time, a visit date is a whole day; the
 * hours are omitted when the config is unknown.
 */
export function visitDateLabel(visitDate: string, calendar?: SessionCalendarResponse | null): string {
  const date = formatDateKeyWithWeekday(visitDate);
  const hours = openingHoursLabel(calendar);
  return hours ? `${date} · ${hours}` : date;
}

/**
 * "Đang diễn ra · đến dd/MM/yyyy" for a CALENDAR session running at [now]; '' for anything else, including a session
 * whose first date is still ahead (the caller then shows its start date).
 */
export function ongoingCalendarLabel(
  session?: { sessionType?: SessionType; startTime?: number; endTime?: number } | null,
  now: number = Date.now(),
): string {
  if (!session?.endTime || !session.startTime || !isCalendarSession(session) || session.endTime <= now) {
    return '';
  }
  // Compared by Vietnam day, not by ms: metasearch sends its own "now" as startTime of a running session, and a client
  // clock slightly behind the server must not hide the label. A calendar session starts at 00:00 of its first date.
  if (toVnDateKey(session.startTime) > toVnDateKey(now)) {
    return '';
  }
  return `Đang diễn ra · đến ${formatVnDate(session.endTime)}`;
}

/**
 * Home-visible session closest to [now] — the one list cards are dated by. Metasearch already sends "now" as the
 * startTime of a running calendar session, so such a session wins naturally.
 */
export function nearestHomeSession(
  sessions: SearchSessionResponse[] | null | undefined,
  now: number = Date.now(),
): SearchSessionResponse | undefined {
  const homeVisibleSessions = (sessions ?? []).filter((session) => session.showInHome);
  if (!homeVisibleSessions.length) {
    return undefined;
  }
  return homeVisibleSessions.reduce((prev, curr) =>
    Math.abs(curr.startTime - now) < Math.abs(prev.startTime - now) ? curr : prev,
  );
}
