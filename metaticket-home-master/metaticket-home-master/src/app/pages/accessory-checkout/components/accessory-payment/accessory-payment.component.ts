import { Component, OnInit } from '@angular/core';
import { NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { ButtonComponent, ImageComponent } from 'src/app/lib/components';
import { FormatPricePipe } from 'src/app/lib/pipes';
import {
  NotificationService,
  PaymentService,
  AccessoryService,
  PaymentWindowService,
  BlockerDetectionService,
} from 'src/app/lib/services';
import { PaymentType } from 'src/app/lib/enums';
import { StartPaymentRequest } from 'src/app/lib/entities';
import { routeName } from 'src/app/lib/constants';
import { listPaymentMethods } from 'src/app/lib/constants/payment-methods';
import { AccessoryCartQuery, AccessoryCheckoutQuery, AccessoryCheckoutService } from 'src/app/lib/stores';
import { AccessoryOrderCountdownComponent } from '../accessory-order-countdown/accessory-order-countdown.component';

/**
 * Step 3 "Thanh toán" of the accessory checkout — payment-method selection and gateway start
 * (`POST payment/start`), reusing the shared `PaymentService` (Req 7.3).
 *
 * After the gateway payment is started, the flow moves straight to the "Hoàn tất" step, which polls
 * the payment status and reports success / failure / still-processing.
 */
@Component({
  selector: 'accessory-payment',
  standalone: true,
  imports: [
    NgIf,
    NgForOf,
    NgTemplateOutlet,
    NzTypographyComponent,
    NzDividerComponent,
    ImageComponent,
    ButtonComponent,
    FormatPricePipe,
    AccessoryOrderCountdownComponent,
  ],
  templateUrl: './accessory-payment.component.html',
})
export class AccessoryPaymentComponent implements OnInit {
  protected readonly paymentType = PaymentType;

  private readonly _paymentId: string;
  readonly orderCode: string;
  readonly total: number;
  /** Checkout deadline + order id feeding the display-only countdown. */
  readonly expiresAt: number;
  readonly encodedOrderId: string;
  // Filtered to the show's allowed methods in ngOnInit (falls back to all when unconfigured).
  paymentMethods = listPaymentMethods;

  paymentMethod: PaymentType | null = null;
  createPaymentLoading = false;
  /** "Hủy đơn" bookkeeping: whether the confirm dialog is open and whether the cancel call is running. */
  cancelConfirmOpen = false;
  cancelLoading = false;
  /** Trình duyệt chặn tab thanh toán — hiện lối thoát chuyển thẳng trên tab hiện tại. */
  popupBlockedOpen = false;

  private _blockerDetected = false;
  private _blockerWarningAcknowledged = false;

  constructor(
    private _paymentService: PaymentService,
    private _paymentWindowService: PaymentWindowService,
    private _blockerDetectionService: BlockerDetectionService,
    private _notification: NotificationService,
    private _accessoryService: AccessoryService,
    private _cartQuery: AccessoryCartQuery,
    private _checkoutQuery: AccessoryCheckoutQuery,
    private _checkoutService: AccessoryCheckoutService,
    private _router: Router,
  ) {
    const state = this._checkoutQuery.getValue();
    this._paymentId = state.encodedPaymentId;
    this.orderCode = state.orderCode;
    this.total = state.total;
    this.expiresAt = state.expiresAt;
    this.encodedOrderId = state.encodedOrderId;
  }

  ngOnInit(): void {
    void this._refreshBlockerState();
    this._loadAllowedPaymentMethods();
  }

  /**
   * Restrict the offered payment methods to those the show allows (e.g. The Flob: VietQR,
   * Chuyển khoản, Thẻ quốc tế). Fetched from the show detail using the cart's still-present
   * accessoryShowId. An empty/absent list — or any lookup failure — leaves all methods visible,
   * matching the backend which falls back to its default set.
   */
  private _loadAllowedPaymentMethods(): void {
    const accessoryShowId = this._cartQuery.getValue().accessoryShowId;
    if (!accessoryShowId) return;

    this._accessoryService.getAccessoryShow(accessoryShowId).subscribe({
      next: (show) => {
        const allowed = show?.allowedPaymentMethods ?? [];
        if (allowed.length > 0) {
          this.paymentMethods = listPaymentMethods.filter((method) => allowed.includes(method.code));
        }
      },
      error: () => {
        // Keep the full method list on failure; the backend still gates the actual start.
      },
    });
  }

  onSelectPayment(code: PaymentType): void {
    if (this.createPaymentLoading) return;
    this.paymentMethod = code;
  }

  /** Open the "Hủy đơn" confirmation (cancelling is irreversible — it releases the reserved stock). */
  onRequestCancel(): void {
    if (this.cancelLoading) return;
    this.cancelConfirmOpen = true;
  }

  /** Dismiss the cancel confirmation and stay on the payment step. */
  onDismissCancel(): void {
    if (this.cancelLoading) return;
    this.cancelConfirmOpen = false;
  }

  /**
   * Confirmed cancel → ask the backend to cancel the order (releases the reserved stock + discount).
   * On success, clear the checkout flow on the frontend and return to the cart so the customer can
   * start over cleanly. A 409 (order already past the cancellable window) surfaces as a notification;
   * the order's real outcome will still be reflected by the completed-step polling.
   */
  onConfirmCancel(): void {
    if (this.cancelLoading) return;
    this.cancelLoading = true;
    this._accessoryService.cancelAccessoryOrder(this.encodedOrderId).subscribe({
      next: () => {
        this.cancelLoading = false;
        this.cancelConfirmOpen = false;
        this._checkoutService.clear();
        this._navigateBackToCart();
      },
      error: (err: HttpErrorResponse) => {
        this.cancelLoading = false;
        this.cancelConfirmOpen = false;
        this._notification.showHttpErrorNotification(err);
      },
    });
  }

  /** Return to the show's product page and reopen the cart popup (falls back to the product list). */
  private _navigateBackToCart(): void {
    const accessoryShowId = this._cartQuery.getValue().accessoryShowId;
    const navigation = accessoryShowId
      ? this._router.navigate(['/', routeName.productDetail, accessoryShowId], { queryParams: { openCart: 1 } })
      : this._router.navigate(['/', routeName.product]);
    navigation.then(() => setTimeout(() => window.scrollTo(0, 0), 100));
  }

  /**
   * Mở tab thanh toán NGAY trong click handler (còn user activation) rồi mới gọi `payment/start`.
   * Nếu tab bị chặn thì không gọi API — tránh đẩy đơn sang `PaymentProcessing` và khóa tồn kho trong
   * khi khách không hề thấy trang thanh toán.
   */
  startPayment(): void {
    if (!this.paymentMethod) {
      this._notification.showErrorNotification('Vui lòng chọn phương thức thanh toán.');
      return;
    }
    if (this.createPaymentLoading) return;

    // Đọc cờ đã dò sẵn: mọi `await` trước `window.open` đều làm mất user activation.
    if (this._blockerDetected && !this._blockerWarningAcknowledged) {
      this._blockerWarningAcknowledged = true;
      void this._refreshBlockerState();
      this._notification.showBlockingWarning(
        'Trình duyệt của bạn đang bật tiện ích chặn quảng cáo/popup, có thể làm cửa sổ thanh toán ' +
          'không mở được. Vui lòng tắt rồi bấm Thanh toán lại. Nếu vẫn muốn tiếp tục, hãy bấm ' +
          'Thanh toán một lần nữa.',
      );
      return;
    }

    const paymentWindow = this._paymentWindowService.openPlaceholder();
    if (!paymentWindow) {
      this.popupBlockedOpen = true;
      return;
    }

    this.createPaymentLoading = true;
    this._requestPaymentUrl({
      onSuccess: (redirectUrl) => {
        // Kể cả khi khách đã đóng tab chờ (navigateTo trả false) vẫn sang bước "Hoàn tất" với URL này:
        // đơn đã ở `PaymentProcessing` nên gọi lại `payment/start` bị 404 và "Hủy đơn" bị 409. Bước
        // "Hoàn tất" có nút "Mở lại trang thanh toán" dùng đúng URL đã lưu.
        this._paymentWindowService.navigateTo(paymentWindow, redirectUrl);
        this.createPaymentLoading = false;
        this._checkoutService.goToPaymentTracking(redirectUrl);
      },
      onError: () => this._paymentWindowService.close(paymentWindow),
    });
  }

  /** Khách bấm tiếp tục ở màn hình bị chặn popup — chuyển thẳng tab hiện tại, không thể bị chặn. */
  continuePaymentSameTab(): void {
    if (this.createPaymentLoading) return;

    this.popupBlockedOpen = false;
    this.createPaymentLoading = true;
    this._requestPaymentUrl({
      onSuccess: (redirectUrl) => {
        this._checkoutService.goToPaymentTracking(redirectUrl);
        this._paymentWindowService.redirectSameTab(redirectUrl);
      },
    });
  }

  closePopupBlocked(): void {
    this.popupBlockedOpen = false;
  }

  private _requestPaymentUrl(handlers: { onSuccess: (redirectUrl: string) => void; onError?: () => void }): void {
    const body: StartPaymentRequest = {
      paymentId: this._paymentId,
      paymentType: this.paymentMethod!,
    };
    this._paymentService.startPayment(body).subscribe({
      next: (redirectUrl) => handlers.onSuccess(redirectUrl),
      error: (err: HttpErrorResponse) => {
        handlers.onError?.();
        this.createPaymentLoading = false;
        this._notification.showHttpErrorNotification(err);
      },
    });
  }

  private async _refreshBlockerState(): Promise<void> {
    const blocker = await this._blockerDetectionService.check();
    this._blockerDetected = blocker.adBlocker || blocker.inAppWebView;
    if (!this._blockerDetected) {
      this._blockerWarningAcknowledged = false;
    }
  }
}
