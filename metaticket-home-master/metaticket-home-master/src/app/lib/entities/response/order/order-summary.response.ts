export interface OrderSummaryResponse {
  orderCode: string;
  paymentInfo: PaymentInfoResponse;
  orderDetail: OrderDetailResponse;
  buyerInfo: BuyerInfoResponse;
  recipientInfo?: RecipientInfoResponse;
}

interface OrderDetailResponse {
  prices: OrderTicketResponse[];
  promotions: PromotionResponse[];
  total: number;
}

interface PromotionResponse {
  name: string;
  price: number;
}

export interface OrderTicketResponse {
  name: string;
  price: number;
  quantity: number;
}

interface PaymentInfoResponse {
  payTime: string;
  payMethod: string;
  payStatus: string;
}

interface BuyerInfoResponse {
  name: string;
  phone: string;
  email: string;
}

interface RecipientInfoResponse {
  name: string;
  phone: string;
  address: string;
}
