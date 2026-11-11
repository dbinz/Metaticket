import { Injectable } from '@angular/core';
import { BookingState, BookingStore } from './booking.store';
import { TentativePriceResponse } from 'src/app/lib/entities';
import { SeatPlanTriggerItem } from 'src/app/lib/dto';
import { LocalStorageService } from '../../services';

@Injectable({ providedIn: 'root' })
export class BookingService {
  constructor(
    private _store: BookingStore,
    private _localStorageService: LocalStorageService,
  ) {}

  initial(state: BookingState): void {
    this._store.update(state);
  }

  /**
   * Chọn ngày tham quan cho suất theo lịch. Trạng thái đã lưu của ngày cũ (còn lại khi khách quay về bước chọn vé)
   * bị xóa để tải lại trang không khôi phục nhầm ngày cũ.
   */
  selectVisitDate(visitDate: string): void {
    const state = this._store.getValue();
    if (state.visitDate && state.visitDate !== visitDate) {
      this._localStorageService.removeBookingSession(state.showId, state.sessionId, state.visitDate);
    }
    this._store.update({ visitDate });
  }

  bookingSeatMap(
    reservationId: string,
    listTickets: SeatPlanTriggerItem[],
    tentativePrice: TentativePriceResponse,
    promotionCode: string,
  ): void {
    const state = this._store.getValue();
    // A new hold never has an order yet. The information step reuses a stored order (back from payment), so one left
    // over from an earlier hold must not survive into this one.
    const noOrder: Pick<BookingState, 'orderId' | 'code' | 'paymentId' | 'isStartedPayment' | 'paymentRedirectUrl'> = {
      orderId: '',
      code: '',
      paymentId: '',
      isStartedPayment: false,
      paymentRedirectUrl: '',
    };
    const data: BookingState = {
      ...state,
      ...noOrder,
      reservationId: reservationId,
      tentativePrice: tentativePrice,
      listSelectedTickets: listTickets,
      currentIndex: state.currentIndex + 1,
    };
    this._localStorageService.saveBookingSession(data);

    this._store.update({
      ...noOrder,
      reservationId: reservationId,
      tentativePrice: tentativePrice,
      listSelectedTickets: listTickets,
      promotionCode: promotionCode,
      currentIndex: state.currentIndex + 1,
    });
    setTimeout(() => {
      window.scrollTo(0, 0);
    }, 100);
  }

  bookingInformation({ orderId, code, paymentId }: { orderId: string; code: string; paymentId?: string }): void {
    const state = this._store.getValue();
    const data: BookingState = {
      ...state,
      orderId: orderId,
      currentIndex: state.currentIndex + 1,
      code: code,
      paymentId: paymentId,
      isStartedPayment: false,
      paymentRedirectUrl: '',
    };
    this._localStorageService.saveBookingSession(data);

    this._store.update({
      orderId: orderId,
      currentIndex: state.currentIndex + 1,
      code: code,
      paymentId: paymentId,
    });

    if (!paymentId) {
      // this means the payment is already successful. HACKY. Need to change.
      console.log('Payment already successful. No need to go to payment page.');
      this.startPayment();
      this.bookingCompleted();
    }

    setTimeout(() => {
      window.scrollTo(0, 0);
    }, 100);
  }

  /** [redirectUrl] được lưu lại để khách mở lại cổng thanh toán khi lỡ đóng tab hoặc tab bị unload. */
  startPayment(redirectUrl?: string): void {
    const state = this._store.getValue();
    const data: BookingState = {
      ...state,
      isStartedPayment: true,
      paymentRedirectUrl: redirectUrl,
    };
    this._localStorageService.saveBookingSession(data);

    this._store.update({
      isStartedPayment: true,
      paymentRedirectUrl: redirectUrl,
    });
  }

  expireBookingSession(): void {
    const state = this._store.getValue();
    this._localStorageService.removeBookingSession(state.showId, state.sessionId, state.visitDate);
    this._store.update({ currentIndex: 0 });
    setTimeout(() => {
      window.scrollTo(0, 0);
    }, 100);
  }

  bookingCompleted(): void {
    const state = this._store.getValue();
    this._localStorageService.removeBookingSession(state.showId, state.sessionId, state.visitDate);
    this._store.update((state) => ({ currentIndex: state.currentIndex + 1 }));
    setTimeout(() => {
      window.scrollTo(0, 0);
    }, 100);
  }

  resetPaymentState(): void {
    const state = this._store.getValue();
    const data: BookingState = {
      ...state,
      isStartedPayment: false,
      paymentRedirectUrl: '',
    };
    this._localStorageService.saveBookingSession(data);

    this._store.update({
      isStartedPayment: false,
      paymentRedirectUrl: '',
    });
  }

  onPreviousIndex(): void {
    const state = this._store.getValue();
    const previousIndex = state.currentIndex - 1;
    if (previousIndex === 0) {
      this._localStorageService.removeBookingSession(state.showId, state.sessionId, state.visitDate);
    }

    this._localStorageService.saveBookingSession({ ...state, currentIndex: previousIndex });
    this._store.update({ currentIndex: previousIndex });
    setTimeout(() => {
      window.scrollTo(0, 0);
    }, 100);
  }
}
