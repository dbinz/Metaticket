import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TicketSlotModel } from '../../@core/metaticket/model';
import { UpdateTicketSlotRequest } from './request.ticket.slots';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi } from '../../../environments/environment';

@Injectable()
export class TicketSlotGateway {
    constructor(private http: HttpClient) {}

    fetchTicketSlotsByShow(showId: string, sessionId: string): Observable<TicketSlotModel[]> {
        return this.http
            .post<TicketSlotModel[]>(
                privateApi('/ticket-slots/fetch-by-show'),
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

    openSelectedTicketSlots(request: UpdateTicketSlotRequest): Observable<any> {
        return this.http
            .post<any>(privateApi('/ticket-slots/open-selected-slots'), request, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    closeSelectedTicketSlots(request: UpdateTicketSlotRequest): Observable<any> {
        return this.http
            .post<any>(privateApi('/ticket-slots/close-selected-slots'), request, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }
}
