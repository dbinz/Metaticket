import { Injectable, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, of, Subscription } from 'rxjs';
import { catchError, finalize, map } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { routeName } from 'src/app/lib/constants';
import { PendingAccessoryOrder } from 'src/app/lib/entities';
import { AuthService } from '../auth-service/auth.service';
import { NotificationService } from '../notification/notification.service';
import { AccessoryCheckoutQuery, AccessoryCheckoutService } from 'src/app/lib/stores';
import { AccessoryService } from './accessory.service';

/**
 * On product pages, if the customer is logged in, loads the newest unpaid accessory hold
 * (`GET accessory/order/pending`) and surfaces a blocking continue/cancel modal.
 */
@Injectable({ providedIn: 'root' })
export class AccessoryPendingOrderPromptService implements OnDestroy {
  private readonly _pending$ = new BehaviorSubject<PendingAccessoryOrder | null>(null);
  private readonly _loadingAction$ = new BehaviorSubject<'continue' | 'cancel' | null>(null);
  private readonly _checking$ = new BehaviorSubject<boolean>(false);
  private _checkSub: Subscription | null = null;

  readonly pending$ = this._pending$.asObservable();
  readonly loadingAction$ = this._loadingAction$.asObservable();
  readonly checking$ = this._checking$.asObservable();

  constructor(
    private _auth: AuthService,
    private _accessory: AccessoryService,
    private _checkout: AccessoryCheckoutService,
    private _checkoutQuery: AccessoryCheckoutQuery,
    private _router: Router,
    private _notification: NotificationService,
  ) {}

  /**
   * Fetch pending order when the user is authenticated. No-op when logged out.
   * Skips the modal if checkout store already resumed the same unpaid order.
   */
  checkOnProductPage(): void {
    this._checkSub?.unsubscribe();
    if (!this._auth.isAuthenticated()) {
      this._pending$.next(null);
      return;
    }
    this._checking$.next(true);
    this._checkSub = this._accessory
      .getPendingAccessoryOrder()
      .pipe(
        map((res) => res.order ?? null),
        catchError((err: HttpErrorResponse) => {
          // Stay silent on product browse for auth/network blips — do not block the catalog.
          if (err.status !== 401) {
            console.warn('pending accessory order lookup failed', err.status);
          }
          return of(null);
        }),
        finalize(() => this._checking$.next(false)),
      )
      .subscribe((order) => {
        if (!order || this._isAlreadyResumed(order.orderId)) {
          this._pending$.next(null);
          return;
        }
        this._pending$.next(order);
      });
  }

  /** Whether the active checkout store already points at this unpaid order past information. */
  private _isAlreadyResumed(orderId: string): boolean {
    const state = this._checkoutQuery.getValue();
    if (!state.encodedOrderId || state.encodedOrderId !== orderId) {
      return false;
    }
    return state.currentIndex >= 2;
  }

  /** Resume payment using server `expiresAt` (matches BE TTL). */
  continuePayment(): void {
    const order = this._pending$.getValue();
    if (!order || this._loadingAction$.getValue()) {
      return;
    }
    if (!order.paymentId) {
      this._notification.showErrorNotification('Đơn chưa sẵn sàng thanh toán. Vui lòng hủy đơn và đặt lại.');
      return;
    }
    const userId = this._auth.getUserId();
    if (!userId) {
      return;
    }
    this._loadingAction$.next('continue');
    this._checkout.resumePendingPayment({
      customerName: order.customerName,
      customerPhone: order.customerPhone,
      customerEmail: order.customerEmail,
      encodedOrderId: order.orderId,
      encodedPaymentId: order.paymentId,
      orderCode: order.orderCode,
      total: order.total,
      ownerUserId: userId,
      expiresAt: order.expiresAt,
    });
    this._pending$.next(null);
    this._loadingAction$.next(null);
    void this._router.navigate(['/', routeName.productCheckout]).then(() => {
      setTimeout(() => window.scrollTo(0, 0), 100);
    });
  }

  /** Cancel on the server (releases reserved stock) then clear local checkout. */
  cancelPending(): void {
    const order = this._pending$.getValue();
    if (!order || this._loadingAction$.getValue()) {
      return;
    }
    this._loadingAction$.next('cancel');
    this._accessory.cancelAccessoryOrder(order.orderId).subscribe({
      next: () => {
        this._checkout.clear();
        this._pending$.next(null);
        this._loadingAction$.next(null);
        this._notification.showSuccessNotification('Đã hủy đơn chưa thanh toán.');
      },
      error: (err: HttpErrorResponse) => {
        this._loadingAction$.next(null);
        this._notification.showHttpErrorNotification(err);
      },
    });
  }

  ngOnDestroy(): void {
    this._checkSub?.unsubscribe();
  }
}
