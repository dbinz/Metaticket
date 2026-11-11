import { Component, OnDestroy, OnInit } from '@angular/core';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { appIcons } from 'src/app/lib/theme';
import { ButtonComponent, ImageComponent, LoadingComponent } from 'src/app/lib/components';
import { FormatPricePipe, SecondsToTimePipe } from 'src/app/lib/pipes';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { StartPaymentRequest, TentativePriceResponse } from 'src/app/lib/entities';
import {
  BlockerDetectionService,
  NotificationService,
  PaymentService,
  PaymentWindowService,
  TicketService,
} from 'src/app/lib/services';
import { PaymentStatus, PaymentType } from 'src/app/lib/enums';
import { BookingTicketItemComponent } from '../booking-ticket-item/booking-ticket-item.component';
import { BookingQuery, BookingService } from 'src/app/lib/stores';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { SeatPlanTriggerItem } from 'src/app/lib/dto';
import { TicketReservationStatus } from 'src/app/lib/enums/ticket-reservation-status';
import { HttpErrorResponse } from '@angular/common/http';
import { listPaymentMethods } from 'src/app/lib/constants/payment-methods';
import { escapeHtml, isReservationGoneError, serverErrorText } from 'src/app/lib/utils';

const RESERVATION_LOAD_FAILED_MESSAGE = 'Không tải được thời gian giữ vé. Vui lòng tải lại trang.';

@Component({
  selector: 'booking-payment',
  standalone: true,
  imports: [
    NzTypographyComponent,
    NzModalModule,
    NzIconModule,
    NgForOf,
    NgTemplateOutlet,
    ImageComponent,
    LoadingComponent,
    NgIf,
    ButtonComponent,
    FormatPricePipe,
    NzDividerComponent,
    BookingTicketItemComponent,
    SecondsToTimePipe,
    NzDrawerModule,
  ],
  templateUrl: './booking-payment.component.html',
})
export class BookingPaymentComponent implements OnInit, OnDestroy {
  private readonly _paymentId: string;
  protected readonly code: string;
  readonly listTickets: SeatPlanTriggerItem[];
  readonly tentativePrice: TentativePriceResponse;
  protected readonly paymentType = PaymentType;
  protected readonly appIcons = appIcons;

  bottomSheetVisible = false;
  isLoading = false;
  modalVisible = false;
  modalErrorVisible = false;
  modalFailVisible = false;
  modalPopupBlockedVisible = false;
  createPaymentLoading = false;
  showCancelAfterDelay = false;
  cancelConfirmVisible = false;
  paymentMethod: PaymentType | null = null;
  expireInS = 0;
  expireTimeInterval?: number;
  attempt = 1;
  paymentMethods = [listPaymentMethods.find((m) => m.code == PaymentType.QrCode)];

  private _paymentRedirectUrl = '';
  /** Tab cổng thanh toán đang mở, để đóng khi khách bấm "Tôi chưa thanh toán". Mất khi reload trang. */
  private _paymentWindow: Window | null = null;
  private _blockerDetected = false;
  private _blockerWarningAcknowledged = false;
  private _statusPollTimeout?: number;
  private _cancelDelayTimeout?: number;

  constructor(
    private _ticketService: TicketService,
    private _paymentService: PaymentService,
    private _paymentWindowService: PaymentWindowService,
    private _blockerDetectionService: BlockerDetectionService,
    private _notificationService: NotificationService,
    private _bookingQuery: BookingQuery,
    private _bookingService: BookingService,
  ) {
    const state = this._bookingQuery.getValue();
    this._paymentId = state.paymentId!;
    this._paymentRedirectUrl = state.paymentRedirectUrl ?? '';
    this.code = state.code ?? '';
    this.listTickets = state.listSelectedTickets;
    this.tentativePrice = state.tentativePrice;
  }

  onOpenBottomSheet(): void {
    this.bottomSheetVisible = true;
  }

  onCloseBottomSheet(): void {
    this.bottomSheetVisible = false;
  }

  onSelectPayment(code: PaymentType): void {
    if (this.createPaymentLoading) return;
    this.paymentMethod = code;
  }

  private _getSingleReservation(): void {
    this.isLoading = true;
    const state = this._bookingQuery.getValue();
    this._ticketService.getSingleReservation(state.reservationId).subscribe({
      next: (response) => {
        if (response.status == TicketReservationStatus.Reserved) {
          this.isLoading = false;
          const currentTime = Date.now();
          const expireTime = currentTime + response.expireInS * 1000;
          this._startCountdown(expireTime);
        } else {
          this.isLoading = false;
          this._notificationService.showErrorNotification(response.message ?? 'Chọn vé thất bại, vui lòng thử lại sau');
          this.onGoBack();
        }
      },
      error: (error: unknown) => {
        this.isLoading = false;
        if (isReservationGoneError(error)) {
          this._bookingService.expireBookingSession();
          return;
        }
        // Transient failure: the hold and its order are still live, so stay here; paying is checked server-side.
        this._notificationService.showErrorNotification(RESERVATION_LOAD_FAILED_MESSAGE);
      },
    });
  }

