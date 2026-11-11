import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SeatplanCategoryModel, SeatplanModel } from '../../@core/metaticket/model';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi } from '../../../environments/environment';

@Injectable()
export class PolicyDescGateway {
    constructor(private http: HttpClient) {}

    fetchAllSeatPlanCategory(): Observable<SeatplanCategoryModel[]> {
        return this.http
            .get<SeatplanCategoryModel[]>(privateApi('/editor/policydesc/documents/category'), {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    fetchSeatPlanCategory(id: string): Observable<SeatplanCategoryModel> {
        return this.http
            .get<SeatplanCategoryModel>(privateApi('/editor/policydesc/document/category'), {
                params: { globalId: id },
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    loadSeatPlan(id: string): Observable<SeatplanModel> {
        return this.http
            .get<SeatplanModel>(privateApi('/editor/policydesc/document'), {
                params: { globalId: id },
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }
}
