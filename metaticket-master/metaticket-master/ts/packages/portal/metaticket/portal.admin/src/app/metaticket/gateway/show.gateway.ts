import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
    CityModel,
    EventCategoryModel,
    PaymentType,
    SessionCalendarConfig,
    SessionType,
    ShowModel,
    ShowSessionModel,
    UploadShowImageResponseModel,
    BannerPinBoard,
    BannerPinCandidatePage,
} from '../../@core/metaticket/model';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi, publicApi } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ShowGateway {
    constructor(private http: HttpClient) {}

    fetchAllShow(): Observable<ShowModel[]> {
        return this.http.get<ShowModel[]>(publicApi('/show/fetch/all')).pipe(take(1));
    }

    fetchShow(showId: string): Observable<ShowModel> {
        return this.http.get<ShowModel>(publicApi(`/show/fetch?id=${showId}`)).pipe(take(1));
    }

    fetchAllCity(): Observable<CityModel[]> {
        return this.http.get<CityModel[]>(publicApi('/show/city/fetch/all')).pipe(take(1));
    }

    createShow(
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
        return this.http
            .post<ShowModel>(
                privateApi('show/create'),
                {
                    showName: showName || undefined,
                    code: code || undefined,
                    bannerImage: bannerImage || undefined,
                    detailImageUrl: detailImageUrl || undefined,
                    thumbnailUrl: thumbnailUrl || undefined,
                    description: description || undefined,
                    ticketDescription: ticketDescription || undefined,
                    policy: policy || undefined,
                    faq: faq || undefined,
                    eventCategoryCode: eventCategoryCode || undefined,
                    eventCategory: eventCategory || undefined,
                    pinned: !!pinned,
                    // Always send boolean — `false || undefined` would omit the field and backend unsets it
                    hideSessions: !!hideSessions,
                    logoShowImage: logoShowImage || undefined,
                    logoHostImage: logoHostImage || undefined,
                    slug: slug || undefined,
                },
                {
                    withCredentials: true,
                    headers: {
                        'Content-Type': 'application/json',
                    },
                },
            )
            .pipe(take(1));
    }

    uploadImage(form: FormData): Observable<UploadShowImageResponseModel> {
        return this.http
            .post<UploadShowImageResponseModel>(privateApi('show/upload/image'), form, {
                withCredentials: true,
                // headers: {
                // 'Content-Type': 'multipart/form-data',
                // },
            })
            .pipe(take(1));
    }
    fetchShowSessions(showId: string): Observable<ShowSessionModel[]> {
        return this.http.get<ShowSessionModel[]>(publicApi(`/show/sessions/fetch?showId=${showId}`)).pipe(take(1));
    }

    getMarketTicketLink(showId: string, sessionId: string): Observable<string> {
        return this.http
            .post<string>(privateApi(`/show/session/link`), null, {
                params: {
                    showId: showId,
                    sessionId: sessionId,
                },
                responseType: 'text' as 'json',
                withCredentials: true,
            })
            .pipe(take(1));
    }

    getShowDetailLink(showId: string): Observable<string> {
        return this.http
            .post<string>(privateApi(`/show/detail/link`), null, {
                params: {
                    showId: showId,
                },
                responseType: 'text' as 'json',
                withCredentials: true,
            })
            .pipe(take(1));
    }

    getEventCategory(): Observable<EventCategoryModel[]> {
        return this.http.get<EventCategoryModel[]>(publicApi(`/show/event-category/fetch`)).pipe(take(1));
    }

    updateShow(
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
        return this.http
            .post<ShowModel>(
                privateApi('show/update'),
                {
                    id: id || undefined,
                    showName: showName || undefined,
                    code: code || undefined,
                    description: description || undefined,
                    ticketDescription: ticketDescription || undefined,
                    policy: policy || undefined,
                    faq: faq || undefined,
                    eventCategoryCode: eventCategoryCode || undefined,
                    eventCategory: eventCategory || undefined,
                    pinned: !!pinned,
                    // Always send boolean — `false || undefined` would omit the field and backend unsets it
                    hideSessions: !!hideSessions,
                    bannerImage: bannerImage || undefined,
                    detailImageUrl: detailImageUrl || undefined,
                    thumbnailUrl: thumbnailUrl || undefined,
                    logoShowImage: logoShowImage || undefined,
                    logoHostImage: logoHostImage || undefined,
                    slug: slug || undefined,
                },
                {
                    withCredentials: true,
                    headers: {
                        'Content-Type': 'application/json',
                    },
                },
            )
            .pipe(take(1));
    }

    updateShowSession(
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
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/update'),
                {
                    showId: showId || undefined,
                    sessionId: sessionId || undefined,
                    sessionName: sessionName || undefined,
                    code: code || undefined,
                    address: address || undefined,
                    startTime: startTime || undefined,
                    endTime: endTime || undefined,
                    requireDelivery: requireDelivery || undefined,
                    orderNote: orderNote || undefined,
                    cityId: city || undefined,
                    isFree: isFree || undefined,
                    showInHome: showInHome || undefined,
                    ledgendImage: ledgendImage || undefined,
                    eventSquareImage: eventSquareImage || undefined,
                    eventNameImage: eventNameImage || undefined,
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

    updateShowSessionPayment(
        showId: string,
        sessionId: string,
        paymentOffline?: boolean,
        paymentNote?: string,
        pgAccountCode?: string,
        paymentMethods?: PaymentType[],
    ): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/update/payment'),
                {
                    showId: showId || undefined,
                    sessionId: sessionId || undefined,
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

    updateShowSessionMailTemplate(
        showId: string,
        sessionId: string,
        mailTemplates: unknown[],
    ): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/update/mail/templates'),
                {
                    showId: showId || undefined,
                    sessionId: sessionId || undefined,
                    mailTemplates: mailTemplates || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    updateShowSessionSeatPlan(
        showId: string,
        sessionId: string,
        seatPlanId: string,
        edType: string,
        extraInfo?: string,
    ): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/update/seatplan'),
                {
                    showId: showId || undefined,
                    sessionId: sessionId || undefined,
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

    syncShowData(): Observable<string> {
        return this.http
            .get<string>(privateApi('/show/sync-metasearch'), {
                responseType: 'text' as 'json',
                withCredentials: true,
            })
            .pipe(take(1));
    }

    /** GET /private/show/pin/banner — 10 banner slots. */
    fetchBannerPins(): Observable<BannerPinBoard> {
        return this.http.get<BannerPinBoard>(privateApi('show/pin/banner'), { withCredentials: true }).pipe(take(1));
    }

    /** POST refresh — persist 10 most recently modified shows as the board. */
    refreshBannerPins(): Observable<BannerPinBoard> {
        return this.http
            .post<BannerPinBoard>(privateApi('show/pin/banner/refresh'), {}, { withCredentials: true })
            .pipe(take(1));
    }

    /** POST confirm — persist FE slot order / replacements. */
    confirmBannerPins(slots: { pinOrder: number; showId: string | null }[]): Observable<BannerPinBoard> {
        return this.http
            .post<BannerPinBoard>(privateApi('show/pin/banner/confirm'), { slots }, { withCredentials: true })
            .pipe(take(1));
    }

    /** GET paginated picker (page is 1-based). */
    searchPinCandidates(
        page: number,
        size: number,
        keyword?: string,
        eventCategoryCode?: string,
    ): Observable<BannerPinCandidatePage> {
        const params: Record<string, string> = { page: String(page), size: String(size) };
        if (keyword) params.keyword = keyword;
        if (eventCategoryCode) params.eventCategoryCode = eventCategoryCode;
        return this.http
            .get<BannerPinCandidatePage>(privateApi('show/pin/candidates'), { params, withCredentials: true })
            .pipe(take(1));
    }

    requestApproveShowSession(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/approval/request'),
                {
                    showId: showId,
                    sessionId: sessionId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    rejectShowSession(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/approval/reject'),
                {
                    showId: showId,
                    sessionId: sessionId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    approveShowSession(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/approval/approve'),
                {
                    showId: showId,
                    sessionId: sessionId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    startShowSession(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/sale/start'),
                {
                    showId: showId,
                    sessionId: sessionId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    stopShowSession(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/sale/stop'),
                {
                    showId: showId,
                    sessionId: sessionId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    pauseShowSession(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/sale/pause'),
                {
                    showId: showId,
                    sessionId: sessionId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    terminateShowSession(showId: string, sessionId: string): Observable<ShowSessionModel> {
        return this.http
            .post<ShowSessionModel>(
                privateApi('/show/session/terminate'),
                {
                    showId: showId,
                    sessionId: sessionId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }
}
