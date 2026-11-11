/**
 * Checkout request bodies for the accessory storefront ("Sản phẩm"), served by
 * `service.accessory` via the nginx `public/accessory/*` path prefix.
 *
 * These mirror the backend `CheckoutInput` / `CheckoutLine` contracts from the
 * accessory-sales design. The full frontend cart (`lines`) is submitted once at
 * checkout; the backend re-loads each accessory and re-derives price/labels —
 * the client-supplied snapshots are never trusted.
 */

/** A single submitted cart line (design `CheckoutLine`). */
export interface AccessoryCheckoutLineRequest {
  accessoryId: string; // encoded/opaque catalog id
  variantKey?: string; // required iff the accessory has variants; omitted for single-SKU
  quantity: number; // integer, 1..99
}

/** Post-merger delivery destination when `fulfillment === 'Delivery'` (no district). */
export interface AccessoryDeliveryAddressRequest {
  city: string; // Tỉnh/Thành phố
  ward: string; // Phường/Xã
  address: string;
  note?: string;
}

/**
 * Full checkout payload (design `CheckoutInput`). Includes contact details plus the chosen
 * fulfillment mode and its mode-specific fields.
 */
export interface AccessoryCheckoutRequest {
  lines: AccessoryCheckoutLineRequest[]; // the entire cart, 1..50 lines (Req 6.1)
  customerName: string;
  customerPhone: string;
  customerEmail: string;
  discountCode?: string;
  /** Defaults to PickupAtShow on the server when omitted. */
  fulfillment?: 'PickupAtShow' | 'MetaTicketPickup' | 'Delivery';
  // Required when fulfillment is PickupAtShow and the owning show has sessions.
  showSessionId?: string;
  // Required when fulfillment is MetaTicketPickup.
  pickupPointId?: string;
  // Required when fulfillment is Delivery.
  deliveryAddress?: AccessoryDeliveryAddressRequest;
}
