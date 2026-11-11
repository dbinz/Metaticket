import { HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AdminService } from '../../@core/metaticket/data.service';
import {
    BookingUploadErrorResponse,
    BookingUploadSuccessResponse,
    CreateReportFilterRequest,
    LoadReportFilterListRequest,
    LoadReportResponse,
    ReportFilterResponse,
    TicketBookingResponse,
} from '../../@core/metaticket/model';
import { AdminGateway } from '../gateway/admin.gateway';
import { Observable } from 'rxjs';
import {
    DownloadOrderReportRequest,
    InvoiceStatisticsByShow,
    PaymentType,
    SessionCalendarConfig,
    SessionType,
    ShowSessionDraftModel,
    ShowSessionDraftOrderCustomFieldModel,
    ShowSessionDraftPromotionModel,
} from '../';

@Injectable()
export class MetaticketAdminService extends AdminService {
    constructor(private adminGateway: AdminGateway) {
        super();
    }

    override createShowSessionDraft$(
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
        mangeTicket: boolean | undefined,
        qrcodeEmail: boolean | undefined,
        slug?: string,
        sessionType?: SessionType,
        calendar?: SessionCalendarConfig | null,
    ): Observable<ShowSessionDraftModel> {
        return this.adminGateway.createShowSessionDraft(
            showId,
            sessionName,
            code,
            address,
            startTime,
            endTime,
            requireDelivery,
            orderNote,
            cityId,
            isFree,
            showInHome,
            ledgendImage,
            ledgendImageUrl,
            eventSquareImage,
            eventSquareImageUrl,
            eventNameImage,
            eventNameImageUrl,
            mangeTicket,
            qrcodeEmail,
            slug,
            sessionType,
            calendar,
        );
    }
    override updateShowSessionDraft$(
        id: string,
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
        ledgendImage: string | null,
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
        return this.adminGateway.updateShowSessionDraft(
            id,
            sessionName,
            code,
            address,
            startTime,
            endTime,
            requireDelivery,
            orderNote,
            cityId,
            isFree,
            showInHome,
            ledgendImage,
            ledgendImageUrl,
            eventSquareImage,
            eventSquareImageUrl,
            eventNameImage,
            eventNameImageUrl,
            manageTicket,
            qrcodeEmail,
            slug,
            sessionType,
            calendar,
        );
    }

    override updateShowSessionDraftPayment$(
        id: string,
        paymentOffline?: boolean,
        paymentNote?: string,
        pgAccountCode?: string,
        paymentMethods?: PaymentType[],
    ): Observable<ShowSessionDraftModel> {
        return this.adminGateway.updateShowSessionDraftPayment(
            id,
            paymentOffline,
            paymentNote,
            pgAccountCode,
            paymentMethods,
        );
    }

    override updateShowSessionDraftSeatPlan$(
        id: string,
        seatPlanId: string,
        edType: string,
        extraInfo?: string,
    ): Observable<ShowSessionDraftModel> {
        return this.adminGateway.updateShowSessionDraftSeatPlan(id, seatPlanId, edType, extraInfo);
    }

    override getShowSessionsDraft$(showId: string): Observable<ShowSessionDraftModel[]> {
        return this.adminGateway.fetchShowSessionsDraft(showId);
    }

    override updateShowSessionDraftPromotions$(
        id: string,
        name: string,
        promotionData: string,
    ): Observable<ShowSessionDraftPromotionModel> {
        return this.adminGateway.updateShowSessionDraftPromotions(id, name, promotionData);
    }

    override createShowSessionDraftPromotions$(
        sessionDraftId: string,
        name: string,
        promotionData: string,
    ): Observable<ShowSessionDraftPromotionModel> {
        return this.adminGateway.createShowSessionDraftPromotion(sessionDraftId, name, promotionData);
    }

    override getShowSessionsDraftPromotions$(sessionDraftId: string): Observable<ShowSessionDraftPromotionModel[]> {
        return this.adminGateway.fetchShowSessionDraftPromotions(sessionDraftId);
    }

    override getShowSessionsDraftOrderCustomFields$(
        sessionDraftId: string,
    ): Observable<ShowSessionDraftOrderCustomFieldModel> {
        return this.adminGateway.fetchShowSessionDraftOrderCustomFields(sessionDraftId);
    }

    override createShowSessionDraftOrderCustomFields$(
        sessionDraftId: String,
        customFields: unknown[],
    ): Observable<ShowSessionDraftOrderCustomFieldModel> {
        return this.adminGateway.createShowSessionDraftOrderCustomFields(sessionDraftId, customFields);
    }

    override updateShowSessionDraftOrderCustomFields$(
        id: String,
        customFields: unknown[],
    ): Observable<ShowSessionDraftOrderCustomFieldModel> {
        return this.adminGateway.updateShowSessionDraftOrderCustomFields(id, customFields);
    }

    override updateShowSessionDraftMailTemplates$(
        id: String,
        mailTemplates: unknown[],
    ): Observable<ShowSessionDraftModel> {
        return this.adminGateway.updateShowSessionDraftMailTemplates(id, mailTemplates);
    }

    override createShowSession$(id: String): Observable<ShowSessionDraftModel> {
        return this.adminGateway.createShowSession(id);
    }

    override deleteShowSessionDraft$(id: string): Observable<{ id: string; deleted: boolean }> {
        return this.adminGateway.deleteShowSessionDraft(id);
    }

    override updateShowSession$(id: String): Observable<ShowSessionDraftModel> {
        return this.adminGateway.updateShowSession(id);
    }

    override createReportFilter$(req: CreateReportFilterRequest): Observable<string> {
        return this.adminGateway.createReportFilter(req);
    }

    override loadReportFilterList$(req: LoadReportFilterListRequest): Observable<ReportFilterResponse[]> {
        return this.adminGateway.loadReportFilterList(req);
    }

    override disableReportFilter$(reportId: string): Observable<ReportFilterResponse> {
        return this.adminGateway.disableReportFilter(reportId);
    }

    override enableReportFilter$(reportId: string): Observable<ReportFilterResponse> {
        return this.adminGateway.enableReportFilter(reportId);
    }

    override deleteReportFilter$(reportId: string): Observable<string> {
        return this.adminGateway.removeReportFilter(reportId);
    }

    override loadInvoiceStatistics$(reportKey: string): Observable<InvoiceStatisticsByShow[]> {
        return this.adminGateway.loadInvoiceStatistics(reportKey);
    }

    override loadReport$(
        reportKey: string,
        loadOrderRequest: DownloadOrderReportRequest,
    ): Observable<LoadReportResponse> {
        return this.adminGateway.loadReport(reportKey, loadOrderRequest);
    }

    override loadTicketBooking(showId: string, sessionId: string): Observable<TicketBookingResponse[]> {
        return this.adminGateway.loadTicketBooking(showId, sessionId);
    }

    override bookingTicketFromFile(
        file: File,
        showId: string,
        sessionId: string,
        customerUserId: string,
    ): Observable<HttpResponse<BookingUploadSuccessResponse | BookingUploadErrorResponse>> {
        return this.adminGateway.bookingTicketFromFile(file, showId, sessionId, customerUserId);
    }
}
