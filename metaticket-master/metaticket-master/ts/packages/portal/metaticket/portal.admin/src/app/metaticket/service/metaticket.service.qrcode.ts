import { Injectable } from '@angular/core';
import { QRCodeGateway } from '../gateway/qrcode.gateway';
import { QRCodeTicketInfoModel, ScanQRCodeRequest } from '../../qrcode/model';
import { Observable } from 'rxjs';

@Injectable()
export class QRCodeService {
    constructor(private qrcodeGateway: QRCodeGateway) {}

    getQRCodeScannerLink(sessionId: string): Observable<string> {
        return this.qrcodeGateway.getQRCodeScannerLink(sessionId);
    }

    verifyPermission(session: string, privateCode: string): Observable<any> {
        return this.qrcodeGateway.verifyPermission(session, privateCode);
    }

    scanCode(session: string, privateCode: string, verifyCode: string): Observable<QRCodeTicketInfoModel> {
        const req: ScanQRCodeRequest = {
            session: session,
            privateCode: privateCode,
            verifyCode: verifyCode,
        };
        return this.qrcodeGateway.scanQRCode(req);
    }
}
