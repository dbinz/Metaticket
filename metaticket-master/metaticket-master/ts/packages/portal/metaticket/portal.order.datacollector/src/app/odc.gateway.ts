import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, take } from 'rxjs';
import {
    OrderDataCollectedResponse,
    OrderDataCollectedStatus,
    OrderDataCollectorTemplateResponse,
    SaveOrderDataCollectedRequest,
} from './app.model';
import { api } from '../environments/environment';

@Injectable()
export class OdcGateway {
    constructor(private http: HttpClient) {}

    replacer(key: any, value: any) {
        if (value instanceof Map) {
            return Object.fromEntries(value); // or with spread: value: [...value]
        } else {
            return value;
        }
    }

    loadCollectorTemplate(id: string): Observable<OrderDataCollectorTemplateResponse> {
        return this.http
            .get<OrderDataCollectorTemplateResponse>(api(`/odc/template/load/${id}`), {
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    loadDataCollected(id: string): Observable<OrderDataCollectedResponse> {
        return this.http
            .get<OrderDataCollectedResponse>(api(`/odc/data/load/${id}`), {
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    saveDataCollected(request: SaveOrderDataCollectedRequest): Observable<OrderDataCollectedStatus> {
        return this.http
            .post<OrderDataCollectedStatus>(api('/odc/data/save'), JSON.stringify(request, this.replacer), {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }
}
