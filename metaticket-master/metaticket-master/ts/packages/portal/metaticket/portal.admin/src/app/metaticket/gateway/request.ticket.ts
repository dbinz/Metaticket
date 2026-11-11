import { CalendarDayStatus, SeatType } from '../../@core/metaticket/model';

export interface TicketDocDTO {
    sid: string;
    dname: string;
    seatType: SeatType;
    totCap?: number;
    priceCat: string;
    basePrice: number;
    capacityId?: string;
    policy?: string;
}

export interface CreateTicketDocRequest {
    showId: string;
    sessionId: string;
    tickets: TicketDocDTO[];
}

export interface UpdateTicketDocRequest {
    showId: string;
    sessionId: string;
    sid: string;
    dname: string;
    totCap?: number;
    priceCat: string;
    policy?: string;
    capacityId?: string;
}

export interface UpdateTicketSlotRequest {
    showId: string;
    sessionId: string;
    ticketIds: string[];
    slot: number;
}

/** Giá trị mặc định kèm giá trị riêng theo thứ (khóa '1' = T2 … '7' = CN, phải nằm trong daysOfWeek). */
export interface CalendarByDayOfWeek<T> {
    default: T;
    byDayOfWeek?: Record<string, T>;
}

export interface CalendarTicketTypeDTO {
    /** Mã hạng vé = key của category mapping. */
    category: string;
    dname?: string;
    policy?: string;
    price: CalendarByDayOfWeek<number>;
    quota: CalendarByDayOfWeek<number>;
}

/** Ngày dạng 'YYYY-MM-DD'; thứ 1 = T2 … 7 = CN. */
export interface GenerateCalendarRequest {
    showId: string;
    sessionId: string;
    fromDate: string;
    toDate: string;
    daysOfWeek: number[];
    excludeDates?: string[];
    types: CalendarTicketTypeDTO[];
    dailyCapacity?: CalendarByDayOfWeek<number>;
}

/** Chọn ô theo khoảng ngày + thứ (bỏ trống = mọi thứ) + loại vé (bỏ trống = mọi loại). */
export interface CalendarSelectionRequest {
    showId: string;
    sessionId: string;
    fromDate: string;
    toDate: string;
    daysOfWeek?: number[];
    dates?: string[];
    categories?: string[];
}

export interface BulkUpdateCalendarRequest extends CalendarSelectionRequest {
    price?: number;
    quota?: number;
    status?: CalendarDayStatus;
    dailyCapacity?: number;
}

export type DeleteCalendarRequest = CalendarSelectionRequest;