  private _startCountdown(expireTime: number): void {
    // eslint-disable-next-line @typescript-eslint/explicit-function-return-type
    const updateCountdown = () => {
      const currentTime = Date.now();
      const remainingTime = Math.max(0, expireTime - currentTime);

      this.expireInS = Math.floor(remainingTime / 1000);
      if (remainingTime === 0) {
        this._bookingService.expireBookingSession();
        clearInterval(this.expireTimeInterval);
      }
    };

    updateCountdown();
    this.expireTimeInterval = window.setInterval(updateCountdown, 1000);
  }

  onGoBack(): void {
    this._bookingService.onPreviousIndex();
  }

  /**
   * Mở tab thanh toán NGAY trong click handler (còn user activation) rồi mới gọi `payment/start`.
   * Nếu tab bị popup blocker chặn thì không gọi API — vì backend sẽ đẩy đơn sang `PaymentProcessing`
   * và giữ ghế trong khi khách không có cách nào thanh toán, và hiện chưa có cơ chế nhả ghế đó.
   */
  startPayment(): void {
    if (this.createPaymentLoading) return;

    // Cảnh báo trước khi chạm tới backend. Đọc cờ đã dò sẵn thay vì dò lại tại đây: mọi lệnh `await`
    // trước `window.open` đều làm mất user activation và khiến trình duyệt chặn tab thanh toán.
    if (this._blockerDetected && !this._blockerWarningAcknowledged) {
      this._blockerWarningAcknowledged = true;
      void this._refreshBlockerState();
      this._notificationService.showBlockingWarning(
        'Trình duyệt của bạn đang bật tiện ích chặn quảng cáo/popup, có thể làm cửa sổ thanh toán ' +
          'không mở được. Vui lòng tắt rồi bấm Thanh toán lại. Nếu vẫn muốn tiếp tục, hãy bấm ' +
          'Thanh toán một lần nữa.',
      );
      return;
    }

    const paymentWindow = this._paymentWindowService.openPlaceholder();
    if (!paymentWindow) {
      this.modalPopupBlockedVisible = true;
      return;
    }

    this.createPaymentLoading = true;
    this._requestPaymentUrl({
      onSuccess: (redirectUrl) => {
        // Kể cả khi khách đã đóng tab chờ (navigateTo trả false) vẫn phải vào màn chờ với URL này:
        // backend đã chuyển đơn sang `PaymentProcessing`, gọi lại `payment/start` sẽ bị 404. Màn chờ có
        // nút "Mở lại trang thanh toán" dùng đúng URL đã lưu.
        this._paymentWindow = this._paymentWindowService.navigateTo(paymentWindow, redirectUrl) ? paymentWindow : null;
        this._enterPaymentProcessing(redirectUrl);
      },
      onError: () => this._paymentWindowService.close(paymentWindow),
    });
  }

  /** Khách bấm tiếp tục ở màn hình bị chặn popup — chuyển thẳng tab hiện tại, không thể bị chặn. */
  continuePaymentSameTab(): void {
    if (this.createPaymentLoading) return;

    this.modalPopupBlockedVisible = false;
    this.createPaymentLoading = true;
    this._requestPaymentUrl({
      onSuccess: (redirectUrl) => {
        this._bookingService.startPayment(redirectUrl);
        this._paymentWindowService.redirectSameTab(redirectUrl);
      },
    });
  }

  /** Mở lại cổng thanh toán bằng URL đã lưu (khách lỡ đóng tab). Đang trong click nên không bị chặn. */
  reopenPaymentWindow(): void {
    if (!this._paymentRedirectUrl) return;

    const paymentWindow = this._paymentWindowService.openPlaceholder();
    if (!this._paymentWindowService.navigateTo(paymentWindow, this._paymentRedirectUrl)) {
      this._paymentWindowService.redirectSameTab(this._paymentRedirectUrl);
      return;
    }
    this._paymentWindow = paymentWindow;
  }

  /** Bước 1 của nút "Tôi chưa thanh toán": nói rõ hệ quả trước khi bỏ màn chờ. */
  requestCancelPendingPayment(): void {
    this.cancelConfirmVisible = true;
  }

  dismissCancelPendingPayment(): void {
    this.cancelConfirmVisible = false;
  }

