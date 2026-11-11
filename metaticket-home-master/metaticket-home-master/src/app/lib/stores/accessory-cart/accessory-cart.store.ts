import { Store, StoreConfig } from '@datorama/akita';
import { Injectable } from '@angular/core';

/**
 * Minimum / soft-max quantity per cart line (Req 3.3, 3.5, 3.6).
 * Soft-max applies only when the product has no `maxOrderQty`; otherwise the product (and per-user)
 * limits win. Soft-max must match backend `accessory.checkout.maxLineQuantity`.
 */
export const ACCESSORY_CART_MIN_QUANTITY = 1;
export const ACCESSORY_CART_MAX_QUANTITY = 99;

/**
 * A single item in the client-only cart (design `CartLine`): `accessoryId` +
 * optional `variantKey` (present iff the accessory has variants) + `quantity`.
 * Display snapshots (name/label/price/image) are carried purely so the cart UI
 * can render without re-fetching; they are not authoritative — the backend
 * re-derives price and labels from the catalog at checkout.
 */
export interface CartLine {
  accessoryId: string;
  variantKey?: string;
  quantity: number;
  // display-only snapshots
  name?: string;
  variantLabel?: string;
  unitPrice?: number;
  imageUrl?: string;
  /** Product-level purchase limits (optional; empty = unlimited besides soft cart cap). */
  minOrderQty?: number;
  maxOrderQty?: number;
  maxPerUserQty?: number;
  /** Units already counted toward maxPerUserQty for this user (Paid + open holds). */
  myPurchasedQty?: number;
}

/**
 * Client-side cart (design `Cart`). The cart is scoped to a single accessory
 * show (`accessoryShowId`); adding a line from a different show replaces the
 * cart. `showSessionId` records which show session the customer is buying these
 * products for (session-selection feature); it is submitted at checkout and
 * reset whenever the cart switches to a different show.
 */
export interface Cart {
  accessoryShowId: string;
  showSessionId?: string;
  lines: CartLine[];
}

export function createInitialCart(): Cart {
  return { accessoryShowId: '', showSessionId: undefined, lines: [] };
}

@Injectable({ providedIn: 'root' })
@StoreConfig({ name: 'accessory-cart' })
export class AccessoryCartStore extends Store<Cart> {
  constructor() {
    super(createInitialCart());
  }
}
