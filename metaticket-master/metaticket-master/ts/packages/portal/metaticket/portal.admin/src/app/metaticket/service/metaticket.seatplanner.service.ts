import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SeatplanCategoryModel, SeatplanModel } from '../../@core/metaticket/model';
import { SeatplanService } from '../../@core/metaticket/data.service';
import { SeatplannerGateway } from '../gateway/seatplanner.gateway';

@Injectable()
export class MetaticketSeatplannerService extends SeatplanService {
    constructor(private seatplannerGateway: SeatplannerGateway) {
        super();
    }

    getAllSeatplanCategory(): Observable<SeatplanCategoryModel[]> {
        return this.seatplannerGateway.fetchAllSeatPlanCategory();
    }

    getSeatplanCategory(id: string): Observable<SeatplanCategoryModel> {
        return this.seatplannerGateway.fetchSeatPlanCategory(id);
    }

    getSeatPlan(id: string): Observable<SeatplanModel> {
        return this.seatplannerGateway.loadSeatPlan(id);
    }

    createSeatPlan(): Observable<SeatplanModel> {
        return this.seatplannerGateway.createSeatPlan();
    }

    deleteSeatPlan(id: string): Observable<any> {
        return this.seatplannerGateway.deleteSeatPlan(id);
    }
}
