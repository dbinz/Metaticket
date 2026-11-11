import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
    BulkUpdateCalendarResponse,
    CalendarAdminDay,
    CategoryItemModel,
    CategoryMappingModel,
    CountTicketByShowModel,
    DeleteCalendarResponse,
    GenerateCalendarResponse,
    TicketModel,
    TicketReservationModel,
    TicketReservationStatus,
} from '../../@core/metaticket/model';
import {
    BulkUpdateCalendarRequest,
    CreateTicketDocRequest,
    DeleteCalendarRequest,
    GenerateCalendarRequest,
    UpdateTicketDocRequest,
    UpdateTicketSlotRequest,
} from './request.ticket';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi } from '../../../environments/environment';

@Injectable()
export class TicketGateway {
    constructor(private http: HttpClient) {}

    countTicketByShow(showId: string): Observable<CountTicketByShowModel[]> {
        return this.http
            .get<CountTicketByShowModel[]>(privateApi('/ticket/doc/count/by-show'), {
                params: { showId: showId },
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    fetchTicketById(tkId: string): Observable<TicketModel> {
        return this.http
            .get<TicketModel>(privateApi('/ticket/doc/fetch'), {
                params: { id: tkId },
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    fetchAllTicket(showId: string, sessionId: string): Observable<TicketModel[]> {
        return this.http
            .post<TicketModel[]>(
                privateApi('/ticket/doc/fetch-all'),
                {
                    showId: showId,
                    sessionId: sessionId,
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

    fetchReservationById(reservationId: string): Observable<TicketReservationModel> {
        return this.http
            .get<TicketReservationModel>(privateApi('/ticket/reservation/fetch'), {
                params: { id: reservationId },
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    fetchReservationByIds(reservationIds: string[]): Observable<TicketReservationModel[]> {
        return this.http
            .post<TicketReservationModel[]>(privateApi('/ticket/reservation/fetch'), reservationIds, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    fetchReservationByTicket(
        tkId: string,
        reservationStatus: TicketReservationStatus[],
    ): Observable<TicketReservationModel[]> {
        return this.http
            .post<TicketReservationModel[]>(
                privateApi('/ticket/reservation/fetch-by-ticket'),
                {
                    tkId: tkId,
                    reservationStatus: reservationStatus,
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

    forceReserveTicket(showId: string, sessionId: string, details: any): Observable<any> {
        return this.http
            .post(
                privateApi('/ticket/reservation/lock-ticket'),
                {
                    showId: showId,
                    sessionId: sessionId,
                    details: details,
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

    createTicket(req: CreateTicketDocRequest): Observable<TicketModel[]> {
        return this.http
            .post<TicketModel[]>(privateApi('/ticket/doc/create'), req, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    updateTicket(req: UpdateTicketDocRequest) {
        return this.http
            .post(privateApi('/ticket/doc/update'), req, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    updateTicketSlot(req: UpdateTicketSlotRequest) {
        return this.http
            .post(privateApi('/ticket/doc/update-slot'), req, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    unlockReservation(reservationId: string): Observable<TicketReservationModel> {
        return this.http
            .get<TicketReservationModel>(privateApi('/ticket/reservation/unlock'), {
                params: { id: reservationId },
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    createCategoryMapping$(
        showId: string,
        sessionId: string,
        kindMap: string,
        mapId: string,
        category: CategoryItemModel[],
    ): Observable<CategoryMappingModel> {
        return this.http
            .post<CategoryMappingModel>(
                privateApi('/ticket/category/mapping/create'),
                {
                    showId: showId,
                    sessionId: sessionId,
                    kindMap: kindMap,
                    mapId: mapId,
                    category: category,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    addOrUpdateCategory$(
        showId: string,
        sessionId: string,
        oldCat: string,
        category: CategoryItemModel,
    ): Observable<{ updatedCat: string; noTicketUpdated: number }> {
        return this.http
            .post<{ updatedCat: string; noTicketUpdated: number }>(
                privateApi('/ticket/category/mapping/add-update'),
                {
                    showId: showId,
                    sessionId: sessionId,
                    oldCat: oldCat,
                    category: category,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    deleteCategory$(
        showId: string,
        sessionId: string,
        cat: string,
    ): Observable<{ deletedCat: string; noTicketDeleted: number }> {
        return this.http
            .post<{ deletedCat: string; noTicketDeleted: number }>(
                privateApi('/ticket/category/mapping/delete'),
                {
                    showId: showId,
                    sessionId: sessionId,
                    category: cat,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    deleteTicketOfCategory$(showId: string, sessionId: string, cat: string): Observable<number> {
        return this.http
            .post<number>(
                privateApi('/ticket/doc/delete-by-cat'),
                {
                    showId: showId,
                    sessionId: sessionId,
                    category: cat,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    loadCategoryMappingByShowAndSession$(
        showId: string,
        sessionId: string,
    ): Observable<CategoryMappingModel | undefined> {
        return this.http
            .get<CategoryMappingModel | undefined>(privateApi(`/ticket/category/mapping/load-by-show-session`), {
                params: {
                    showId: showId,
                    sessionId: sessionId,
                },
            })
            .pipe(take(1));
    }

    /** Các ngày đã có lịch trong tháng [month] ('YYYY-MM') của session vé theo lịch. */
    loadCalendarMonth$(showId: string, sessionId: string, month: string): Observable<CalendarAdminDay[]> {
        return this.http
            .get<CalendarAdminDay[]>(privateApi('/ticket/calendar/month'), {
                params: { showId: showId, sessionId: sessionId, month: month },
                withCredentials: true,
            })
            .pipe(take(1));
    }

    generateCalendar$(req: GenerateCalendarRequest): Observable<GenerateCalendarResponse> {
        return this.http
            .post<GenerateCalendarResponse>(privateApi('/ticket/calendar/generate'), req, { withCredentials: true })
            .pipe(take(1));
    }

    bulkUpdateCalendar$(req: BulkUpdateCalendarRequest): Observable<BulkUpdateCalendarResponse> {
        return this.http
            .post<BulkUpdateCalendarResponse>(privateApi('/ticket/calendar/bulk-update'), req, {
                withCredentials: true,
            })
            .pipe(take(1));
    }

    deleteCalendar$(req: DeleteCalendarRequest): Observable<DeleteCalendarResponse> {
        return this.http
            .post<DeleteCalendarResponse>(privateApi('/ticket/calendar/delete'), req, { withCredentials: true })
            .pipe(take(1));
    }
}
