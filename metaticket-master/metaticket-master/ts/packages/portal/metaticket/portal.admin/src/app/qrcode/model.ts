export interface ScanQRCodeRequest {
    session: string;
    privateCode: string;
    verifyCode: string;
}

export interface QRCodeTicketInfoModel {
    orderCode: string;
    cat: string;
    sid: string;
}

export interface FetchOrderQRCodeResponse {
    disabled: string[];
    enabled: string[];
}

export interface DisableEnableQRCodeResponse {
    orderId: string;
    countQRCode: number;
}

export interface ScanQRCodeResponse {
    status: number;
    message?: string;
    ticketInfo: QRCodeTicketInfoModel;
}
