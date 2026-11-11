import { buildMonthGrid, formatDateKeyWithWeekday, toVnDateKey } from './vn-date.util';

// Inputs are built with Date.UTC and the functions under test use UTC arithmetic only, so these results must not
// depend on the timezone of the machine running the tests.
describe('toVnDateKey', () => {
  it('keeps the Vietnam day until 23:59:59 VN (16:59:59 UTC)', () => {
    expect(toVnDateKey(Date.UTC(2026, 9, 12, 16, 59, 59))).toBe('2026-10-12');
  });

  it('rolls to the next Vietnam day at 00:00 VN (17:00 UTC)', () => {
    expect(toVnDateKey(Date.UTC(2026, 9, 12, 17, 0, 0))).toBe('2026-10-13');
  });
});

describe('buildMonthGrid (weeks start on Monday)', () => {
  const leadingBlanks = (cells: (string | null)[]): number => cells.findIndex((cell) => cell !== null);
  const days = (cells: (string | null)[]): string[] => cells.filter((cell): cell is string => cell !== null);

  it('pads 6 cells before a month starting on Sunday (01/11/2026)', () => {
    const cells = buildMonthGrid('2026-11');
    expect(leadingBlanks(cells)).toBe(6);
    expect(cells[6]).toBe('2026-11-01');
    expect(days(cells).length).toBe(30);
    expect(cells.length % 7).toBe(0);
  });

  it('pads nothing before a month starting on Monday (01/06/2026)', () => {
    expect(leadingBlanks(buildMonthGrid('2026-06'))).toBe(0);
  });

  it('has 29 days in February of a leap year (2028)', () => {
    const february = days(buildMonthGrid('2028-02'));
    expect(february.length).toBe(29);
    expect(february[february.length - 1]).toBe('2028-02-29');
  });

  it('has 28 days in February of a common year (2026)', () => {
    const february = days(buildMonthGrid('2026-02'));
    expect(february.length).toBe(28);
    expect(february[february.length - 1]).toBe('2026-02-28');
  });
});

describe('formatDateKeyWithWeekday', () => {
  it('prefixes the Vietnamese weekday', () => {
    expect(formatDateKeyWithWeekday('2026-10-15')).toBe('Thứ Năm, 15/10/2026');
  });

  it('names Sunday "Chủ Nhật"', () => {
    expect(formatDateKeyWithWeekday('2026-10-18')).toBe('Chủ Nhật, 18/10/2026');
  });

  it('handles the first day of a month and a leap day', () => {
    expect(formatDateKeyWithWeekday('2026-10-01')).toBe('Thứ Năm, 01/10/2026');
    expect(formatDateKeyWithWeekday('2028-02-29')).toBe('Thứ Ba, 29/02/2028');
  });
});
