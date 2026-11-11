import { Injectable } from '@angular/core';
import { storageKeys } from 'src/app/lib/constants';
import { LocalStorageService } from '../../services/local-storage/local-storage.service';
import { AuthService } from '../../services/auth-service/auth.service';
import {
  ACCESSORY_CART_MAX_QUANTITY,
  ACCESSORY_CART_MIN_QUANTITY,
  Cart,
  CartLine,
  AccessoryCartStore,
  createInitialCart,
} from './accessory-cart.store';

/** Outcome of a cart mutation, so callers can surface the right feedback (Req 3.6). */
export type AccessoryCartResult =
  | { ok: true }
  | { ok: false; reason: 'max-quantity'; max: number }
  | { ok: false; reason: 'min-quantity'; min: number }
  | { ok: false; reason: 'max-user'; max: number; remaining: number };

/**
 * Client-only accessory cart (Req 3). Holds the cart in an Akita store and persists it to
 * `localStorage` on every mutation, never contacting the backend (Req 3.1, 3.2). The full line set
 * is submitted once at checkout; on success the cart is cleared (Req 3.4). Line identity is
 * `(accessoryId, variantKey)`.
 *
 * The cart is **scoped per user**: the `localStorage` key is suffixed with the logged-in user's id,
 * so different accounts never share a cart on the same browser. The service reacts to auth changes
 * (login / logout / user switch) by reloading the current user's cart, and refuses to persist while
 * logged out (adding to cart requires login).
 */
@Injectable({ providedIn: 'root' })
export class AccessoryCartService {
  constructor(
    private _store: AccessoryCartStore,
    private _localStorageService: LocalStorageService,
    private _authService: AuthService,
  ) {
    // Re-sync the cart whenever the authenticated user changes: clear the in-memory cart (so the
    // previous user's cart is never shown) then load the current user's persisted cart, if any.
    this._authService.authStatus$.subscribe(() => {
      this._store.update(createInitialCart());
      this.restore();
    });
  }

  /** The per-user `localStorage` key, or null when no user is logged in. */
  private _cartKey(): string | null {
    const userId = this._authService.getUserId();
    return userId ? `${storageKeys.accessoryCart}:${userId}` : null;
  }

  /** Restore the current user's persisted cart into client-side state on storefront load (Req 3.7). */
  restore(): void {
    const key = this._cartKey();
    const saved = key ? this._localStorageService.getData<Cart>(key) : null;
    if (saved && Array.isArray(saved.lines)) {
      this._store.update({
        accessoryShowId: saved.accessoryShowId ?? '',
        showSessionId: saved.showSessionId,
        lines: saved.lines.filter((line) => this._isValidLine(line)),
      });
    } else {
      // No cart for this user (or logged out) → start from an empty cart.
      this._store.update(createInitialCart());
    }
  }

  /**
   * Record which show session the customer is buying for (session-selection feature). Scoped to a
   * single show, mirroring the cart itself; persisted with the cart so it survives navigation to
   * the checkout page. The product page keeps this in sync with its session selector.
   */
  setSession(showSessionId: string, accessoryShowId: string): void {
    const state = this._store.getValue();
    this._commit({
      accessoryShowId: state.lines.length > 0 ? state.accessoryShowId : accessoryShowId,
      showSessionId,
      lines: state.lines,
    });
  }

  /**
   * Add a line to the cart (Req 2.6/2.7, 3.2). `variantKey` is recorded for variant
   * products and omitted for single-SKU products. If a matching line already exists
   * its quantity is increased. A resulting quantity above 99 is rejected and the
   * previous quantity is retained (Req 3.6); anything below 1 is coerced up to 1
   * (Req 3.3). Adding a line from a different accessory show replaces the current
   * cart, since the cart is scoped to a single show.
   */
  addLine(line: CartLine, accessoryShowId: string): AccessoryCartResult {
    const state = this._store.getValue();
    const requested = Math.trunc(line.quantity || 0);
    const lineMin = Math.max(ACCESSORY_CART_MIN_QUANTITY, line.minOrderQty ?? ACCESSORY_CART_MIN_QUANTITY);
    // First add for this product (any variant) bumps to product min when set.
    const existingProductQty = this._productQty(state.lines, line.accessoryId);
    const quantity =
      existingProductQty === 0 && requested < lineMin
        ? lineMin
        : requested < ACCESSORY_CART_MIN_QUANTITY
          ? ACCESSORY_CART_MIN_QUANTITY
          : requested;

    // A cart holds items from a single show; switching shows starts a fresh cart (and drops the
    // previously selected session, which belonged to the old show).
    const isDifferentShow = !!state.accessoryShowId && state.accessoryShowId !== accessoryShowId;
    const baseLines = isDifferentShow ? [] : [...state.lines];
    const showSessionId = isDifferentShow ? undefined : state.showSessionId;
    const existingIndex = baseLines.findIndex((existing) => this._isSameLine(existing, line));

    let nextLines: CartLine[];
    if (existingIndex >= 0) {
      const merged = baseLines[existingIndex].quantity + quantity;
      nextLines = [...baseLines];
      nextLines[existingIndex] = { ...baseLines[existingIndex], ...line, quantity: merged };
    } else {
      nextLines = [...baseLines, { ...line, quantity }];
    }

    const limit = this._checkProductLimits(nextLines, line.accessoryId);
    if (!limit.ok) {
      return limit;
    }

    this._commit({ accessoryShowId, showSessionId, lines: nextLines });
    return { ok: true };
  }

