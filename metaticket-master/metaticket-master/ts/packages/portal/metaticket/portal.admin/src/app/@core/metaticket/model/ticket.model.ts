import { SeatType } from './';

export declare type TicketReservationStatus =
    | 'Created'
    | 'Reserving'
    | 'Reserved'
    | 'Locked'
    | 'Unlocked'
    | 'Removing'
    | 'Removed'
    | 'OrderPaying'
    | 'OrderSuccessful'
    | 'OrderCancelled'
    | 'OrderFailed';

export declare type TicketStatus = 'Created' | 'Opened' | 'Blocked' | 'Closed';

export type CatFormGroupType = 'SeatZone' | 'PolicyConf';

export interface TicketModel {
    id: string;
    sid: string;
    showId: string;
    sessionId: string;
    seatType: SeatType;
    status: TicketStatus;
    resCap: number;
    totCap: number;
    remCap: number;
    priceCat: string;
    basePrice: number;
    dname?: string;
    capacityId?: string;
    policy?: string;
    ticketSlots?: number;
    /** 'YYYY-MM-DD' of a calendar ticket; tickets of different dates share the same sid. */
    calendarDate?: string;
    currentStateIdx: number;
    lastModified: number;
    createdAt: number;
    version: number;
}

export interface TicketReservationDetailModel {
    ticketId: string;
    quantity: number;
    priceCat: string;
    sid: string;
}

export interface TicketReservationModel {
    id: string;
    showId: string;
    sessionId: string;
    status: TicketReservationStatus;
    expireInS: number;
    details: TicketReservationDetailModel[];
    createdAt: number;
}

export interface CountTicketByShowModel {
    sessionId: string;
    noTicket: number;
}

export type CalendarDayStatus = 'OPEN' | 'CLOSED';

/** Lý do một ô (ngày, loại vé) bị bỏ qua khi sinh/sửa/xóa lịch. */
export type CalendarSkipReason = 'EXISTS' | 'HAS_SALES' | 'HELD' | 'PAST_DATE' | 'NOT_FOUND';

export interface CalendarSkip {
    date: string;
    /** Mã hạng vé (key của category mapping); null = cả ngày. */
    category: string | null;
    reason: CalendarSkipReason;
}

export interface CalendarAdminTicket {
    ticketId: string;
    category: string;
    priceCat: string;
    dname: string | null;
    price: number;
    quota: number;
    remaining: number;
    /** Đã bán + đang giữ. */
    reserved: number;
    /** true = đã có vé bán/giữ: chỉ được tăng số lượng hoặc ngừng bán. */
    locked: boolean;
}

export interface CalendarAdminCapacity {
    capacityId: string;
    total: number;
    remaining: number;
    reserved: number;
}

export interface CalendarAdminDay {
    date: string;
    status: CalendarDayStatus;
    capacity: CalendarAdminCapacity | null;
    tickets: CalendarAdminTicket[];
}

export interface GenerateCalendarResponse {
    createdDays: number;
    createdTickets: number;
    skipped: CalendarSkip[];
}

export interface BulkUpdateCalendarResponse {
    updated: number;
    skipped: CalendarSkip[];
}

export interface DeleteCalendarResponse {
    deletedTickets: number;
    deletedDays: number;
    skipped: CalendarSkip[];
}
