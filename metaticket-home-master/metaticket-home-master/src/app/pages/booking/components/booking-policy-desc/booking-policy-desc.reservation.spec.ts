import { SimpleChange } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import {
  CreateReservationResponse,
  GetSingleReservationResponse,
  TicketResponse,
  TicketsWithVersionResponse,
} from 'src/app/lib/entities';
import { TicketReservationStatus } from 'src/app/lib/enums';
import { NotificationService, OrderService, TicketService } from 'src/app/lib/services';
import { BookingQuery, BookingService } from 'src/app/lib/stores';
import { BookingPolicyDescComponent } from './booking-policy-desc.component';

/**
 * A calendar ticket's reservation belongs to the visit date it was created for. Changing the date while the
 * reservation is still being held must not carry that reservation to the next step under the new date.
 */
describe('BookingPolicyDescComponent — reservation vs visit date change', () => {
  const DATE_A = '2026-11-01';
  const DATE_B = '2026-11-02';
  const RESERVATION_ID = 'R-1';

  let component: BookingPolicyDescComponent;
  let ticketService: jasmine.SpyObj<TicketService>;
  let bookingService: jasmine.SpyObj<BookingService>;
  let notificationService: jasmine.SpyObj<NotificationService>;

  const reservation = (status: TicketReservationStatus): GetSingleReservationResponse => ({
    id: RESERVATION_ID,
    showId: 'show-1',
    sessionId: 'session-1',
    expireInS: 600,
    status,
  });

  const ticket = {
    id: 'ticket-a',
    sid: 'VIP',
    priceCat: 'VIP',
    basePrice: 100_000,
    remCap: 10,
  } as TicketResponse;

  beforeEach(() => {
    ticketService = jasmine.createSpyObj<TicketService>('TicketService', [
      'getAllTicket',
      'getTicketByTime',
      'createReservation',
      'reserveReservation',
      'getSingleReservation',
      'releaseReservation',
    ]);
    ticketService.getAllTicket.and.returnValue(of({ tickets: [] } as unknown as TicketsWithVersionResponse));
    ticketService.createReservation.and.returnValue(of({ reservationId: RESERVATION_ID } as CreateReservationResponse));
    ticketService.reserveReservation.and.returnValue(of({}));
    ticketService.getSingleReservation.and.returnValues(
      of(reservation(TicketReservationStatus.Reserving)),
      of(reservation(TicketReservationStatus.Reserved)),
    );
    ticketService.releaseReservation.and.returnValue(of({}));

    bookingService = jasmine.createSpyObj<BookingService>('BookingService', ['bookingSeatMap']);
    notificationService = jasmine.createSpyObj<NotificationService>('NotificationService', ['showErrorNotification']);
    const orderService = jasmine.createSpyObj<OrderService>('OrderService', ['tentativePrice']);
    orderService.tentativePrice.and.returnValue(of({ priceTotal: 100_000, priceDiscount: 0, shipPrice: 0 }));

    TestBed.configureTestingModule({
      imports: [BookingPolicyDescComponent],
      providers: [
        { provide: TicketService, useValue: ticketService },
        { provide: OrderService, useValue: orderService },
        { provide: NotificationService, useValue: notificationService },
        { provide: BookingService, useValue: bookingService },
        {
          provide: BookingQuery,
          useValue: { getValue: (): unknown => ({ showId: 'show-1', sessionId: 'session-1' }) },
        },
      ],
    });
    // Only the class logic is under test; skip the template so its child components need no providers.
    TestBed.overrideComponent(BookingPolicyDescComponent, { set: { imports: [], template: '' } });
    component = TestBed.createComponent(BookingPolicyDescComponent).componentInstance;
    component.visitDate = DATE_A;
    component.onChangeQuantity(2, ticket);
  });

  it('shows the refusal body as escaped text instead of the HTTP status label', () => {
    ticketService.createReservation.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict', error: 'Không đủ vé <b>VIP</b>' })),
    );

    component.createReservation();

    expect(component.createReservationLoading).toBeFalse();
    expect(ticketService.reserveReservation.calls.count()).toBe(0);
    expect(notificationService.showErrorNotification.calls.allArgs()).toEqual([['Không đủ vé &lt;b&gt;VIP&lt;/b&gt;']]);
  });

  it('releases the reservation instead of moving on when the date changed while reserving', fakeAsync(() => {
    component.createReservation();

    component.visitDate = DATE_B;
    component.ngOnChanges({ visitDate: new SimpleChange(DATE_A, DATE_B, false) });
    tick(1000);

    // Assert through `.calls`: passing the spied method itself trips @typescript-eslint/unbound-method.
    expect(ticketService.getSingleReservation.calls.count()).toBe(2);
    expect(bookingService.bookingSeatMap.calls.count()).toBe(0);
    expect(ticketService.releaseReservation.calls.allArgs()).toEqual([[RESERVATION_ID]]);
    expect(notificationService.showErrorNotification.calls.allArgs()).toEqual([
      ['Bạn đã đổi ngày tham quan, vui lòng chọn lại vé'],
    ]);
    expect(component.createReservationLoading).toBeFalse();
  }));

  // Back on the same date the selection was still reset, so comparing date values alone would book an empty order.
  it('releases the reservation when the date went A -> B -> A while reserving', fakeAsync(() => {
    component.createReservation();

    component.visitDate = DATE_B;
    component.ngOnChanges({ visitDate: new SimpleChange(DATE_A, DATE_B, false) });
    component.visitDate = DATE_A;
    component.ngOnChanges({ visitDate: new SimpleChange(DATE_B, DATE_A, false) });
    tick(1000);

    expect(ticketService.getSingleReservation.calls.count()).toBe(2);
    expect(bookingService.bookingSeatMap.calls.count()).toBe(0);
    expect(ticketService.releaseReservation.calls.allArgs()).toEqual([[RESERVATION_ID]]);
    expect(notificationService.showErrorNotification.calls.allArgs()).toEqual([
      ['Bạn đã đổi ngày tham quan, vui lòng chọn lại vé'],
    ]);
    expect(component.createReservationLoading).toBeFalse();
  }));

  it('moves on with the held reservation when the date did not change', fakeAsync(() => {
    component.createReservation();
    tick(1000);

    expect(ticketService.releaseReservation.calls.count()).toBe(0);
    expect(bookingService.bookingSeatMap.calls.allArgs()).toEqual([
      [
        RESERVATION_ID,
        [jasmine.objectContaining({ id: 'ticket-a', quantity: 2 })],
        { priceTotal: 100_000, priceDiscount: 0, shipPrice: 0 },
        '',
      ],
    ]);
    expect(component.createReservationLoading).toBeFalse();
  }));
});
