import { Injectable } from '@angular/core';
import { ShowService } from '../../@core/metaticket/data.service';
import { Observable } from 'rxjs';
import {
    CityModel,
    EventCategoryModel,
    PaymentType,
    SessionCalendarConfig,
    SessionType,
    ShowModel,
    ShowSessionModel,
    UploadShowImageResponseModel,
} from '../';
import { ShowGateway } from '../gateway/show.gateway';

@Injectable()
export class MetaticketShowService extends ShowService {
    constructor(private showGateway: ShowGateway) {
        super();
    }

    override getShows$(): Observable<ShowModel[]> {
        return this.showGateway.fetchAllShow();
    }
    override getShow$(id: string): Observable<ShowModel> {
        return this.showGateway.fetchShow(id);
    }
    createShows$(
        showName: string,
        code: string,
        bannerImage: string,
        detailImageUrl: string,
        thumbnailUrl: string,
        description: string,
        ticketDescription: string,
        policy: string,
        faq: string,
        eventCategoryCode: string,
        eventCategory: string,
        pinned: boolean,
        hideSessions: boolean,
        logoShowImage?: string,
        logoHostImage?: string,
        slug?: string,
    ): Observable<ShowModel> {
        return this.showGateway.createShow(
            showName,
            code,
            bannerImage,
            detailImageUrl,
            thumbnailUrl,
            description,
            ticketDescription,
            policy,
            faq,
            eventCategoryCode,
            eventCategory,
            pinned,
            hideSessions,
            logoShowImage,
            logoHostImage,
            slug,
        );
    }
    updateShows$(
        id: string,
        showName: string,
        code: string,
        description: string,
        ticketDescription: string,
        policy: string,
        faq: string,
        eventCategoryCode: string,
        eventCategory: string,
        pinned: boolean,
        hideSessions: boolean,
        bannerImage?: string,
        detailImageUrl?: string,
        thumbnailUrl?: string,
        logoShowImage?: string,
        logoHostImage?: string,
        slug?: string,
    ): Observable<ShowModel> {
        return this.showGateway.updateShow(
            id,
            showName,
            code,
            description,
            ticketDescription,
            policy,
            faq,
            eventCategoryCode,
            eventCategory,
            pinned,
            hideSessions,
            bannerImage,
            detailImageUrl,
            thumbnailUrl,
            logoShowImage,
            logoHostImage,
            slug,
        );
    }

    uploadImage$(formData: FormData): Observable<UploadShowImageResponseModel> {
        return this.showGateway.uploadImage(formData);
    }
    getShowSessions$(id: string): Observable<ShowSessionModel[]> {
        return this.showGateway.fetchShowSessions(id);
    }

    getMarketTicketLink$(showId: string, sessionId: string): Observable<string> {
        return this.showGateway.getMarketTicketLink(showId, sessionId);
    }

    getShowDetailLink$(showId: string): Observable<string> {
        return this.showGateway.getShowDetailLink(showId);
    }

    getEventCategory$(): Observable<EventCategoryModel[]> {
        return this.showGateway.getEventCategory();
    }

    requestApproveShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.showGateway.requestApproveShowSession(showId, sessionId);
    }

    rejectShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.showGateway.rejectShowSession(showId, sessionId);
    }

    approveShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.showGateway.approveShowSession(showId, sessionId);
    }

    startShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.showGateway.startShowSession(showId, sessionId);
    }

    pauseShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.showGateway.pauseShowSession(showId, sessionId);
    }

    terminateShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.showGateway.terminateShowSession(showId, sessionId);
    }

    stopShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.showGateway.stopShowSession(showId, sessionId);
    }

    updateShowSession$(
        showId: string,
        sessionId: string,
        sessionName: string,
        code: string,
        address: string,
        startTime: number,
        endTime: number,
        requireDelivery: boolean,
        orderNote: string,
        city: string,
        isFree: boolean,
        showInHome: boolean,
        ledgendImage?: string,
        eventSquareImage?: string,
        eventNameImage?: string,
        manageTicket?: boolean,
        qrcodeEmail?: boolean,
        slug?: string,
        sessionType?: SessionType,
        calendar?: SessionCalendarConfig | null,
    ): Observable<ShowSessionModel> {
        return this.showGateway.updateShowSession(
            showId,
            sessionId,
            sessionName,
            code,
            address,
            startTime,
            endTime,
            requireDelivery,
            orderNote,
            city,
            isFree,
            showInHome,
            ledgendImage,
            eventSquareImage,
            eventNameImage,
            manageTicket,
            qrcodeEmail,
            slug,
            sessionType,
            calendar,
        );
    }

    updateShowSessionPayment$(
        showId: string,
        sessionId: string,
        paymentOffline: boolean,
        paymentNote?: string,
        pgAccountCode?: string,
        paymentMethods?: PaymentType[],
    ): Observable<ShowSessionModel> {
        return this.showGateway.updateShowSessionPayment(
            showId,
            sessionId,
            paymentOffline,
            paymentNote,
            pgAccountCode,
            paymentMethods,
        );
    }

    updateShowSessionMailTemplates$(
        showId: string,
        sessionId: string,
        mailTemplates: unknown[],
    ): Observable<ShowSessionModel> {
        return this.showGateway.updateShowSessionMailTemplate(showId, sessionId, mailTemplates);
    }
    updateShowSessionSeatPlan$(
        showId: string,
        sessionId: string,
        seatPlanId: string,
        edType: string,
        extraInfo?: string,
    ): Observable<ShowSessionModel> {
        return this.showGateway.updateShowSessionSeatPlan(showId, sessionId, seatPlanId, edType, extraInfo);
    }

    syncShowData$(): Observable<any> {
        return this.showGateway.syncShowData();
    }

    getCities(): Observable<CityModel[]> {
        return this.showGateway.fetchAllCity();
    }
}
