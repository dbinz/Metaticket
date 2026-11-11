import { PaymentType } from 'src/app/lib/enums';

export interface StartPaymentRequest {
  paymentId: string;
  paymentType: PaymentType;
}
