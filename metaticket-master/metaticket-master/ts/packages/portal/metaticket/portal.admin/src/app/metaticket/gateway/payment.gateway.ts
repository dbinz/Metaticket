import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
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
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi } from '../../../environments/environment';

@Injectable()
export class PaymentGateway {
    constructor(private http: HttpClient) {}

    fetchPayments$(paymentIds: string[]): Observable<PaymentResponse[]> {
        return this.http
            .post<PaymentResponse[]>(privateApi('/payment/ids/fetch'), paymentIds, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    fetchPGConfig$(): Observable<PGConfigModel> {
        return this.http
            .get<PGConfigModel>(privateApi('/payment/pg-config/fetch'), {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    loadPGAccount$(code: string): Observable<PGAccountModel> {
        return this.http
            .get<PGAccountModel>(privateApi('/payment/pg-account/load'), {
                params: {
                    code: code,
                },
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    removePGAccount$(code: string): Observable<any> {
        return this.http
            .post<PGConfigModel>(
                privateApi('/payment/pg-account/remove'),
                {
                    code: code,
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

    enablePGAccount$(code: string): Observable<any> {
        return this.http
            .post<PGConfigModel>(
                privateApi('/payment/pg-account/enable'),
                {
                    code: code,
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

    disablePGAccount$(code: string): Observable<any> {
        return this.http
            .post<PGConfigModel>(
                privateApi('/payment/pg-account/disable'),
                {
                    code: code,
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

    setDefaultPGAccount$(code: string): Observable<any> {
        return this.http
            .post<PGConfigModel>(
                privateApi('/payment/pg-account/set-default'),
                {
                    code: code,
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

    addOnePayAccount$(request: AddOnePayAccountRequest): Observable<OnePayAccountModel> {
        return this.http
            .post<OnePayAccountModel>(privateApi('/payment/pg-account/onepay/add'), request, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    updateOnePayAccount$(request: UpdateOnePayAccountRequest): Observable<OnePayAccountModel> {
        return this.http
            .post<OnePayAccountModel>(privateApi('/payment/pg-account/onepay/update'), request, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    addNinePayAccount$(request: AddNinePayAccountRequest): Observable<NinePayAccountModel> {
        return this.http
            .post<NinePayAccountModel>(privateApi('/payment/pg-account/ninepay/add'), request, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    updateNinePayAccount$(request: UpdateNinePayAccountRequest): Observable<NinePayAccountModel> {
        return this.http
            .post<NinePayAccountModel>(privateApi('/payment/pg-account/ninepay/update'), request, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    addVNPayAccount$(request: AddVNPayAccountRequest): Observable<VNPayAccountModel> {
        return this.http
            .post<VNPayAccountModel>(privateApi('/payment/pg-account/vnpay/add'), request, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    updateVNPayAccount$(request: UpdateVNPayAccountRequest): Observable<VNPayAccountModel> {
        return this.http
            .post<VNPayAccountModel>(privateApi('/payment/pg-account/vnpay/update'), request, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }
}
