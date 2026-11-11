import { Injectable } from '@angular/core';
import { PaymentService } from '../../@core/metaticket/data.service';
import {
    AddNinePayAccountRequest,
    AddOnePayAccountRequest,
    AddVNPayAccountRequest,
    NinePayAccountModel,
    OnePayAccountModel,
    PaymentResponse,
    PGAccountModel,
    PGConfigModel,
    UpdateNinePayAccountRequest,
    UpdateOnePayAccountRequest,
    UpdateVNPayAccountRequest,
    VNPayAccountModel,
} from '../../@core/metaticket/model';
import { PaymentGateway } from '../gateway/payment.gateway';
import { Observable } from 'rxjs';

@Injectable()
export class MetaticketPaymentService extends PaymentService {
    constructor(private paymentGateway: PaymentGateway) {
        super();
    }

    override getPayments(paymentIds: string[]): Observable<PaymentResponse[]> {
        return this.paymentGateway.fetchPayments$(paymentIds);
    }

    addOnePayAccount(request: AddOnePayAccountRequest): Observable<OnePayAccountModel> {
        return this.paymentGateway.addOnePayAccount$(request);
    }

    addNinePayAccount(request: AddNinePayAccountRequest): Observable<NinePayAccountModel> {
        return this.paymentGateway.addNinePayAccount$(request);
    }

    addVNPayAccount(request: AddVNPayAccountRequest): Observable<VNPayAccountModel> {
        return this.paymentGateway.addVNPayAccount$(request);
    }

    getPGAccount(code: string): Observable<PGAccountModel> {
        return this.paymentGateway.loadPGAccount$(code);
    }

    deletePGAccount(code: string): Observable<any> {
        return this.paymentGateway.removePGAccount$(code);
    }

    getPGConfig(): Observable<PGConfigModel> {
        return this.paymentGateway.fetchPGConfig$();
    }

    updateOnePayAccount(request: UpdateOnePayAccountRequest): Observable<OnePayAccountModel> {
        return this.paymentGateway.updateOnePayAccount$(request);
    }

    updateNinePayAccount(request: UpdateNinePayAccountRequest): Observable<NinePayAccountModel> {
        return this.paymentGateway.updateNinePayAccount$(request);
    }

    updateVNPayAccount(request: UpdateVNPayAccountRequest): Observable<VNPayAccountModel> {
        return this.paymentGateway.updateVNPayAccount$(request);
    }

    disablePGAccount(code: string): Observable<any> {
        return this.paymentGateway.disablePGAccount$(code);
    }

    enablePGAccount(code: string): Observable<any> {
        return this.paymentGateway.enablePGAccount$(code);
    }

    setDefaultPGAccount(code: string): Observable<any> {
        return this.paymentGateway.setDefaultPGAccount$(code);
    }
}
