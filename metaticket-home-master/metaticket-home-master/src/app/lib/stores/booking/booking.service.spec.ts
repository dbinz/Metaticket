import { TestBed } from '@angular/core/testing';
import { BookingService } from './booking.service';
import { BookingQuery } from './booking.query';
import { LocalStorageService } from '../../services';

describe('BookingService.bookingSeatMap', () => {
  let service: BookingService;
  let query: BookingQuery;
  let storage: LocalStorageService;

  beforeEach(() => {
    localStorage.clear();
    service = TestBed.inject(BookingService);
    query = TestBed.inject(BookingQuery);
    storage = TestBed.inject(LocalStorageService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('drops the order of an earlier hold so the information step cannot reuse it for the new one', () => {
    // Customer went back to ticket selection without paying and picked again: the store still has the old order.
    service.initial({
      ...query.getValue(),
      currentIndex: 0,
      showId: 'show-1',
      sessionId: 'sess-1',
      reservationId: 'res-old',
      orderId: 'order-old',
      code: 'MT0001',
      paymentId: 'pay-old',
      isStartedPayment: true,
      paymentRedirectUrl: 'https://pay.example/old',
    });

    service.bookingSeatMap('res-new', [], { priceTotal: 1000, priceDiscount: 0, shipPrice: 0 }, '');

    const state = query.getValue();
    expect(state.reservationId).toBe('res-new');
    expect(state.currentIndex).toBe(1);
    expect([state.orderId, state.code, state.paymentId, state.paymentRedirectUrl]).toEqual(['', '', '', '']);
    expect(state.isStartedPayment).toBeFalse();
    const saved = storage.getBookingSession('show-1', 'sess-1');
    expect(saved?.reservationId).toBe('res-new');
    expect([saved?.orderId, saved?.paymentId, saved?.isStartedPayment]).toEqual(['', '', false]);
  });
});
