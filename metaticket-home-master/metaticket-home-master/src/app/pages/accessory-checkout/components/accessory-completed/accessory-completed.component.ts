import { Component, OnDestroy, OnInit } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { ButtonComponent, ImageComponent, LoadingComponent } from 'src/app/lib/components';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { appIcons } from 'src/app/lib/theme';
import { routeName } from 'src/app/lib/constants';
import { AccessoryService, PaymentService, PaymentWindowService } from 'src/app/lib/services';
import { PaymentStatus } from 'src/app/lib/enums';
import { AccessoryOrderResponse, AccessoryOrderStatus } from 'src/app/lib/entities';
import {
  AccessoryCartQuery,
  AccessoryCartService,
  AccessoryCheckoutQuery,
  AccessoryCheckoutService,
} from 'src/app/lib/stores';

/** Overall resolution combining the payment and the accessory order statuses. */
type OverallStatus = 'processing' | 'success' | 'failed' | 'mismatch';

/** MetaTicket support contact, shown when the two statuses disagree. */
const SUPPORT_HOTLINE = '1900 3392';
const SUPPORT_EMAIL = 'support@metaticket.com.vn';

/**
 * Step 4 "Hoàn tất" of the accessory checkout. It reconciles TWO statuses:
 *  - the accessory **order** status (`GET public/accessory/order/fetch`) — always checked,
 *  - the **payment** status (`GET payment/status`) — checked only when the order has a payment.
 *
 * It shows a message per status, then an overall outcome:
 *  - both success (or order-only success) → success,
 *  - both non-success → failed,
 *  - one success + the other not → mismatch: ask the customer to contact MetaTicket (with contact
 *    details), since the payment and the order disagree.
 *
 * Statuses are polled until both resolve (bounded budget), so a delayed payment→order callback still
 * settles. The result is persisted so a reload resumes correctly.
 */
@Component({
  selector: 'accessory-completed',
  standalone: true,
  imports: [
    NgIf,
    NgForOf,
    NzTypographyModule,
    NzDividerComponent,
    ImageComponent,
    LoadingComponent,
    ButtonComponent,
    FormatPricePipe,
  ],
  templateUrl: './accessory-completed.component.html',
})
export class AccessoryCompletedComponent implements OnInit, OnDestroy {
  protected readonly appIcons = appIcons;
  protected readonly supportHotline = SUPPORT_HOTLINE;
  protected readonly supportEmail = SUPPORT_EMAIL;
  /** Fallback receive instructions when the order has no fulfillmentSummary yet. */
  protected readonly defaultPickupNote = 'Nhận sản phẩm tại show. Vui lòng xuất trình mã đơn hàng khi nhận.';

  private readonly _encodedOrderId: string;
  private readonly _encodedPaymentId: string;
  readonly orderCode: string;
  /** Whether this order has a payment to reconcile against (order-only otherwise). */
  readonly hasPayment: boolean;
  /** Gateway URL của lần thanh toán đang chờ — rỗng khi không có gì để mở lại. */
  readonly paymentRedirectUrl: string;

  order: AccessoryOrderResponse | null = null;
  paymentStatus: PaymentStatus | null = null;
  orderStatus: AccessoryOrderStatus | null = null;
  /** Whether the "leave while still processing" warning confirmation is open. */
  leaveConfirmOpen = false;

  /** Poll interval while a status is still pending. Polling never gives up on its own — it keeps
   * running until the order (and payment, when present) resolves, so a delayed gateway callback
   * always settles the page without any manual action. */
  private readonly _pollIntervalMs = 3 * 1000;
  private _successRecorded = false;
  private _failRecorded = false;
  private _stopped = false;
  private _pollTimeout?: ReturnType<typeof setTimeout>;

