import { CustomFieldTmpl, PaymentType } from './';

export type ShowSessionStatus =
    | 'Created'
    | 'ApprovalWait'
    | 'Rejected'
    | 'OpeningWait'
    | 'Open'
    | 'Paused'
    | 'Stopped'
    | 'Terminated';

export interface ShowModel {
    id: string;
    showName: string;
    code: string;
    slug?: string;
    website: string;
    eventCategoryCode: string;
    eventCategory: string;
    bannerImageUrl: string;
    detailImageUrl: string;
    thumbnailUrl: string;
    logoHostUrl: string;
    logoShowUrl: string;
    status: string;
    description: string;
    ticketDescription: string;
    faq: string;
    policy: string;
    shipPrice: number;
    sessions: ShowSessionModel[];
    pinned: boolean;
    pinOrder?: number | null;
    hideSessions: boolean;
    createdAt: number;
}

/** Compact show card on the banner pin board / picker. */
export interface BannerPinShowSummary {
    id: string;
    showName: string;
    code: string;
    eventCategory: string;
    eventCategoryCode: string;
    thumbnailUrl: string;
    bannerImageUrl: string;
    pinOrder?: number | null;
}

export interface BannerPinSlot {
    pinOrder: number;
    show: BannerPinShowSummary | null;
}

export interface BannerPinBoard {
    slots: BannerPinSlot[];
}

export interface BannerPinCandidatePage {
    content: BannerPinShowSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface EventCategoryModel {
    id: string;
    name: string;
}

export interface UploadShowImageResponseModel {
    fileName: string;
    fileUrl: string;
}

export interface CityModel {
    id: string;
    name: string;
    code: string;
}

/** SINGLE: suất tại một thời điểm. CALENDAR: vé theo lịch, bán vé cho từng ngày tham quan trong khoảng startTime–endTime. */
export type SessionType = 'SINGLE' | 'CALENDAR';

/** Cấu hình chung của session lịch; giá/số lượng từng ngày nằm ở màn Lịch bán vé (service.ticket). */
export interface SessionCalendarConfig {
    openTime: string; // 'HH:mm' giờ VN
    closeTime: string;
    bookingWindowDays: number;
    /** Ngừng bán trong ngày trước giờ đóng cửa X phút; null = không bán vé cho chính hôm nay. */
    sameDayCutoffMinutes?: number | null;
}

export interface ShowSessionModel {
    id: string;
    code: string;
    slug?: string;
    sessionName?: string;
    status: ShowSessionStatus;
    time: string;
    venue: string;
    startTime: number;
    endTime: number;
    seatPlanId: string;
    edType: string;
    ledgendImageUrl: string;
    eventSquareImageUrl: string;
    eventNameImageUrl: string;
    /** Optional HTML for booking "Thông tin bổ sung". */
    extraInfo?: string;
    requireDelivery: boolean;
    paymentOffline: boolean;
    paymentNote: string;
    mailPluginTemplate: ShowSessionMailTemplateModel[];
    orderNote: string;
    city?: CityModel;
    isFree: boolean;
    showInHome: boolean;
    pgAccountCode?: string;
    paymentMethods?: PaymentType[];
    manageTicket?: boolean;
    qrcodeEmail?: boolean;
    /** Thiếu = SINGLE (dữ liệu cũ). */
    sessionType?: SessionType;
    calendar?: SessionCalendarConfig | null;
}
export interface ShowSessionDraftModel {
    id: string;
    showId: string;
    sessionId?: string;
    showName?: string;
    sessionName?: string;
    code: string;
    slug?: string;
    address: string;
    startDate: number;
    startTime: number;
    endDate: number;
    endTime: number;
    requireDelivery: boolean;
    orderNote: string;
    paymentOffline: boolean;
    paymentNote: string;
    data: string;
    categoryData?: string;
    paymentData: string;
    promotionsData: string;
    seatPlanId: string;
    edType: string;
    mailPluginTemplate: ShowSessionMailTemplateModel[];
    city?: CityModel;
    isFree: boolean;
    showInHome: boolean;
    ledgendImage?: string;
    ledgendImageUrl?: string;
    eventSquareImage?: string;
    eventSquareImageUrl?: string;
    eventNameImage?: string;
    eventNameImageUrl?: string;
    /** Optional HTML for booking "Thông tin bổ sung". */
    extraInfo?: string;
    pgAccountCode?: string;
    paymentMethods?: PaymentType[];
    manageTicket?: boolean;
    qrcodeEmail?: boolean;
    /** Thiếu = SINGLE (dữ liệu cũ). */
    sessionType?: SessionType;
    calendar?: SessionCalendarConfig | null;
}

export interface ShowSessionMailTemplateModel {
    orderStatus: string;
    plugin: string;
    templateId: string;
}

export interface ShowSessionDraftPromotionModel {
    id: string;
    sessionDraftId: string;
    data: string;
    name: string;
}

export interface ShowSessionDraftOrderCustomFieldModel {
    id: string;
    sessionDraftId: string;
    customFields: CustomFieldTmpl[];
}
