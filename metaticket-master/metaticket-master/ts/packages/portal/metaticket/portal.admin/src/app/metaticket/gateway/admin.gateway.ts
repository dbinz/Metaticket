import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
    BookingUploadErrorResponse,
    BookingUploadSuccessResponse,
    CreateReportFilterRequest,
    DownloadOrderReportRequest,
    InvoiceStatisticsByShow,
    LoadReportFilterListRequest,
    LoadReportResponse,
    PaymentType,
    ReportFilterResponse,
    SessionCalendarConfig,
    SessionType,
    ShowSessionDraftModel,
    ShowSessionDraftOrderCustomFieldModel,
    ShowSessionDraftPromotionModel,
    TicketBookingResponse,
} from '../../@core/metaticket/model';
import { Observable, throwError } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi, publicApi } from '../../../environments/environment';

@Injectable()
export class AdminGateway {
    constructor(private http: HttpClient) {}

    fetchShowSessionsDraft(showId: string): Observable<ShowSessionDraftModel[]> {
        return this.http
            .get<ShowSessionDraftModel[]>(privateApi(`/admin/show/sessions/draft/fetch`), {
                params: { showId: showId },
            })
            .pipe(take(1));
    }

    createShowSessionDraft(
        showId: string,
        sessionName: string,
        code: string,
        address: string,
        startTime: number,
        endTime: number,
        requireDelivery: boolean,
        orderNote: string,
        cityId: string,
        isFree: boolean,
        showInHome: boolean,
        ledgendImage: string | undefined,
        ledgendImageUrl: string | undefined,
        eventSquareImage: string | undefined,
        eventSquareImageUrl: string | undefined,
        eventNameImage: string | undefined,
        eventNameImageUrl: string | undefined,
        manageTicket: boolean | undefined,
        qrcodeEmail: boolean | undefined,
        slug?: string,
        sessionType?: SessionType,
        calendar?: SessionCalendarConfig | null,
    ): Observable<ShowSessionDraftModel> {
        return this.http
            .post<ShowSessionDraftModel>(
                privateApi('/admin/show/session/draft/create'),
                {
                    showId: showId || undefined,
                    sessionName: sessionName || undefined,
                    code: code || undefined,
                    address: address || undefined,
                    startTime: startTime || undefined,
                    endTime: endTime || undefined,
                    requireDelivery: requireDelivery || undefined,
                    orderNote: orderNote || undefined,
                    cityId: cityId || undefined,
                    isFree: isFree || undefined,
                    showInHome: showInHome || undefined,
                    ledgendImage: ledgendImage || undefined,
                    ledgendImageUrl: ledgendImageUrl || undefined,
                    eventSquareImage: eventSquareImage || undefined,
                    eventSquareImageUrl: eventSquareImageUrl || undefined,
                    eventNameImage: eventNameImage || undefined,
                    eventNameImageUrl: eventNameImageUrl || undefined,
                    manageTicket: manageTicket || undefined,
                    qrcodeEmail: qrcodeEmail || undefined,
                    slug: slug || undefined,
                    sessionType: sessionType || undefined,
                    calendar: calendar || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    updateShowSessionDraft(
        id: String,
        sessionName: string,
        code: string,
        address: string,
        startTime: number,
        endTime: number,
        requireDelivery: boolean,
        orderNote: string,
        cityId: string,
        isFree: boolean,
        showInHome: boolean,
        ledgendImage: string | undefined,
        ledgendImageUrl: string | undefined,
        eventSquareImage: string | undefined,
        eventSquareImageUrl: string | undefined,
        eventNameImage: string | undefined,
        eventNameImageUrl: string | undefined,
        manageTicket: boolean | undefined,
        qrcodeEmail: boolean | undefined,
        slug?: string,
        sessionType?: SessionType,
        calendar?: SessionCalendarConfig | null,
    ): Observable<ShowSessionDraftModel> {
        return this.http
            .post<ShowSessionDraftModel>(
                privateApi('/admin/show/session/draft/update'),
                {
                    id: id || undefined,
                    sessionName: sessionName || undefined,
                    code: code || undefined,
                    address: address || undefined,
                    startTime: startTime || undefined,
                    endTime: endTime || undefined,
                    requireDelivery: requireDelivery || undefined,
                    orderNote: orderNote || undefined,
                    cityId: cityId || undefined,
                    isFree: isFree || undefined,
                    showInHome: showInHome || undefined,
                    ledgendImage: ledgendImage || undefined,
                    ledgendImageUrl: ledgendImageUrl || undefined,
                    eventSquareImage: eventSquareImage || undefined,
                    eventSquareImageUrl: eventSquareImageUrl || undefined,
                    eventNameImage: eventNameImage || undefined,
                    eventNameImageUrl: eventNameImageUrl || undefined,
                    manageTicket: manageTicket || undefined,
                    qrcodeEmail: qrcodeEmail || undefined,
                    slug: slug || undefined,
                    sessionType: sessionType || undefined,
                    calendar: calendar || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    updateShowSessionDraftPayment(
        id: string,
        paymentOffline?: boolean,
        paymentNote?: string,
        pgAccountCode?: string,
        paymentMethods?: PaymentType[],
    ): Observable<ShowSessionDraftModel> {
        return this.http
            .post<ShowSessionDraftModel>(
                privateApi('/admin/show/session/draft/update/payment'),
                {
                    id: id || undefined,
                    paymentOffline: paymentOffline || undefined,
                    paymentNote: paymentNote || undefined,
                    pgAccountCode: pgAccountCode || undefined,
                    paymentMethods: paymentMethods || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    updateShowSessionDraftSeatPlan(
        id: string,
        seatPlanId?: string,
        edType?: string,
        extraInfo?: string,
    ): Observable<ShowSessionDraftModel> {
        return this.http
            .post<ShowSessionDraftModel>(
                privateApi('/admin/show/session/draft/update/seatplan'),
                {
                    id: id || undefined,
                    seatPlanId: seatPlanId || undefined,
                    edType: edType || undefined,
                    extraInfo: extraInfo ?? '',
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    updateShowSessionDraftMailTemplates(id: String, mailTemplates: unknown[]): Observable<ShowSessionDraftModel> {
        return this.http
            .post<ShowSessionDraftModel>(
                privateApi('/admin/show/session/draft/update/mail/templates'),
                {
                    id: id || undefined,
                    mailTemplates: mailTemplates || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    fetchShowSessionDraftPromotions(sessionDraftId: string): Observable<ShowSessionDraftPromotionModel[]> {
        return this.http
            .get<ShowSessionDraftPromotionModel[]>(privateApi(`/admin/show/session/draft/promotions/fetch`), {
                params: { sessionDraftId: sessionDraftId },
            })
            .pipe(take(1));
    }

    updateShowSessionDraftPromotions(
        id: string,
        name: string,
        promotionData: string,
    ): Observable<ShowSessionDraftPromotionModel> {
        return this.http
            .post<ShowSessionDraftPromotionModel>(
                privateApi('/admin/show/session/draft/update/promotion'),
                {
                    id: id || undefined,
                    data: promotionData || undefined,
                    name: name || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    createShowSessionDraftPromotion(
        sessionDraftId: string,
        name: string,
        promotionData: string,
    ): Observable<ShowSessionDraftPromotionModel> {
        return this.http
            .post<ShowSessionDraftPromotionModel>(
                privateApi(`/admin/show/session/draft/create/promotion`),
                {
                    sessionDraftId: sessionDraftId || undefined,
                    name: name || undefined,
                    data: promotionData || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    fetchShowSessionDraftOrderCustomFields(sessionDraftId: string): Observable<ShowSessionDraftOrderCustomFieldModel> {
        return this.http
            .get<ShowSessionDraftOrderCustomFieldModel>(
                privateApi(`/admin/show/session/draft/order/custom/fields/fetch`),
                {
                    params: { sessionDraftId: sessionDraftId },
                },
            )
            .pipe(take(1));
    }

    createShowSessionDraftOrderCustomFields(
        sessionDraftId: String,
        customFields: unknown[],
    ): Observable<ShowSessionDraftOrderCustomFieldModel> {
        return this.http
            .post<ShowSessionDraftOrderCustomFieldModel>(
                privateApi('/admin/show/session/draft/create/order/custom/fields'),
                {
                    sessionDraftId: sessionDraftId || undefined,
                    customFields: customFields || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    updateShowSessionDraftOrderCustomFields(
        id: String,
        customFields: unknown[],
    ): Observable<ShowSessionDraftOrderCustomFieldModel> {
        return this.http
            .post<ShowSessionDraftOrderCustomFieldModel>(
                privateApi('/admin/show/session/draft/update/order/custom/fields'),
                {
                    id: id || undefined,
                    customFields: customFields || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    createShowSession(id: String): Observable<ShowSessionDraftModel> {
        return this.http
            .post<ShowSessionDraftModel>(
                privateApi('/admin/show/session/draft/session/create'),
                {
                    id: id,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    deleteShowSessionDraft(id: string): Observable<{ id: string; deleted: boolean }> {
        return this.http
            .post<{ id: string; deleted: boolean }>(
                privateApi('/admin/show/session/draft/delete'),
                { id },
                { withCredentials: true },
            )
            .pipe(take(1));
    }

    updateShowSession(id: String): Observable<ShowSessionDraftModel> {
        return this.http
            .post<ShowSessionDraftModel>(
                privateApi('/admin/show/session/draft/session/update'),
                {
                    id: id,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    // ==================== REPORT FILTER ==========================
    createReportFilter(req: CreateReportFilterRequest): Observable<string> {
        return this.http
            .post<string>(privateApi('/admin/report-filter/create'), req, {
                withCredentials: true,
                responseType: 'text' as 'json',
            })
            .pipe(take(1));
    }

    loadReportFilterList(req: LoadReportFilterListRequest): Observable<ReportFilterResponse[]> {
        return this.http
            .post<ReportFilterResponse[]>(privateApi('/admin/report-filter/list'), req, {
                withCredentials: true,
            })
            .pipe(take(1));
    }

    disableReportFilter(reportId: string): Observable<ReportFilterResponse> {
        return this.http
            .post<ReportFilterResponse>(
                privateApi('/admin/report-filter/disable'),
                {
                    reportId: reportId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    enableReportFilter(reportId: string): Observable<ReportFilterResponse> {
        return this.http
            .post<ReportFilterResponse>(
                privateApi('/admin/report-filter/enable'),
                {
                    reportId: reportId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    removeReportFilter(reportId: string): Observable<string> {
        return this.http
            .post<string>(
                privateApi('/admin/report-filter/remove'),
                {
                    reportId: reportId,
                },
                {
                    withCredentials: true,
                    responseType: 'text' as 'json',
                },
            )
            .pipe(take(1));
    }

    loadInvoiceStatistics(reportKey: string): Observable<InvoiceStatisticsByShow[]> {
        return this.http
            .get<InvoiceStatisticsByShow[]>(publicApi(`/admin/report-filter/load-invoice-statistics/${reportKey}`), {
                withCredentials: true,
            })
            .pipe(take(1));
    }

    loadReport(reportKey: string, loadOrderRequest: DownloadOrderReportRequest): Observable<LoadReportResponse> {
        return this.http
            .post<LoadReportResponse>(publicApi(`/admin/report-filter/load-report/${reportKey}`), loadOrderRequest, {
                withCredentials: true,
            })
            .pipe(take(1));
    }

    loadTicketBooking(showId: string, sessionId: string): Observable<TicketBookingResponse[]> {
        return this.http
            .get<TicketBookingResponse[]>(
                privateApi(`/admin/booking-ticket/fetch?showId=${showId}&sessionId=${sessionId}`),
                {
                    withCredentials: true,
                    headers: {
                        'Content-Type': 'application/json',
                    },
                },
            )
            .pipe(take(1));
    }

    /**
     * Uploads the booking file along with showId and sessionId.
     * @param file The file to upload (.csv, .xlsx, .xls)
     * @param showId The Show ID
     * @param sessionId The Session ID
     * @returns An Observable of the HttpResponse (includes status code and body)
     */
    bookingTicketFromFile(
        file: File,
        showId: string,
        sessionId: string,
        customerUserId: string,
    ): Observable<HttpResponse<BookingUploadSuccessResponse | BookingUploadErrorResponse>> {
        if (!file || !showId || !sessionId) {
            return throwError(new Error('File, Show ID, and Session ID are required.'));
        }

        const formData = new FormData();
        formData.append('file', file, file.name); // Key 'file' assumed by backend
        formData.append('showId', showId);
        formData.append('sessionId', sessionId);
        formData.append('customerUserId', customerUserId);

        // We use observe: 'response' to get the full HTTP response including status code
        return this.http
            .post<BookingUploadSuccessResponse | BookingUploadErrorResponse>(
                privateApi(`/admin/booking-ticket`),
                formData,
                {
                    observe: 'response', // Get the full response
                    // reportProgress: true, // Add this upload progress events
                },
            )
            .pipe(take(1));
    }
}
