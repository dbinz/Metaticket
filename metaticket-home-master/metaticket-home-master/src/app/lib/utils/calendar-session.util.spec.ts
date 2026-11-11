import { ongoingCalendarLabel, visitDateLabel } from './calendar-session.util';

describe('ongoingCalendarLabel', () => {
  // 13/10/2026 10:00 in Vietnam (UTC+7).
  const NOW = Date.UTC(2026, 9, 13, 3, 0, 0);
  // A calendar session from 01/12/2026 00:00 to 31/12/2026 23:59 (VN).
  const DECEMBER = {
    sessionType: 'CALENDAR' as const,
    startTime: Date.UTC(2026, 10, 30, 17, 0, 0),
    endTime: Date.UTC(2026, 11, 31, 16, 59, 59),
  };

  it('is empty for a calendar session that has not started yet', () => {
    expect(ongoingCalendarLabel(DECEMBER, NOW)).toBe('');
  });

  it('labels a calendar session running at now', () => {
    const running = { ...DECEMBER, startTime: Date.UTC(2026, 9, 1, 17, 0, 0) };
    expect(ongoingCalendarLabel(running, NOW)).toBe('Đang diễn ra · đến 31/12/2026');
  });

  it('labels it from its first day on', () => {
    const firstDay = Date.UTC(2026, 11, 1, 3, 0, 0);
    expect(ongoingCalendarLabel(DECEMBER, firstDay)).toBe('Đang diễn ra · đến 31/12/2026');
  });

  // Metasearch sends its own "now" as startTime of a running session; a client clock a few seconds behind must not
  // hide the label.
  it('still labels a running session whose startTime is the server now, slightly ahead of the client', () => {
    const running = { ...DECEMBER, startTime: NOW + 5_000 };
    expect(ongoingCalendarLabel(running, NOW)).toBe('Đang diễn ra · đến 31/12/2026');
  });

  it('is empty once the session has ended', () => {
    expect(ongoingCalendarLabel(DECEMBER, DECEMBER.endTime + 1)).toBe('');
  });

  it('is empty for a non-calendar session', () => {
    const single = { ...DECEMBER, sessionType: 'SINGLE' as const, startTime: NOW - 1 };
    expect(ongoingCalendarLabel(single, NOW)).toBe('');
  });
});

describe('visitDateLabel', () => {
  const calendar = { openTime: '08:00', closeTime: '17:00', bookingWindowDays: 90 };

  it('shows the visit day with its weekday and opening hours, no clock time', () => {
    expect(visitDateLabel('2026-10-15', calendar)).toBe('Thứ Năm, 15/10/2026 · Mở cửa 08:00–17:00');
  });

  it('shows only the day when the calendar config is unknown', () => {
    expect(visitDateLabel('2026-10-15', null)).toBe('Thứ Năm, 15/10/2026');
  });
});
