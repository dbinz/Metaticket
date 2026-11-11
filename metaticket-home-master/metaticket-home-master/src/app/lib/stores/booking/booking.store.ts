import { Store, StoreConfig } from '@datorama/akita';
import { Injectable } from '@angular/core';
import { ShowResponse, TentativePriceResponse } from 'src/app/lib/entities';
import { SeatPlanTriggerItem } from 'src/app/lib/dto';

export interface BookingState {
  currentIndex: number;
  showId: string;
  showDetail?: ShowResponse;
  sessionId: string;
  reservationId: string;
  listSelectedTickets: SeatPlanTriggerItem[];
  tentativePrice: TentativePriceResponse;
  orderId: string;
  requireDelivery: boolean;
  paymentId?: string;
  code?: string;
  promotionCode: string;
  isStartedPayment: boolean;
  /** URL cổng thanh toán đang dùng — giữ lại để khách mở lại được khi lỡ đóng tab thanh toán. */
  paymentRedirectUrl?: string;
  /** "YYYY-MM-DD" (Vietnam) chosen for a CALENDAR session; undefined for normal sessions. */
  visitDate?: string;
}

@Injectable({ providedIn: 'root' })
@StoreConfig({ name: 'booking' })
export class BookingStore extends Store<BookingState> {
  constructor() {
    const initialState: BookingState = {
      currentIndex: 0,
      showId: '',
      sessionId: '',
      reservationId: '',
      listSelectedTickets: [],
      tentativePrice: {
        priceTotal: 0,
        priceDiscount: 0,
        shipPrice: 0,
      },
      requireDelivery: false,
      orderId: '',
      promotionCode: '',
      paymentId: '',
      isStartedPayment: false,
      paymentRedirectUrl: '',
    };

    super(initialState);
  }
}
