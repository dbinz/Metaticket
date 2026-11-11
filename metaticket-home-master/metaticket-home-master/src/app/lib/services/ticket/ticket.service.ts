import { Injectable } from '@angular/core';
import { HttpClientService } from 'src/app/lib/services/http-client/http-client.service';
import {
  CalendarDayResponse,
  CreateReservationRequest,
  CreateReservationResponse,
  GetAllTicketRequest,
  GetSingleReservationResponse,
  GetTicketByTimeRequest,
  TicketsWithVersionResponse,
} from 'src/app/lib/entities';
import { Observable, take } from 'rxjs';
import { apiUrl } from 'src/app/lib/constants';
import { HttpParams } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class TicketService {
  constructor(private _http: HttpClientService) {}

  getAllTicket(body: GetAllTicketRequest): Observable<TicketsWithVersionResponse> {
    let params = new HttpParams().set('showId', body.showId).set('sessionId', body.sessionId);
    if (body.date) {
      params = params.set('date', body.date);
    }

    return this._http.getJson<TicketsWithVersionResponse>(apiUrl.getAllTicket, params).pipe(take(1));
  }

  getTicketByTime(body: GetTicketByTimeRequest): Observable<TicketsWithVersionResponse> {
    let params = new HttpParams()
      .set('showId', body.showId)
      .set('sessionId', body.sessionId)
      .set('startTime', body.startTime);
    if (body.date) {
      params = params.set('date', body.date);
    }

    return this._http.getJson<TicketsWithVersionResponse>(apiUrl.getTicketByTime, params).pipe(take(1));
  }

  /** Per-date sale state + lowest price of a CALENDAR session for one month ("YYYY-MM"). 403 when not on sale. */
  getCalendarMonth(showId: string, sessionId: string, month: string): Observable<CalendarDayResponse[]> {
    const params = new HttpParams().set('showId', showId).set('sessionId', sessionId).set('month', month);

    return this._http.getJson<CalendarDayResponse[]>(apiUrl.getCalendarMonth, params).pipe(take(1));
  }

  createReservation(body: CreateReservationRequest): Observable<CreateReservationResponse> {
    return this._http.postJson<CreateReservationResponse>(apiUrl.createReservation, body);
  }

  reserveReservation(reservationId: string): Observable<any> {
    const params = new HttpParams().set('id', reservationId);
    return this._http.getJson(apiUrl.reserveReservation, params);
  }

  getSingleReservation(reservationId: string): Observable<GetSingleReservationResponse> {
    const params = new HttpParams().set('id', reservationId);
    return this._http.getJson<GetSingleReservationResponse>(apiUrl.getSingleReservation, params);
  }

  releaseReservation(reservationId: string): Observable<any> {
    const params = new HttpParams().set('id', reservationId);
    return this._http.getJson(apiUrl.releaseReservation, params);
  }
}
