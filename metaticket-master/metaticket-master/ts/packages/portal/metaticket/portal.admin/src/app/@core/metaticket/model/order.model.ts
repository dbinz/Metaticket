import { SeatType } from './';

export interface OrderResponse {
    id: string;
    reservationId: string;
    paymentId?: string;
    showId: string;
    sessionId: string;
    code: string;
    status: OrderStatus;
    customerName: string;
    customerPhone: string;
    customerEmail: string;
    delivery?: DeliveryInfo;
    customFields: CustomFieldDataResponse[];
    promotionDetails: PromotionResponse[];
    tickets: TicketInfoResponse[];
    total: number;
    createdAt: number;
    /** Ngày tham quan 'YYYY-MM-DD' của đơn vé theo lịch. */
    visitDate?: string;
}

export interface PromotionResponse {
    name: string;
    priceDiscount: number;
}

export interface OrderPromotionModel {
    id: string;
    pluginName: string;
    configuration: string;
}

export interface DeliveryInfo {
    deliveryName: string;
    deliveryPhone: string;
    provinceName: string;
    districtName: string;
    wardName: string;
    address: string;
}

export interface CustomFieldDataResponse {
    id: string;
    name: string;
    value?: string;
}

export interface TicketInfoResponse {
    id: string;
    dname?: string;
    sid: string;
    seatType: SeatType;
    priceCat: string; // price category
    quantity: number; // requested capacity
    basePrice: number;
}

export interface OrderSummaryModel {
    reservationId: string;
    orderId: string;
    orderCode: string;
    orderStatus: string;
    email: string;
    paymentOffline: boolean;
    paymentNode?: string;
    paymentInfo?: PayInfoModel;
    orderDetail: OrderDetailModel;
    buyerInfo: BuyerInfoModel;
    recipientInfo?: RecipientInfoModel;
    customFields?: CustomField[];
    createdAt: number;
}

export interface PayInfoModel {
    paymentId?: string;
    payTime: string;
    payMethod: string;
    payStatus: string;
}

export interface OrderDetailModel {
    eventName: string;
    eventTime: string;
    venue: string;
    prices: OrderPriceModel[];
    promotions: PromotionModel[];
    total: number;
    orderNote?: string;
}

export interface OrderPriceModel {
    name: string;
    price: number;
    quantity: number;
}

export interface PromotionModel {
    name: string;
    price: number;
}

export interface BuyerInfoModel {
    name: string;
    phone: string;
    email: string;
}

export interface RecipientInfoModel {
    name: string;
    phone: string;
    address: string;
}

export interface CustomField {
    name: string;
    value?: string;
}

export enum CustomFieldType {
    String = 'String',
    Boolean = 'Boolean',
    Date = 'Date',
    List = 'List',
    DataCollector = 'DataCollector',
    Number = 'Number',
}

export enum OrderStatus {
    Created = 'Created',
    WaitingForPayment = 'WaitingForPayment',
    PaymentProcessing = 'PaymentProcessing',
    PaymentSucceed = 'PaymentSucceed',
    PaymentFailed = 'PaymentFailed',
    PaymentCancelled = 'PaymentCancelled',
    PaymentRefunded = 'PaymentRefunded',
    Expired = 'Expired',
    Failed = 'Failed',
    Canceled = 'Canceled',
    PaymentPending = 'PaymentPending',
}

export interface CustomFieldTmpl {
    id: string;
    type: CustomFieldType;
    displayName?: string;
    constraints?: string;
    note?: string;
}

export interface BasicResponse {
    code: number;
    message: string;
    data?: any;
}

export interface OrderCustomFieldsModel {
    id: string;
    showId: string;
    sessionId: string;
    customFields: CustomFieldTmpl[];
}

export interface DownloadOrderReportRequest {
    startDate?: number;
    endDate?: number;
    status?: string[];
    showId?: string;
    sessionId?: string;
    /** Lọc đơn vé theo lịch theo ngày tham quan 'YYYY-MM-DD' (gồm cả hai đầu). */
    visitDateFrom?: string;
    visitDateTo?: string;
}

export interface LoadInvoiceStatisticsRequest {
    startDate?: number;
    endDate?: number;
    status?: string[];
    showId?: string;
    sessionId?: string;
    visitDateFrom?: string;
    visitDateTo?: string;
}

export interface InvoiceStatisticsByShow {
    showId: string;
    totalTicket: number;
    totalPrice: number;
    discount: number;
    revenue: number;
    bySession: InvoiceStatisticsBySession[];
}

export interface InvoiceStatisticsBySession {
    sessionId: string;
    totalTicket: number;
    totalPrice: number;
    discount: number;
    commission: number;
    revenue: number;
    byCategory: InvoiceStatisticsByCategory[];
}

export interface InvoiceStatisticsByCategory {
    cat: string;
    basePrice: number;
    totalTicket: number;
    totalPrice: number;
}

export interface OrderModel {
    id: string;
    reservationId: string;
    paymentId: string;
    showId: string;
    sessionId: string;
    code: string;
    status: string;
    payStatus: string;
    customerName: string;
    customerPhone: string;
    customerEmail: string;
    eventName: string;
    time: string;
    price: string;
}
