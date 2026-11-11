import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { url } from '../environments/environment';
import { Observable, take } from 'rxjs';

@Injectable()
export class TicketGateway {
    constructor(private http: HttpClient) {}

    sendTicket(request: any): Observable<any> {
        return this.http
            .post(url('/doc/create'), JSON.stringify(request), {
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }
}
