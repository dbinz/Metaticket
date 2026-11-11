import { Injectable } from '@angular/core';
import { accessoryOrderTtlMinutes, storageKeys } from 'src/app/lib/constants';
import { LocalStorageService } from '../../services/local-storage/local-storage.service';
import { AccessoryCheckoutState, AccessoryCheckoutStore, createInitialCheckoutState } from './accessory-checkout.store';

/**
 * Drives the accessory checkout flow state. Cart lines live in `AccessoryCartService`;
 * this service advances the step index and records discount, customer info, fulfillment
 * draft, and the encoded checkout result.
 *
 * Page-internal indices:
 * 0 = Điền thông tin, 1 = Thông tin nhận hàng, 2 = Thanh toán, 3 = Hoàn tất.
 */
@Injectable({ providedIn: 'root' })
export class AccessoryCheckoutService {
  constructor(
    private _store: AccessoryCheckoutStore,
    private _localStorage: LocalStorageService,
  ) {}

  /** Reset the flow to the first step with a clean state. */
  reset(): void {
    this._store.update(createInitialCheckoutState());
    this._persist();
  }

  /** Restore a persisted checkout flow into the store (e.g. after a reload). */
  restore(): void {
    const saved = this._localStorage.getData<AccessoryCheckoutState>(storageKeys.accessoryCheckout);
    if (saved) {
      this._store.update({
        ...createInitialCheckoutState(),
        ...saved,
        fulfillmentType: saved.fulfillmentType ?? '',
        showSessionId: saved.showSessionId ?? '',
        pickupPointId: saved.pickupPointId ?? '',
        deliveryCity: saved.deliveryCity ?? '',
        deliveryWard: saved.deliveryWard ?? '',
        deliveryAddress: saved.deliveryAddress ?? '',
        deliveryNote: saved.deliveryNote ?? '',
      });
    }
  }

  /** Clear the persisted flow and reset the store — called when the customer confirms completion. */
  clear(): void {
    this._store.update(createInitialCheckoutState());
    this._localStorage.removeData(storageKeys.accessoryCheckout);
  }

  /** Record the subtotal computed from the cart at the Chọn step. */
  setSubtotal(subtotal: number): void {
    this._store.update({ subtotal, total: Math.max(0, subtotal - this._store.getValue().discountAmount) });
    this._persist();
  }

  /** Record a previewed discount (Req 4.1). Pass amount 0 / empty code to clear. */
  applyDiscount(code: string, amount: number, subtotal: number, total: number, type: string, value: number): void {
    this._store.update({
      discountCode: code,
      discountAmount: amount,
      discountType: type,
      discountValue: value,
      subtotal,
      total,
    });
    this._persist();
  }

  /** Clear any previewed discount. */
  clearDiscount(): void {
    const state = this._store.getValue();
    this._store.update({
      discountCode: '',
      discountAmount: 0,
      discountType: '',
      discountValue: 0,
      total: state.subtotal,
    });
    this._persist();
  }

  /**
   * Enter the checkout page at the information step (index 0). Preserves previewed
   * discount/subtotal; resets per-order result/payment bookkeeping.
   */
  startAtInformation(): void {
    this._store.update({
      currentIndex: 0,
      encodedOrderId: '',
      encodedPaymentId: '',
      orderCode: '',
      isStartedPayment: false,
      paymentResult: '',
      paymentRedirectUrl: '',
      expiresAt: 0,
      ownerUserId: '',
    });
    this._persist();
  }

  /** Persist customer contact from step 2 and advance to "Thông tin nhận hàng" (index 1). */
  saveCustomerInfo(params: { customerName: string; customerPhone: string; customerEmail: string }): void {
    this._store.update({
      ...params,
      currentIndex: 1,
    });
    this._persist();
    this._scrollTop();
  }

  /** Persist fulfillment draft fields while the customer edits step 3. */
  saveFulfillmentDraft(params: {
    fulfillmentType: '' | 'PickupAtShow' | 'MetaTicketPickup' | 'Delivery';
    showSessionId?: string;
    pickupPointId?: string;
    deliveryCity?: string;
    deliveryWard?: string;
    deliveryAddress?: string;
    deliveryNote?: string;
  }): void {
    this._store.update({
      fulfillmentType: params.fulfillmentType,
      showSessionId: params.showSessionId ?? '',
      pickupPointId: params.pickupPointId ?? '',
      deliveryCity: params.deliveryCity ?? '',
      deliveryWard: params.deliveryWard ?? '',
      deliveryAddress: params.deliveryAddress ?? '',
      deliveryNote: params.deliveryNote ?? '',
    });
    this._persist();
  }

