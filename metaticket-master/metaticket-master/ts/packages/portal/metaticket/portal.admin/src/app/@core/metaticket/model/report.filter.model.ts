import { LoadInvoiceStatisticsRequest, OrderResponse, PaymentResponse } from './';

export interface CreateReportFilterRequest {
    reportName: string;
    username: string;
    filters: LoadInvoiceStatisticsRequest;
}

export interface LoadReportFilterListRequest {
    showId?: string;
    sessionId?: string;
    username?: string;
}

export type ReportFilterStatus = 'Active' | 'Inactive';

export interface ReportFilterResponse {
    id: string;
    reportKey: string;
    reportName: string;
    status: ReportFilterStatus;
    filter: LoadInvoiceStatisticsRequest;
    createdAt: number;
}

export interface LoadReportResponse {
    orders: OrderResponse[];
    payments: PaymentResponse[];
}