  constructor(
    private _accessoryService: AccessoryService,
    private _paymentService: PaymentService,
    private _cartService: AccessoryCartService,
    private _cartQuery: AccessoryCartQuery,
    private _checkoutQuery: AccessoryCheckoutQuery,
    private _checkoutService: AccessoryCheckoutService,
    private _paymentWindowService: PaymentWindowService,
    private _router: Router,
  ) {
    const state = this._checkoutQuery.getValue();
    this._encodedOrderId = state.encodedOrderId;
    this._encodedPaymentId = state.encodedPaymentId;
    this.orderCode = state.orderCode;
    this.hasPayment = !!state.encodedPaymentId;
    this.paymentRedirectUrl = state.paymentRedirectUrl;
  }

  /** Mở lại cổng thanh toán khi khách lỡ đóng tab. Đang trong click handler nên không bị chặn. */
  onReopenPaymentWindow(): void {
    if (!this.paymentRedirectUrl) return;

    const paymentWindow = this._paymentWindowService.openPlaceholder();
    if (!this._paymentWindowService.navigateTo(paymentWindow, this.paymentRedirectUrl)) {
      this._paymentWindowService.redirectSameTab(this.paymentRedirectUrl);
    }
  }

  ngOnInit(): void {
    this._poll();
    // The payment gateway opens in a separate tab, so the customer completes payment away from this
    // page. When they switch back here, poll once immediately (if still unresolved) so the status
    // updates on return without waiting for the next tick.
    document.addEventListener('visibilitychange', this._onVisibilityChange);
    window.addEventListener('focus', this._onWindowFocus);
  }

  ngOnDestroy(): void {
    this._stopped = true;
    if (this._pollTimeout) {
      clearTimeout(this._pollTimeout);
    }
    document.removeEventListener('visibilitychange', this._onVisibilityChange);
    window.removeEventListener('focus', this._onWindowFocus);
  }

  /** Poll once immediately when the tab becomes visible again after returning from the gateway. */
  private readonly _onVisibilityChange = (): void => {
    if (document.visibilityState === 'visible') {
      this._pollNowIfPending();
    }
  };

  /** Same intent as visibilitychange, for browsers/flows that only raise window focus. */
  private readonly _onWindowFocus = (): void => {
    this._pollNowIfPending();
  };

  /**
   * Trigger an immediate poll (cancelling the pending timer) when a status is still unresolved and
   * the loop is still active. No-op once both statuses are terminal or the component is destroyed.
   */
  private _pollNowIfPending(): void {
    if (this._stopped) {
      return;
    }
    const isStillPending = (this.hasPayment && !this._isPaymentResolved()) || !this._isOrderResolved();
    if (isStillPending) {
      if (this._pollTimeout) {
        clearTimeout(this._pollTimeout);
      }
      this._poll();
    }
  }

  // --- Overall + per-status classification -----------------------------------------------------

  get overall(): OverallStatus {
    const isAnyFailed = (this.hasPayment && this.paymentStatusClass === 'failed') || this.orderStatusClass === 'failed';
    const isAnySuccess =
      (this.hasPayment && this.paymentStatus === PaymentStatus.Succeed) || this.orderStatus === 'PaymentSucceed';

    // A failure on either side is actionable right away — don't wait for the other status.
    // If the other side already succeeded, the two disagree → mismatch (needs support); otherwise
    // it's a plain failure (no charge went through).
    if (isAnyFailed) {
      return isAnySuccess ? 'mismatch' : 'failed';
    }

    // No failure yet. Both sides confirmed → success.
    const isBothSuccess = this.hasPayment
      ? this.paymentStatus === PaymentStatus.Succeed && this.orderStatus === 'PaymentSucceed'
      : this.orderStatus === 'PaymentSucceed';
    if (isBothSuccess) return 'success';

    // Otherwise still resolving — polling continues until both statuses settle. If the customer is
    // stuck (e.g. cannot pay), they can leave via the warning action below.
    return 'processing';
  }

  get isProcessing(): boolean {
    return this.overall === 'processing';
  }

  get isSuccess(): boolean {
    return this.overall === 'success';
  }

  get isFailed(): boolean {
    return this.overall === 'failed';
  }

  get isMismatch(): boolean {
    return this.overall === 'mismatch';
  }

