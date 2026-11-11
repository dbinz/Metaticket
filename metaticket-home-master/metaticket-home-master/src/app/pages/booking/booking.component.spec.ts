import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingComponent } from './booking.component';
import { BookingQuery, BookingState } from 'src/app/lib/stores';
import {
  LocalStorageService,
  NavigationService,
  NotificationService,
  ShowService,
  TicketService,
} from 'src/app/lib/services';
import { ShowResponse } from 'src/app/lib/entities';
import { EdType, SessionStatus, TicketReservationStatus } from 'src/app/lib/enums';

describe('BookingComponent', () => {
  let component: BookingComponent;
  let fixture: ComponentFixture<BookingComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookingComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

describe('BookingComponent — resuming a saved booking on reload', () => {
  let component: BookingComponent;
  let ticketServiceSpy: jasmine.SpyObj<TicketService>;
  let storage: LocalStorageService;
  let query: BookingQuery;

  const show = {
    id: 'show-1',
    sessions: [{ id: 'sess-1', status: SessionStatus.Open, edType: EdType.SeatPlanner, requireDelivery: false }],
  } as unknown as ShowResponse;

  /** Saved when the customer reached the payment step: hold res-1 with order order-1 waiting for payment. */
  const paymentStep = {
    currentIndex: 2,
    showId: 'show-1',
    sessionId: 'sess-1',
    reservationId: 'res-1',
    listSelectedTickets: [],
    tentativePrice: { priceTotal: 100000, priceDiscount: 0, shipPrice: 0 },
    orderId: 'order-1',
    code: 'MT0001',
    paymentId: 'pay-1',
    requireDelivery: false,
    promotionCode: '',
    isStartedPayment: false,
  } as BookingState;

  const httpError = (status: number, contentType = 'text/plain; charset=UTF-8'): HttpErrorResponse =>
    new HttpErrorResponse({ status, headers: new HttpHeaders().set('content-type', contentType) });

  beforeEach(async () => {
    localStorage.clear();
    ticketServiceSpy = jasmine.createSpyObj<TicketService>('TicketService', ['getSingleReservation']);
    const showServiceSpy = jasmine.createSpyObj<ShowService>('ShowService', ['getShowDetail']);
    showServiceSpy.getShowDetail.and.returnValue(of(show));

    await TestBed.configureTestingModule({
      imports: [BookingComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { params: {} },
            queryParamMap: of(convertToParamMap({ show: 'show-1', session: 'sess-1' })),
          },
        },
        { provide: ShowService, useValue: showServiceSpy },
        { provide: TicketService, useValue: ticketServiceSpy },
        {
          provide: NavigationService,
          useValue: jasmine.createSpyObj<NavigationService>('NavigationService', ['navigateToNotFound']),
        },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj<NotificationService>('NotificationService', ['showWarningNotification']),
        },
      ],
    })
      // Only the resume logic is under test; the step components need the whole app around them.
      .overrideComponent(BookingComponent, { set: { template: '' } })
      .compileComponents();

    storage = TestBed.inject(LocalStorageService);
    query = TestBed.inject(BookingQuery);
    storage.saveBookingSession(paymentStep);
    component = TestBed.createComponent(BookingComponent).componentInstance;
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('resumes at the payment step while the hold is live', () => {
    ticketServiceSpy.getSingleReservation.and.returnValue(
      of({
        id: 'res-1',
        showId: 'show-1',
        sessionId: 'sess-1',
        expireInS: 300,
        status: TicketReservationStatus.Reserved,
      }),
    );

    component.ngOnInit();

    expect(query.getValue().currentIndex).toBe(2);
    expect(query.getValue().orderId).toBe('order-1');
    expect(component.error).toBeUndefined();
  });

  it('resumes the saved progress with the show settings just loaded, not the ones saved with it', () => {
    // Saved while the session still turned the lone-seat rule on; an admin has turned it off since.
    const savedShow = {
      ...show,
      sessions: [{ ...show.sessions[0], preventSingleSeatGap: true }],
    } as unknown as ShowResponse;
    storage.saveBookingSession({ ...paymentStep, showDetail: savedShow });
    ticketServiceSpy.getSingleReservation.and.returnValue(
      of({
        id: 'res-1',
        showId: 'show-1',
        sessionId: 'sess-1',
        expireInS: 300,
        status: TicketReservationStatus.Reserved,
      }),
    );

    component.ngOnInit();

    expect(query.getValue().showDetail?.sessions[0].preventSingleSeatGap).toBeUndefined();
    expect(query.getValue().reservationId).toBe('res-1');
    expect(query.getValue().orderId).toBe('order-1');
    expect(query.getValue().currentIndex).toBe(2);
  });

  const transientFailures: [string, HttpErrorResponse][] = [
    ['request cut by the reload (status 0)', new HttpErrorResponse({ status: 0 })],
    ['rate limited (429)', httpError(429)],
    ['service down (503)', httpError(503)],
    ['gateway/CDN HTML page (403)', httpError(403, 'text/html')],
  ];
  for (const [label, failure] of transientFailures) {
    it(`keeps the saved booking instead of restarting at ticket selection: ${label}`, () => {
      ticketServiceSpy.getSingleReservation.and.returnValue(throwError(() => failure));

      component.ngOnInit();

      expect(storage.getBookingSession('show-1', 'sess-1')?.reservationId).toBe('res-1');
      expect(component.error).toBe(failure);
      expect(component.isLoading).toBeFalse();
    });
  }

  it('starts over only when the service says the hold is gone (404)', () => {
    ticketServiceSpy.getSingleReservation.and.returnValue(throwError(() => httpError(404)));

    component.ngOnInit();

    expect(storage.getBookingSession('show-1', 'sess-1')).toBeNull();
    expect(query.getValue().currentIndex).toBe(0);
    expect(query.getValue().reservationId).toBe('');
    expect(component.error).toBeUndefined();
  });
});
