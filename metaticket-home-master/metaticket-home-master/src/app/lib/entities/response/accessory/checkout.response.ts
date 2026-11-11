/**
 * Checkout result (design `CheckoutResult`, Req 6.15 + 7.3). Carries the
 * `Checksum`-encoded payment and order ids used to drive the payment step and
 * the order-review link, plus the created Order_Code and computed total for the
 * confirmation UI.
 */
export interface AccessoryCheckoutResponse {
  encodedPaymentId: string;
  encodedOrderId: string;
  orderCode: string;
  total: number;
}
