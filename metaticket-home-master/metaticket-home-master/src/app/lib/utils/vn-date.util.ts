/**
 * Calendar-ticket dates ("YYYY-MM-DD") are Vietnam calendar days. Vietnam has a fixed UTC+7 offset with no DST, so
 * shifting by a constant yields Asia/Ho_Chi_Minh wall-clock fields whatever the browser (or SSR server) timezone is.
 */
const VN_UTC_OFFSET_MS = 7 * 60 * 60 * 1000;

function pad2(value: number): string {
  return value.toString().padStart(2, '0');
}

/** Epoch ms → "YYYY-MM-DD" of that instant in Vietnam. */
export function toVnDateKey(epochMs: number): string {
  return new Date(epochMs + VN_UTC_OFFSET_MS).toISOString().slice(0, 10);
}

/** Epoch ms → "YYYY-MM" of that instant in Vietnam. */
export function toVnMonthKey(epochMs: number): string {
  return toVnDateKey(epochMs).slice(0, 7);
}

/** "YYYY-MM-DD" → "dd/MM/yyyy" by string split, so no timezone conversion can shift the day. */
export function formatDateKey(dateKey: string): string {
  const [year, month, day] = dateKey.split('-');
  return `${day}/${month}/${year}`;
}

const VN_WEEKDAYS = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

/** "YYYY-MM-DD" → "Thứ Năm, 15/10/2026"; the weekday comes from UTC arithmetic so the viewer's timezone cannot shift it. */
export function formatDateKeyWithWeekday(dateKey: string): string {
  const [year, month, day] = dateKey.split('-').map(Number);
  const weekday = VN_WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
  return `${weekday}, ${formatDateKey(dateKey)}`;
}

/** Epoch ms → "dd/MM/yyyy" in Vietnam. */
export function formatVnDate(epochMs: number): string {
  return formatDateKey(toVnDateKey(epochMs));
}

/** "YYYY-MM" → "Tháng 10/2026". */
export function formatMonthKey(monthKey: string): string {
  const [year, month] = monthKey.split('-');
  return `Tháng ${Number(month)}/${year}`;
}

/** Shifts a "YYYY-MM" key by whole months (negative goes back). */
export function addMonthsToMonthKey(monthKey: string, months: number): string {
  const [year, month] = monthKey.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1 + months, 1)).toISOString().slice(0, 7);
}

/**
 * Cells of a month grid whose weeks start on Monday: `null` pads the first and last week, every other cell is a
 * "YYYY-MM-DD" key. Pure UTC arithmetic, so the grid does not depend on the viewer's timezone.
 */
export function buildMonthGrid(monthKey: string): (string | null)[] {
  const [year, month] = monthKey.split('-').map(Number);
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  // getUTCDay() is 0 for Sunday; shift so Monday is column 0.
  const leadingBlanks = (new Date(Date.UTC(year, month - 1, 1)).getUTCDay() + 6) % 7;

  const cells: (string | null)[] = new Array<string | null>(leadingBlanks).fill(null);
  for (let day = 1; day <= daysInMonth; day++) {
    cells.push(`${monthKey}-${pad2(day)}`);
  }
  while (cells.length % 7 !== 0) {
    cells.push(null);
  }
  return cells;
}
