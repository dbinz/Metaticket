import { Store, StoreConfig } from '@datorama/akita';
import { Injectable } from '@angular/core';

/**
 * Flow state for the accessory multi-step checkout container
 * (Chọn → Điền thông tin → Thông tin nhận hàng → Thanh toán → Hoàn tất).
 * The cart lines themselves live in the client cart store (`AccessoryCartStore`);
 * this store tracks the step index, previewed discount, customer info, draft
 * fulfillment choice, and encoded ids returned by checkout.
 */
export interface AccessoryCheckoutState {
  currentIndex: number;
  // customer info — collected at the information step
  customerName: string;
  customerPhone: string;
  customerEmail: string;
  // fulfillment draft — collected at "Thông tin nhận hàng"
  fulfillmentType: '' | 'PickupAtShow' | 'MetaTicketPickup' | 'Delivery';
  showSessionId: string;
  /** Selected META Ticket pickup-point id when fulfillmentType is MetaTicketPickup. */
  pickupPointId: string;
  /** Delivery draft fields when fulfillmentType is Delivery (post-merger: no district). */
  deliveryCity: string;
  deliveryWard: string;
  deliveryAddress: string;
  deliveryNote: string;
  // discount preview (Req 4.1)
  discountCode: string;
  discountAmount: number;
  discountType: string; // '' | 'Percentage' | 'FixedAmount' | 'Tiered' | 'EarlyBird' | 'None'
  discountValue: number; // percent (Percentage) or fixed VND (FixedAmount)
  subtotal: number;
  total: number;
  // checkout result (Req 6.15, 7.3)
  encodedOrderId: string;
  encodedPaymentId: string;
  orderCode: string;
  // The logged-in customer who created the order (JWT `userId`). Used to scope the resume/lock so an
  // order is only ever resumed for its owner — a logged-out user or a different account is not
  // dragged into (or trapped in) someone else's pending checkout. Empty when no order exists.
  ownerUserId: string;
  // payment step bookkeeping
  isStartedPayment: boolean;
  // final payment outcome reported on the "Hoàn tất" step: '' (pending) | 'Succeed' | 'Failed'
  paymentResult: string;
  // Gateway URL in use — kept so the customer can reopen the payment page after closing its tab.
  paymentRedirectUrl: string;
  // Absolute deadline (epoch millis) by which the order must be paid before the backend TTL sweep
  // expires it. Set at checkout (now + orderTtlMinutes) and persisted so a reload keeps the correct
  // remaining time. 0 when there is no active order. Display-only — drives the checkout countdown.
  expiresAt: number;
}

/** Creates a blank checkout flow state (index 0 = Điền thông tin). */
export function createInitialCheckoutState(): AccessoryCheckoutState {
  return {
    currentIndex: 0,
    customerName: '',
    customerPhone: '',
    customerEmail: '',
    fulfillmentType: '',
    showSessionId: '',
    pickupPointId: '',
    deliveryCity: '',
    deliveryWard: '',
    deliveryAddress: '',
    deliveryNote: '',
    discountCode: '',
    discountAmount: 0,
    discountType: '',
    discountValue: 0,
    subtotal: 0,
    total: 0,
    encodedOrderId: '',
    encodedPaymentId: '',
    orderCode: '',
    ownerUserId: '',
    isStartedPayment: false,
    paymentResult: '',
    paymentRedirectUrl: '',
    expiresAt: 0,
  };
}

@Injectable({ providedIn: 'root' })
@StoreConfig({ name: 'accessory-checkout' })
export class AccessoryCheckoutStore extends Store<AccessoryCheckoutState> {
  constructor() {
    super(createInitialCheckoutState());
  }
}
