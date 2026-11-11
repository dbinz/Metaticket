import { Injectable } from '@angular/core';
import { PolicyDescGateway } from '../gateway/policydesc.gateway';
import { Observable } from 'rxjs';
import { SeatplanCategoryModel, SeatplanModel } from '../../@core/metaticket/model';
import { SeatplanService } from '../../@core/metaticket/data.service';

@Injectable()
export class MetaticketPolicyDescService extends SeatplanService {
    constructor(private policyDescGateway: PolicyDescGateway) {
        super();
    }

    getAllSeatplanCategory(): Observable<SeatplanCategoryModel[]> {
        return this.policyDescGateway.fetchAllSeatPlanCategory();
    }

    getSeatplanCategory(id: string): Observable<SeatplanCategoryModel> {
        return this.policyDescGateway.fetchSeatPlanCategory(id);
    }

    getSeatPlan(id: string): Observable<SeatplanModel> {
        return this.policyDescGateway.loadSeatPlan(id);
    }
}