  /**
   * Persist customer info + checkout result.
   * - total > 0 (and payment id present): advance to Thanh toán (index 2).
   * - total <= 0 (zero-pay / free order): skip payment and land on Hoàn tất (index 3) as Succeed.
   */
  checkoutCompleted(params: {
    customerName: string;
    customerPhone: string;
    customerEmail: string;
    encodedOrderId: string;
    encodedPaymentId: string;
    orderCode: string;
    total: number;
    ownerUserId: string;
  }): void {
    const isZeroPay = params.total <= 0 && !params.encodedPaymentId;
    this._store.update({
      ...params,
      encodedPaymentId: isZeroPay ? '' : params.encodedPaymentId,
      total: Math.max(0, params.total),
      currentIndex: isZeroPay ? 3 : 2,
      isStartedPayment: false,
      paymentResult: isZeroPay ? 'Succeed' : '',
      paymentRedirectUrl: '',
      expiresAt: isZeroPay ? 0 : Date.now() + accessoryOrderTtlMinutes * 60_000,
    });
    this._persist();
    this._scrollTop();
  }

  /**
   * Resume an unpaid hold returned by `GET accessory/order/pending`.
   * Uses server `expiresAt` so the countdown matches the BE TTL sweep.
   */
  resumePendingPayment(params: {
    customerName: string;
    customerPhone: string;
    customerEmail: string;
    encodedOrderId: string;
    encodedPaymentId: string;
    orderCode: string;
    total: number;
    ownerUserId: string;
    expiresAt: number;
  }): void {
    this._store.update({
      ...params,
      currentIndex: 2,
      isStartedPayment: false,
      paymentResult: '',
      paymentRedirectUrl: '',
      expiresAt: params.expiresAt,
    });
    this._persist();
    this._scrollTop();
  }

  /** Mark the gateway payment as started (so a returning tab can resume polling). */
  startPayment(): void {
    this._store.update({ isStartedPayment: true });
    this._persist();
  }

  /** Reset the payment-started flag after a failed/cancelled/errored attempt. */
  resetPaymentState(): void {
    this._store.update({ isStartedPayment: false, paymentRedirectUrl: '' });
    this._persist();
  }

  /**
   * Advance from Thanh toán (2) to Hoàn tất (3) in a PENDING state after payment is started.
   * [redirectUrl] is kept so the customer can reopen the gateway page after closing its tab.
   */
  goToPaymentTracking(redirectUrl = ''): void {
    this._store.update({
      currentIndex: 3,
      isStartedPayment: true,
      paymentResult: '',
      paymentRedirectUrl: redirectUrl,
    });
    this._persist();
    this._scrollTop();
  }

  /** Record a successful payment outcome on the completed step. */
  paymentSucceeded(): void {
    this._store.update({ paymentResult: 'Succeed', isStartedPayment: false });
    this._advanceTo(3);
  }

  /** Record a failed/cancelled payment outcome on the completed step. */
  paymentFailed(): void {
    this._store.update({ paymentResult: 'Failed', isStartedPayment: false });
    this._advanceTo(3);
  }

  /** Go back one step (used by information / fulfillment / payment). */
  onPreviousIndex(): void {
    const previous = Math.max(0, this._store.getValue().currentIndex - 1);
    this._store.update({ currentIndex: previous });
    this._persist();
    this._scrollTop();
  }

  /**
   * Migrate persisted flows created before the fulfillment step existed
   * (payment used to be index 1; completed used to be index 2).
   */
  migrateLegacyStepIndices(): void {
    const state = this._store.getValue();
    if (state.encodedPaymentId && state.currentIndex === 1) {
      this._store.update({ currentIndex: 2 });
      this._persist();
      return;
    }
    if (state.encodedOrderId && state.currentIndex === 2 && state.encodedPaymentId) {
      // Old completed was index 2; keep there only if we also have paymentResult tracking —
      // when paymentResult is set and index is 2, bump to 3.
      if (state.paymentResult) {
        this._store.update({ currentIndex: 3 });
        this._persist();
      }
    }
  }

  private _advanceTo(index: number): void {
    this._store.update({ currentIndex: index });
    this._persist();
    this._scrollTop();
  }

  private _persist(): void {
    this._localStorage.saveData(storageKeys.accessoryCheckout, this._store.getValue());
  }

  private _scrollTop(): void {
    setTimeout(() => window.scrollTo(0, 0), 100);
  }
}