  /**
   * Set a line's absolute quantity (Req 3.2, 3.5, 3.6). A value below 1 removes the
   * line; a value above the effective max is rejected and the previous quantity is retained.
   */
  updateQuantity(accessoryId: string, variantKey: string | undefined, quantity: number): AccessoryCartResult {
    const state = this._store.getValue();
    const index = state.lines.findIndex((line) => this._isSameLine(line, { accessoryId, variantKey }));
    if (index < 0) {
      return { ok: true };
    }

    const next = Math.trunc(quantity || 0);
    if (next < ACCESSORY_CART_MIN_QUANTITY) {
      this.removeLine(accessoryId, variantKey);
      return { ok: true };
    }

    const lines = [...state.lines];
    lines[index] = { ...lines[index], quantity: next };
    const limit = this._checkProductLimits(lines, accessoryId);
    if (!limit.ok) {
      return limit;
    }

    this._commit({ ...state, lines });
    return { ok: true };
  }

  /** Remove a line from the cart and persist the change (Req 3.2, 3.5). */
  removeLine(accessoryId: string, variantKey?: string): void {
    const state = this._store.getValue();
    const lines = state.lines.filter((line) => !this._isSameLine(line, { accessoryId, variantKey }));
    this._commit({ ...state, lines });
  }

  /** Clear the cart and remove it from `localStorage`, e.g. on successful checkout (Req 3.4). */
  clear(): void {
    this._store.update(createInitialCart());
    const key = this._cartKey();
    if (key) {
      this._localStorageService.removeData(key);
    }
  }

  /** Update store state and mirror it to the current user's `localStorage` key on every mutation (Req 3.2). */
  private _commit(state: Cart): void {
    const hasLines = state.lines.length > 0;
    const normalized: Cart = {
      accessoryShowId: hasLines ? state.accessoryShowId : '',
      // Keep the selected session in memory (so a session picked before the first add survives the
      // add) but only persist it alongside a non-empty cart.
      showSessionId: state.showSessionId,
      lines: state.lines,
    };
    this._store.update(normalized);
    const key = this._cartKey();
    // Only persist for a logged-in user; adding to cart requires login, so a logged-out session
    // never has anything to persist.
    if (!key) {
      return;
    }
    if (hasLines) {
      this._localStorageService.saveData(key, normalized);
    } else {
      this._localStorageService.removeData(key);
    }
  }

  private _isSameLine(a: CartLine, b: Pick<CartLine, 'accessoryId' | 'variantKey'>): boolean {
    return a.accessoryId === b.accessoryId && (a.variantKey ?? '') === (b.variantKey ?? '');
  }

  /** Total units of one product across all variant lines in the cart. */
  private _productQty(lines: CartLine[], accessoryId: string): number {
    return lines.filter((l) => l.accessoryId === accessoryId).reduce((sum, l) => sum + l.quantity, 0);
  }

  /**
   * Enforce product-level limits (and soft cart cap when maxOrderQty is unset) after a mutation.
   * Limits apply to the sum of all lines for [accessoryId].
   */
  private _checkProductLimits(lines: CartLine[], accessoryId: string): AccessoryCartResult {
    const productLines = lines.filter((l) => l.accessoryId === accessoryId);
    if (!productLines.length) {
      return { ok: true };
    }
    const sample = productLines[0];
    const total = productLines.reduce((sum, l) => sum + l.quantity, 0);
    if (sample.maxPerUserQty != null && sample.maxPerUserQty >= 1) {
      const remaining = Math.max(0, sample.maxPerUserQty - (sample.myPurchasedQty ?? 0));
      if (total > remaining) {
        return {
          ok: false,
          reason: 'max-user',
          max: sample.maxPerUserQty,
          remaining,
        };
      }
    }
    const maxAllowed = this._maxOrderCap(sample);
    if (total > maxAllowed) {
      return { ok: false, reason: 'max-quantity', max: maxAllowed };
    }
    // Soft min check only when every unit of the product is still on the cart (cart checkout will recheck).
    if (sample.minOrderQty != null && sample.minOrderQty > 1 && total < sample.minOrderQty) {
      return { ok: false, reason: 'min-quantity', min: sample.minOrderQty };
    }
    return { ok: true };
  }

  /** Product `maxOrderQty` when set; otherwise the soft technical cart cap. */
  private _maxOrderCap(line: Pick<CartLine, 'maxOrderQty'>): number {
    return line.maxOrderQty != null && line.maxOrderQty >= 1 ? line.maxOrderQty : ACCESSORY_CART_MAX_QUANTITY;
  }

  private _isValidLine(line: CartLine): boolean {
    const max = this._maxOrderCap(line);
    return (
      !!line &&
      typeof line.accessoryId === 'string' &&
      Number.isInteger(line.quantity) &&
      line.quantity >= ACCESSORY_CART_MIN_QUANTITY &&
      line.quantity <= max
    );
  }
}