  /** Human message for the payment status (shown only when the order has a payment). */
  get paymentStatusMessage(): string {
    switch (this.paymentStatus) {
      case PaymentStatus.Succeed:
        return 'Thanh toán thành công';
      case PaymentStatus.Failed:
        return 'Thanh toán thất bại';
      case PaymentStatus.Cancelled:
        return 'Thanh toán đã bị hủy';
      case PaymentStatus.Processing:
        return 'Thanh toán đang xử lý';
      default:
        return 'Đang kiểm tra thanh toán…';
    }
  }

  get paymentStatusOk(): boolean {
    return this.paymentStatus === PaymentStatus.Succeed;
  }

  /** Colour class for the payment row: 'success' (green), 'failed' (red), 'pending' (neutral). */
  get paymentStatusClass(): 'success' | 'failed' | 'pending' {
    if (this.paymentStatus === PaymentStatus.Succeed) return 'success';
    if (this.paymentStatus === PaymentStatus.Failed || this.paymentStatus === PaymentStatus.Cancelled) return 'failed';
    return 'pending';
  }

  /** Human message for the accessory order status. */
  get orderStatusMessage(): string {
    switch (this.orderStatus) {
      case 'PaymentSucceed':
        return 'Đơn hàng đã được xác nhận';
      case 'PaymentFailed':
        return 'Đơn hàng thất bại';
      case 'PaymentCancelled':
        return 'Đơn hàng đã bị hủy';
      case 'Expired':
        return 'Đơn hàng đã hết hạn';
      case 'Created':
      case 'WaitingForPayment':
      case 'PaymentProcessing':
        return 'Đơn hàng đang chờ xử lý';
      default:
        return 'Đang kiểm tra đơn hàng…';
    }
  }

  get orderStatusOk(): boolean {
    return this.orderStatus === 'PaymentSucceed';
  }

  /** Colour class for the order row: 'success' (green), 'failed' (red), 'pending' (neutral). */
  get orderStatusClass(): 'success' | 'failed' | 'pending' {
    if (this.orderStatus === 'PaymentSucceed') return 'success';
    if (
      this.orderStatus === 'PaymentFailed' ||
      this.orderStatus === 'PaymentCancelled' ||
      this.orderStatus === 'Expired'
    ) {
      return 'failed';
    }
    return 'pending';
  }

  get fulfillmentSummary(): string {
    return this.order?.fulfillmentSummary || this.defaultPickupNote;
  }

  // --- Actions ---------------------------------------------------------------------------------

  onReviewOrder(): void {
    this._fetchOrderOnce();
    setTimeout(() => window.scrollTo(0, 0), 100);
  }

  /** Open the warning confirmation before leaving while payment is still pending. */
  onRequestLeaveWhileProcessing(): void {
    this.leaveConfirmOpen = true;
  }

  /** Dismiss the leave warning and stay on the (still-polling) completed page. */
  onCancelLeave(): void {
    this.leaveConfirmOpen = false;
  }

  /** Confirmed leaving while pending → close the warning and return to the cart. */
  onConfirmLeave(): void {
    this.leaveConfirmOpen = false;
    this.onBackToCart();
  }

  /** Success → homepage `/`. */
  onGoHome(): void {
    this._leaveSuccess(['/']);
  }

  /** Success → customer order list `/quan-ly-don-hang`. */
  onGoOrderManager(): void {
    this._leaveSuccess(['/', routeName.orderManager]);
  }

  /**
   * Both statuses succeeded → clear the cart and the checkout flow, then continue shopping on the
   * product list so the customer starts a fresh order (the completed order must not linger in the cart).
   */
  onContinueShopping(): void {
    this._leaveSuccess(['/', routeName.product]);
  }

  /** Drop checkout leftovers then navigate so a completed order cannot reopen the cart. */
  private _leaveSuccess(commands: string[]): void {
    this._cartService.clear();
    this._checkoutService.clear();
    this._router.navigate(commands).then(() => {
      setTimeout(() => window.scrollTo(0, 0), 100);
    });
  }

