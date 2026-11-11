import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
    DisableEnableQRCodeResponse,
    FetchOrderQRCodeResponse,
    QRCodeTicketInfoModel,
    ScanQRCodeRequest,
} from '../../qrcode/model';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi, publicApi } from '../../../environments/environment';

@Injectable()
export class QRCodeGateway {
    constructor(private http: HttpClient) {}

    getQRCodeScannerLink(sessionId: string): Observable<string> {
        return this.http
            .get<string>(privateApi(`/qrcode/generate-link-scanner`), {
                params: { sessionId: sessionId },
                responseType: 'text' as 'json',
            })
            .pipe(take(1));
    }

    fetchOrderQRCode(showId: string, sessionId?: string, orderIds?: string[]): Observable<FetchOrderQRCodeResponse> {
        return this.http
            .post<FetchOrderQRCodeResponse>(
                privateApi('/qrcode/order/fetch'),
                {
                    showId: showId,
                    sessionId: sessionId,
                    orderIds: orderIds || undefined,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    disableOrderQRCode(orderId: string): Observable<DisableEnableQRCodeResponse> {
        return this.http
            .post<DisableEnableQRCodeResponse>(
                privateApi('/qrcode/order/disable'),
                {
                    orderId: orderId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    enableOrderQRCode(orderId: string): Observable<DisableEnableQRCodeResponse> {
        return this.http
            .post<DisableEnableQRCodeResponse>(
                privateApi('/qrcode/order/enable'),
                {
                    orderId: orderId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    verifyPermission(session: string, privateCode: string): Observable<any> {
        return this.http
            .get<any>(publicApi(`/qrcode/verify-scanner`), {
                params: { session: session, privateCode: privateCode },
            })
            .pipe(take(1));
    }

    scanQRCode(scanRequest: ScanQRCodeRequest): Observable<QRCodeTicketInfoModel> {
        return this.http
            .post<QRCodeTicketInfoModel>(publicApi('/qrcode/scan'), scanRequest, {
                withCredentials: true,
            })
            .pipe(take(1));
    }
}
