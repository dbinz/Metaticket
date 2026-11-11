/** Order lifecycle status mirror of the backend `AccessoryOrderStatus` enum. */
export type AccessoryOrderStatus =
  | 'Created'
  | 'WaitingForPayment'
  | 'PaymentProcessing'
  | 'PaymentSucceed'
  | 'PaymentFailed'
  | 'PaymentCancelled'
  | 'Expired';

/**
 * One line of a retrieved order (Req 13.3, 13.5). `display` combines the product
 * name, the variant label (omitted for single-SKU lines), and the quantity —
 * e.g. "Áo thun · Size M ×2".
 */
export interface AccessoryOrderLineResponse {
  display: string;
  name: string;
  variantLabel?: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/**
 * Retrieved accessory order (`GET public/accessory/order/fetch?id=`), powering
 * the "Hoàn tất" review link (Req 13.3).
 */
export interface AccessoryOrderDeliveryAddressResponse {
  city: string;
  ward: string;
  address: string;
  note?: string;
}

/** Discount applied on the order — mirrors BE `AccessoryOrderDiscountView`. */
export interface AccessoryOrderDiscountResponse {
  code: string;
  amount: number;
}

export interface AccessoryOrderResponse {
  id?: string;
  code: string;
  status: AccessoryOrderStatus;
  showName?: string;
  fulfillment: string; // PickupAtShow | MetaTicketPickup | Delivery
  /** Mode-specific receive instructions (Pickup / META / Delivery). */
  fulfillmentSummary?: string;
  showSessionId?: string;
  showSessionLabel?: string;
  pickupPointId?: string;
  pickupPointName?: string;
  pickupPointAddress?: string;
  deliveryAddress?: AccessoryOrderDeliveryAddressResponse;
  lines: AccessoryOrderLineResponse[];
  subtotal: number;
  discount?: AccessoryOrderDiscountResponse;
  total: number;
  currency?: string;
  /** Buyer user id (`X-Customer-User-Id` at checkout). */
  customerUserId?: string | null;
  customerName: string;
  customerPhone: string;
  customerEmail: string;
}

/**
 * Paid accessory order for the customer "Quản lý đơn hàng" page
 * (`GET accessory/order/fetch/mine`).
 */
export interface CustomerAccessoryOrderItem extends AccessoryOrderResponse {
  id: string;
  showName: string;
  bannerImageUrl?: string | null;
  createdAt: number;
  customerUserId?: string | null;
}

/**
 * Newest unpaid accessory hold (`GET accessory/order/pending`).
 * `expiresAt` is the server TTL deadline (epoch millis).
 */
export interface PendingAccessoryOrder {
  orderId: string;
  paymentId?: string | null;
  orderCode: string;
  status: AccessoryOrderStatus;
  showId: string;
  showName: string;
  total: number;
  expiresAt: number;
  customerName: string;
  customerPhone: string;
  customerEmail: string;
}

/** Envelope for pending lookup — `order` is null when nothing to resume. */
export interface PendingAccessoryOrderLookup {
  order?: PendingAccessoryOrder | null;
}