  /**
   * A status failed (no payment went through) → clear the flow and return to the cart on the show's
   * page so the customer can adjust and try again. The cart itself is untouched on failure.
   */
  onBackToCart(): void {
    const accessoryShowId = this._cartQuery.getValue().accessoryShowId;
    this._checkoutService.clear();
    const navigation = accessoryShowId
      ? this._router.navigate(['/', routeName.productDetail, accessoryShowId], { queryParams: { openCart: 1 } })
      : this._router.navigate(['/', routeName.product]);
    navigation.then(() => setTimeout(() => window.scrollTo(0, 0), 100));
  }

  onConfirmComplete(): void {
    this._checkoutService.clear();
    this._router.navigate(['/', routeName.product]).then(() => {
      setTimeout(() => window.scrollTo(0, 0), 100);
    });
  }

  // --- Polling ---------------------------------------------------------------------------------

  private _poll(): void {
    if (this._stopped) {
      return;
    }

    const payment$ = this.hasPayment
      ? this._paymentService.getPaymentStatus(this._encodedPaymentId).pipe(catchError(() => of(null)))
      : of(null);
    const order$ = this._encodedOrderId
      ? this._accessoryService.getAccessoryOrder(this._encodedOrderId).pipe(catchError(() => of(null)))
      : of(null);

    forkJoin({ payment: payment$, order: order$ }).subscribe(({ payment, order }) => {
      if (this._stopped) {
        return;
      }
      if (payment) {
        this.paymentStatus = payment.status;
      }
      if (order) {
        this.order = order;
        this.orderStatus = order.status;
      }
      this._recordOutcome();

      // Keep polling until every status settles. There is no attempt cap: a delayed gateway
      // callback can arrive minutes later and the page still updates on its own. If the customer is
      // stuck and wants to give up, the warning "leave" action returns them to the cart.
      const isStillPending = (this.hasPayment && !this._isPaymentResolved()) || !this._isOrderResolved();
      if (isStillPending) {
        this._pollTimeout = setTimeout(() => this._poll(), this._pollIntervalMs);
      } else {
        this._stopped = true;
      }
    });
  }

  /** Persist the outcome + clear the cart once, when a terminal success/failure is known. */
  private _recordOutcome(): void {
    const hasSucceeded = this.hasPayment
      ? this.paymentStatus === PaymentStatus.Succeed
      : this.orderStatus === 'PaymentSucceed';
    if (hasSucceeded && !this._successRecorded) {
      this._successRecorded = true;
      this._cartService.clear();
      this._checkoutService.clearDiscount();
      this._checkoutService.paymentSucceeded();
      return;
    }

    const hasFailed = this.hasPayment
      ? this.paymentStatus === PaymentStatus.Failed || this.paymentStatus === PaymentStatus.Cancelled
      : this._isOrderResolved() && this.orderStatus !== 'PaymentSucceed';
    if (hasFailed && !this._failRecorded && !this._successRecorded) {
      this._failRecorded = true;
      this._checkoutService.paymentFailed();
    }
  }

  private _isPaymentResolved(): boolean {
    return (
      this.paymentStatus === PaymentStatus.Succeed ||
      this.paymentStatus === PaymentStatus.Failed ||
      this.paymentStatus === PaymentStatus.Cancelled
    );
  }

  private _isOrderResolved(): boolean {
    return (
      this.orderStatus === 'PaymentSucceed' ||
      this.orderStatus === 'PaymentFailed' ||
      this.orderStatus === 'PaymentCancelled' ||
      this.orderStatus === 'Expired'
    );
  }

  private _fetchOrderOnce(): void {
    if (!this._encodedOrderId) {
      return;
    }
    this._accessoryService.getAccessoryOrder(this._encodedOrderId).subscribe({
      next: (res) => {
        this.order = res;
        this.orderStatus = res.status;
      },
      error: (err) => console.log('fetch accessory order error', err),
    });
  }
}
