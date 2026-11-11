import { PaymentStatus } from 'src/app/lib/enums';

export interface PaymentStatusResponse {
  status: PaymentStatus;
}

export interface PaymentRedirectResponse {
  /**
   * 0 -> thành công
   * 1 -> thất bại
   * 3 -> không làm gì cả, để popup tự đóng
   */
  code: number;
  message?: string;
}
