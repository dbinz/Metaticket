import { formatDate } from '@angular/common';
import { CalendarSkip, CalendarSkipReason, ShowSessionModel } from '../../@core/metaticket/model';

/**
 * Vé theo lịch tính mọi ngày theo giờ Việt Nam (backend lưu ngày dạng 'YYYY-MM-DD'), nên các hàm dưới đây không
 * dựa vào múi giờ của trình duyệt: phép cộng/trừ ngày làm trên UTC, đổi sang epoch thì ghép offset +07:00.
 */
const VN_OFFSET = '+07:00';
const VN_TIMEZONE = '+0700';

/** Giới hạn của backend cho một lần sinh/sửa lịch. */
export const MAX_DAYS_PER_REQUEST = 366;
export const MAX_BOOKING_WINDOW_DAYS = 730;

export interface DayOfWeekOption {
    value: number;
    label: string;
}

/** 1 = T2 … 7 = CN, đúng quy ước của backend. */
export const DAYS_OF_WEEK: DayOfWeekOption[] = [
    { value: 1, label: 'T2' },
    { value: 2, label: 'T3' },
    { value: 3, label: 'T4' },
    { value: 4, label: 'T5' },
    { value: 5, label: 'T6' },
    { value: 6, label: 'T7' },
    { value: 7, label: 'CN' },
];

export const SKIP_REASON_LABELS: Record<CalendarSkipReason, string> = {
    EXISTS: 'Đã có',
    HAS_SALES: 'Đã có vé bán/giữ',
    HELD: 'Khách đang giữ chỗ, thử lại sau',
    PAST_DATE: 'Ngày đã qua',
    NOT_FOUND: 'Không tìm thấy',
};

const pad = (value: number) => String(value).padStart(2, '0');

/** Ngày người dùng chọn trên datepicker (Date theo giờ máy) → 'YYYY-MM-DD'. */
export function toYmd(date: Date): string {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** 'YYYY-MM-DD' → Date theo giờ máy, để đưa ngược vào datepicker. */
export function ymdToLocalDate(ymd: string): Date {
    const [year, month, day] = ymd.split('-').map(Number);
    return new Date(year, month - 1, day);
}

/** Epoch ms của giờ [time] ('HH:mm') ngày [ymd] theo giờ Việt Nam. */
export function vnEpoch(ymd: string, time = '00:00'): number {
    return Date.parse(`${ymd}T${time}:00${VN_OFFSET}`);
}

/** Ngày (theo giờ Việt Nam) chứa thời điểm [epoch]. */
export function epochToVnYmd(epoch: number): string {
    return formatDate(epoch, 'yyyy-MM-dd', 'en-US', VN_TIMEZONE);
}

export function todayVnYmd(): string {
    return epochToVnYmd(Date.now());
}

function ymdToUtc(ymd: string): Date {
    const [year, month, day] = ymd.split('-').map(Number);
    return new Date(Date.UTC(year, month - 1, day));
}

function utcToYmd(date: Date): string {
    return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

export function isValidYmd(value: string | null | undefined): boolean {
    return !!value && /^\d{4}-\d{2}-\d{2}$/.test(value) && utcToYmd(ymdToUtc(value)) === value;
}

/** Thứ của ngày: 1 = T2 … 7 = CN. */
export function dayOfWeekOf(ymd: string): number {
    const day = ymdToUtc(ymd).getUTCDay();
    return day === 0 ? 7 : day;
}

export function dayOfWeekLabel(ymd: string): string {
    return DAYS_OF_WEEK[dayOfWeekOf(ymd) - 1].label;
}

export function addDays(ymd: string, days: number): string {
    const date = ymdToUtc(ymd);
    date.setUTCDate(date.getUTCDate() + days);
    return utcToYmd(date);
}

/** Số ngày trong [from, to], tính cả hai đầu. */
export function countDaysInclusive(from: string, to: string): number {
    return Math.round((ymdToUtc(to).getTime() - ymdToUtc(from).getTime()) / 86_400_000) + 1;
}

/** Các ngày trong [from, to] rơi vào [daysOfWeek] (rỗng = mọi thứ) và không nằm trong [excluded], tăng dần. */
export function listDates(from: string, to: string, daysOfWeek: number[], excluded: Set<string> = new Set()): string[] {
    const result: string[] = [];
    if (!isValidYmd(from) || !isValidYmd(to) || from > to) return result;
    const allowed = new Set(daysOfWeek.length > 0 ? daysOfWeek : DAYS_OF_WEEK.map(d => d.value));
    for (let date = from; date <= to; date = addDays(date, 1)) {
        if (allowed.has(dayOfWeekOf(date)) && !excluded.has(date)) result.push(date);
    }
    return result;
}

/** 'YYYY-MM-DD' → 'dd/MM/yyyy'. */
export function displayYmd(ymd: string | null | undefined): string {
    if (!ymd) return '';
    const [year, month, day] = ymd.split('-');
    return `${day}/${month}/${year}`;
}

/** Khoảng ngày bán của session lịch (startTime/endTime là 00:00 ngày đầu và 23:59 ngày cuối giờ VN). */
export function sessionDateRange(session: ShowSessionModel): { from: string; to: string } {
    return { from: epochToVnYmd(session.startTime), to: epochToVnYmd(session.endTime) };
}

/**
 * Kiểm tra khoảng ngày giống backend (CalendarPlanner.validateRange).
 * @return thông báo lỗi tiếng Việt, null khi hợp lệ.
 */
export function validateDateRange(from: string, to: string, sessionFrom: string, sessionTo: string): string | null {
    if (!isValidYmd(from) || !isValidYmd(to)) return 'Vui lòng chọn khoảng ngày';
    if (from > to) return 'Ngày bắt đầu phải trước ngày kết thúc';
    if (from < sessionFrom || to > sessionTo) {
        return `Khoảng ngày phải nằm trong thời gian của suất (${displayYmd(sessionFrom)} – ${displayYmd(sessionTo)})`;
    }
    if (countDaysInclusive(from, to) > MAX_DAYS_PER_REQUEST) {
        return `Mỗi lần chỉ xử lý tối đa ${MAX_DAYS_PER_REQUEST} ngày`;
    }
    return null;
}

/** Một dòng mô tả ô bị bỏ qua; [categoryName] đổi mã hạng vé sang tên hiển thị. */
export function describeSkip(skip: CalendarSkip, categoryName: (category: string) => string): string {
    const parts = [displayYmd(skip.date)];
    if (skip.category) parts.push(categoryName(skip.category));
    parts.push(SKIP_REASON_LABELS[skip.reason] ?? skip.reason);
    return parts.join(' · ');
}

/** Gom lý do bỏ qua thành câu ngắn cho toast, vd. "2 Đã có vé bán/giữ, 1 Ngày đã qua". */
export function summarizeSkips(skipped: CalendarSkip[]): string {
    const counts = new Map<CalendarSkipReason, number>();
    skipped.forEach(s => counts.set(s.reason, (counts.get(s.reason) ?? 0) + 1));
    return [...counts.entries()]
        .map(([reason, count]) => `${count} ${SKIP_REASON_LABELS[reason] ?? reason}`)
        .join(', ');
}

export function isCalendarSession(session: ShowSessionModel | null | undefined): boolean {
    return session?.sessionType === 'CALENDAR';
}
