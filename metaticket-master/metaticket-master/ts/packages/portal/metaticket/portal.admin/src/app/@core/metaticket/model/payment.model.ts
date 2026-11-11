export declare type PaymentType = 'DomesticCard' | 'InternationalCard' | 'QrCode' | 'VietQr';
export declare type PaymentStatus = 'Created' | 'Processing' | 'Succeed' | 'Failed' | 'Refunded' | 'Cancelled';
export type PaymentGatewayType = 'NinePay' | 'VNPay' | 'OnePay';

export const PAYMENT_TYPE_LABELS = new Map<PaymentType, string>([
    ['DomesticCard', 'Thẻ ATM nội địa'],
    ['InternationalCard', 'Thẻ quốc tế (Visa/Master Card/...)'],
    ['QrCode', 'QR | Chuyển khoản | Ví điện tử'],
    ['VietQr', 'Thanh toán với VietQR']
]);

export interface PaymentResponse {
    id: string;
    paymentNo?: string;
    payTime?: number;
    paymentType?: PaymentType;
    gateway?: PaymentGatewayType;
    pgAccountCode?: string;
    pgAccountName?: string;
    status: PaymentStatus;
}