  /**
   * Cho khách thoát khỏi màn hình chờ khi họ chưa thanh toán.
   * Chỉ reset phía client: luồng vé chưa có API hủy đơn cho khách hàng, nên ghế vẫn do backend giữ
   * cho tới khi job phía server xử lý. Link thanh toán cũ cũng vẫn dùng được tới lúc đó, nên đóng tab
   * cổng thanh toán (nếu còn giữ được) để khách không lỡ trả tiền cho đơn cũ sau khi đã đặt đơn mới.
   */
  cancelPendingPayment(): void {
    this._stopStatusPolling();
    this._paymentWindowService.close(this._paymentWindow);
    this._paymentWindow = null;
    this.cancelConfirmVisible = false;
    this.modalVisible = false;
    this.createPaymentLoading = false;
    this._bookingService.resetPaymentState();
    this._bookingService.expireBookingSession();
  }

  closePopupBlockedModal(): void {
    this.modalPopupBlockedVisible = false;
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
        this._notificationService.showErrorNotification(
          escapeHtml(serverErrorText(err) || 'Không thể bắt đầu thanh toán. Vui lòng thử lại.'),
        );
        this.createPaymentLoading = false;
      },
    });
  }

  /** Gọi ngay khi `payment/start` thành công — từ đây đơn đã ở `PaymentProcessing`, không gọi lại API. */
  private _enterPaymentProcessing(redirectUrl: string): void {
    this._paymentRedirectUrl = redirectUrl;
    this._bookingService.startPayment(redirectUrl);

    if (this.expireTimeInterval) {
      clearInterval(this.expireTimeInterval);
    }
    this.expireInS = 0;
    this.cancelConfirmVisible = false;
    this.modalVisible = true;
    this._scheduleCancelOption();
    this._getPaymentStatus(this._paymentId);
  }

  private async _refreshBlockerState(): Promise<void> {
    const blocker = await this._blockerDetectionService.check();
    this._blockerDetected = blocker.adBlocker || blocker.inAppWebView;
    if (!this._blockerDetected) {
      // Khách đã tắt tiện ích — trả lại cảnh báo cho lần thanh toán sau.
      this._blockerWarningAcknowledged = false;
    }
  }

  private _scheduleCancelOption(): void {
    this._cancelDelayTimeout = window.setTimeout(() => {
      this.showCancelAfterDelay = true;
    }, 15 * 1000);
  }

  private _stopStatusPolling(): void {
    if (this._statusPollTimeout) {
      clearTimeout(this._statusPollTimeout);
      this._statusPollTimeout = undefined;
    }
    if (this._cancelDelayTimeout) {
      clearTimeout(this._cancelDelayTimeout);
      this._cancelDelayTimeout = undefined;
    }
  }

  private _getPaymentStatus(paymentId: string): void {
    this._paymentService.getPaymentStatus(paymentId).subscribe({
      next: (response) => {
        if (response.status === PaymentStatus.Failed || response.status === PaymentStatus.Cancelled) {
          this._stopStatusPolling();
          this.createPaymentLoading = false;
          this.modalVisible = false;
          this.modalFailVisible = true;
          // Reset payment state khi payment failed hoặc cancelled
          this._bookingService.resetPaymentState();
          return;
        }
        if (response.status === PaymentStatus.Succeed) {
          this._stopStatusPolling();
          this._bookingService.bookingCompleted();
          return;
        }
        // Continue to load payment status cause have not got the solved status.
        this._statusPollTimeout = window.setTimeout(() => {
          this._getPaymentStatus(paymentId);
        }, 3 * 1000);
        return;
      },
      error: () => {
        if (this.attempt < 5) {
          this._statusPollTimeout = window.setTimeout(() => {
            this._getPaymentStatus(paymentId);
            this.attempt = this.attempt + 1;
          }, 3 * 1000);
        } else {
          this._stopStatusPolling();
          this.createPaymentLoading = false;
          this.modalVisible = false;
          this.modalErrorVisible = true;
          // Reset payment state khi có lỗi trong quá trình get payment status
          this._bookingService.resetPaymentState();
        }
      },
    });
  }

  backToBooking(): void {
    this.modalFailVisible = false;
    this.modalErrorVisible = false;
    this._bookingService.expireBookingSession();
  }

  ngOnInit(): void {
    void this._refreshBlockerState();

    const state = this._bookingQuery.getValue();
    const session = state?.showDetail?.sessions?.find((s) => s.id === state.sessionId);
    if (session) {
      this.paymentMethods = listPaymentMethods.filter((m) => session.paymentMethods.includes(m.code));
    }
    if (state.isStartedPayment) {
      this.modalVisible = true;
      this._scheduleCancelOption();
      this._getPaymentStatus(state.paymentId!);
      return;
    }
    this._getSingleReservation();
  }

  ngOnDestroy(): void {
    if (this.expireTimeInterval) {
      clearInterval(this.expireTimeInterval);
    }
    this._stopStatusPolling();
  }
}
