import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient, withFetch } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Observable, of, throwError } from 'rxjs';
import { BookingInformationComponent } from './booking-information.component';
import { AuthService, NotificationService, OrderService, TicketService } from 'src/app/lib/services';
import { BookingQuery, BookingService, BookingState } from 'src/app/lib/stores';
import { CreateOrderResponse, GetSingleReservationResponse, OrderSummaryResponse } from 'src/app/lib/entities';
import { TicketReservationStatus } from 'src/app/lib/enums';

const QUOTA_TAKEN_MESSAGE = 'Ưu đãi vừa hết lượt sử dụng. Vui lòng chọn vé và đặt lại.';
const BAD_EMAIL_MESSAGE = 'Email người thanh toán không chính xác';
const RESTART_BOOKING_MESSAGE = 'Đặt vé không thành công. Vui lòng chọn vé và đặt lại.';
const ORDER_PAYING_MESSAGE = 'Trạng thái yêu cầu đặt vé không hợp lệ: OrderPaying';
const PAYING_ELSEWHERE_MESSAGE =
  'Đơn hàng này đang được thanh toán ở cửa sổ khác. Vui lòng hoàn tất thanh toán ở cửa sổ đó.';

function reservation(status: TicketReservationStatus): GetSingleReservationResponse {
  return { id: 'res-1', showId: 'show-1', sessionId: 'sess-1', expireInS: 600, status };
}

function badRequest(body: unknown): Observable<never> {
  return throwError(() => new HttpErrorResponse({ status: 400, error: body }));
}

describe('BookingInformationComponent — createOrder refused (MTH-5 R1)', () => {
  let component: BookingInformationComponent;
  let fixture: ComponentFixture<BookingInformationComponent>;
  let orderServiceSpy: jasmine.SpyObj<OrderService>;
  let ticketServiceSpy: jasmine.SpyObj<TicketService>;
  let notificationSpy: jasmine.SpyObj<NotificationService>;
  let bookingServiceSpy: jasmine.SpyObj<BookingService>;
  /** What `ticket/reservation/fetch` answers after the failed createOrder (the first call is the countdown on init). */
  let reservationAfterFailure: () => Observable<GetSingleReservationResponse>;

  beforeEach(async () => {
    orderServiceSpy = jasmine.createSpyObj<OrderService>('OrderService', [
      'createOrder',
      'createPayment',
      'getCustomFields',
      'generateOdcLink',
    ]);
    orderServiceSpy.getCustomFields.and.returnValue(of([]));
    ticketServiceSpy = jasmine.createSpyObj<TicketService>('TicketService', [
      'getSingleReservation',
      'releaseReservation',
    ]);
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.Reserved));
    let reservationCalls = 0;
    ticketServiceSpy.getSingleReservation.and.callFake(() => {
      reservationCalls++;
      return reservationCalls === 1 ? of(reservation(TicketReservationStatus.Reserved)) : reservationAfterFailure();
    });
    notificationSpy = jasmine.createSpyObj<NotificationService>('NotificationService', [
      'showErrorNotification',
      'showHttpErrorNotification',
      'showBlockingWarning',
    ]);
    bookingServiceSpy = jasmine.createSpyObj<BookingService>('BookingService', [
      'expireBookingSession',
      'onPreviousIndex',
      'bookingInformation',
    ]);
    const bookingQuerySpy = jasmine.createSpyObj<BookingQuery>('BookingQuery', ['getValue']);
    bookingQuerySpy.getValue.and.returnValue({
      currentIndex: 1,
      showId: 'show-1',
      sessionId: 'sess-1',
      reservationId: 'res-1',
      listSelectedTickets: [],
      tentativePrice: { priceTotal: 100000, priceDiscount: 20000, shipPrice: 0 },
      orderId: '',
      requireDelivery: false,
      promotionCode: 'SALE20',
      isStartedPayment: false,
    } as BookingState);
    const authServiceMock = { userProfile$: of(null) } as unknown as AuthService;

    await TestBed.configureTestingModule({
      imports: [BookingInformationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: TicketService, useValue: ticketServiceSpy },
        { provide: NotificationService, useValue: notificationSpy },
        { provide: BookingService, useValue: bookingServiceSpy },
        { provide: BookingQuery, useValue: bookingQuerySpy },
        { provide: AuthService, useValue: authServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingInformationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.purchaseFormGroup.setValue({ name: 'Nguyễn Văn A', phone: '0912345678', email: 'a@example.com' });
  });

  afterEach(() => {
    fixture.destroy();
  });

  it('sends the promotion code chosen at step 1', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest(BAD_EMAIL_MESSAGE));

    component.onSubmit();

    expect(orderServiceSpy.createOrder.calls.mostRecent().args[0].promotionCode).toBe('SALE20');
  });

  it('returns to ticket selection with the server sentence when the code was refused and the hold released', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest(QUOTA_TAKEN_MESSAGE));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.OrderFailed));

    component.onSubmit();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[QUOTA_TAKEN_MESSAGE]]);
    expect(notificationSpy.showErrorNotification.calls.count()).toBe(0);
    // service.order already released the hold; releasing again would only fail.
    expect(ticketServiceSpy.releaseReservation.calls.count()).toBe(0);
  });

  it('shows a refused-code reason as text, never as HTML (the notification renders innerHTML)', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest('Mã <b>SALE20</b> đã hết hạn & không dùng được'));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.OrderFailed));

    component.onSubmit();

    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([
      ['Mã &lt;b&gt;SALE20&lt;/b&gt; đã hết hạn &amp; không dùng được'],
    ]);
  });

  it('falls back to a fixed sentence when the refused response has no text body', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest({ unexpected: true }));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.OrderFailed));

    component.onSubmit();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([
      ['Đặt vé không thành công. Vui lòng chọn vé và đặt lại.'],
    ]);
  });

  it('keeps the customer on the form when the 400 is about their details (hold still Reserved)', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest(BAD_EMAIL_MESSAGE));

    component.onSubmit();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(0);
    expect(notificationSpy.showBlockingWarning.calls.count()).toBe(0);
    expect(notificationSpy.showErrorNotification.calls.allArgs()).toEqual([[BAD_EMAIL_MESSAGE]]);
    expect(component.createOrderLoading).toBeFalse();
  });

  it('stays on the form with escaped server text, or a fixed sentence when the refusal has no text', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest('Email <b>sai</b>'));
    component.onSubmit();
    orderServiceSpy.createOrder.and.returnValue(badRequest({ unexpected: true }));
    component.onSubmit();

    expect(notificationSpy.showErrorNotification.calls.allArgs()).toEqual([
      ['Email &lt;b&gt;sai&lt;/b&gt;'],
      ['Đặt vé không thành công. Vui lòng kiểm tra thông tin và thử lại.'],
    ]);
    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(0);
  });

  it('treats a hold that no longer exists (404) as released', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest(QUOTA_TAKEN_MESSAGE));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      throwError(() => new HttpErrorResponse({ status: 404, error: 'Không tìm thấy reservation' }));

    component.onSubmit();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[QUOTA_TAKEN_MESSAGE]]);
  });

  it('keeps the old behaviour when the hold cannot be checked (5xx/network)', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest(QUOTA_TAKEN_MESSAGE));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      throwError(() => new HttpErrorResponse({ status: 0 }));

    component.onSubmit();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(0);
    expect(notificationSpy.showErrorNotification.calls.allArgs()).toEqual([[QUOTA_TAKEN_MESSAGE]]);
    expect(component.createOrderLoading).toBeFalse();
  });

  it('does not drop the session when the hold already belongs to an order (e.g. a second submit)', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest(ORDER_PAYING_MESSAGE));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.OrderPaying));

    component.onSubmit();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(0);
    expect(notificationSpy.showErrorNotification.calls.allArgs()).toEqual([[ORDER_PAYING_MESSAGE]]);
  });

  it('starts a payment only for an order that has none yet (Created)', () => {
    orderServiceSpy.createOrder.and.returnValue(of({ id: 'order-1', code: 'MT-1', status: 'Created' }));
    orderServiceSpy.createPayment.and.returnValue(of('pay-1'));

    component.onSubmit();

    expect(orderServiceSpy.createPayment.calls.allArgs()).toEqual([[{ orderId: 'order-1' }]]);
    expect(bookingServiceSpy.bookingInformation.calls.allArgs()).toEqual([
      [{ orderId: 'order-1', code: 'MT-1', paymentId: 'pay-1' }],
    ]);
  });

  // R3-01: a second tab on the same hold gets back the order the first tab is already paying.
  for (const status of ['WaitingForPayment', 'PaymentProcessing']) {
    it(`points to the other window instead of failing when the returned order is ${status}`, () => {
      orderServiceSpy.createOrder.and.returnValue(of({ id: 'order-1', code: 'MT-1', status }));

      component.onSubmit();

      expect(orderServiceSpy.createPayment.calls.count()).toBe(0);
      expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[PAYING_ELSEWHERE_MESSAGE]]);
      expect(notificationSpy.showHttpErrorNotification.calls.count()).toBe(0);
      // The booking session lives in localStorage shared with the paying tab: dropping it here would break that tab.
      expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(0);
      expect(bookingServiceSpy.bookingInformation.calls.count()).toBe(0);
      expect(component.createOrderLoading).toBeFalse();
    });
  }

  it('does not check the hold when the order is created', () => {
    const created: CreateOrderResponse = { id: 'order-1', code: 'MT-1', status: 'PaymentPending' };
    orderServiceSpy.createOrder.and.returnValue(of(created));

    component.onSubmit();

    expect(ticketServiceSpy.getSingleReservation.calls.count()).toBe(1);
    expect(bookingServiceSpy.bookingInformation.calls.count()).toBe(1);
    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(0);
  });

  it('falls back to a fixed sentence on 502 nginx HTML error page (T1)', () => {
    orderServiceSpy.createOrder.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 502,
            error: '<html><body><h1>502 Bad Gateway</h1></body></html>',
          }),
      ),
    );
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.Removed));

    component.onSubmit();

    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[RESTART_BOOKING_MESSAGE]]);
    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
  });

  it('falls back to a fixed sentence on 504 non-4xx plain text error (T2)', () => {
    orderServiceSpy.createOrder.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 504, error: 'Gateway Timeout' })),
    );
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.OrderFailed));

    component.onSubmit();

    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[RESTART_BOOKING_MESSAGE]]);
    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
  });

  it('falls back to a fixed sentence when 400 error body starts with HTML (T3)', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest('<html>err</html>'));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.OrderFailed));

    component.onSubmit();

    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[RESTART_BOOKING_MESSAGE]]);
    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
  });

  it('returns to ticket selection with the server sentence when hold is OrderCancelled (T4)', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest(QUOTA_TAKEN_MESSAGE));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.OrderCancelled));

    component.onSubmit();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[QUOTA_TAKEN_MESSAGE]]);
  });

  it('returns to ticket selection with the server sentence when hold is Removing (T5)', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest(QUOTA_TAKEN_MESSAGE));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.Removing));

    component.onSubmit();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[QUOTA_TAKEN_MESSAGE]]);
  });

  it('returns to ticket selection with the server sentence when hold is Removed (T6)', () => {
    orderServiceSpy.createOrder.and.returnValue(badRequest(QUOTA_TAKEN_MESSAGE));
    reservationAfterFailure = (): Observable<GetSingleReservationResponse> =>
      of(reservation(TicketReservationStatus.Removed));

    component.onSubmit();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[QUOTA_TAKEN_MESSAGE]]);
  });
});

/**
 * The app runs HttpClient on fetch (`withFetch()`): a JSON request whose 4xx body is plain text errors with a bare
 * SyntaxError and the body is lost, unlike the string `HttpTestingController` hands back. Goes through the real
 * OrderService + FetchBackend with only `window.fetch` stubbed, so the customer-facing text is the server's.
 */
describe('BookingInformationComponent — createOrder refused over fetch (R1-02)', () => {
  const EXPIRED_MESSAGE = 'Mã đã hết hạn';
  let fixture: ComponentFixture<BookingInformationComponent>;
  let notificationSpy: jasmine.SpyObj<NotificationService>;
  let bookingServiceSpy: jasmine.SpyObj<BookingService>;
  let holdAfterFailure: TicketReservationStatus;

  /** Answers like the gateway: custom fields as JSON, order/create refused with a text/plain 400. */
  function fakeFetch(input: RequestInfo | URL): Promise<Response> {
    const url = input instanceof Request ? input.url : String(input);
    if (url.endsWith('/order/create')) {
      return Promise.resolve(
        new Response(EXPIRED_MESSAGE, { status: 400, headers: [['Content-Type', 'text/plain;charset=UTF-8']] }),
      );
    }
    return Promise.resolve(new Response('[]', { status: 200, headers: [['Content-Type', 'application/json']] }));
  }

  /** Lets the fetch promise chain (response, body reader, subscriber) run to the notification. */
  async function settle(): Promise<void> {
    for (let i = 0; i < 20; i++) {
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
    }
  }

  beforeEach(async () => {
    spyOn(window, 'fetch').and.callFake(fakeFetch);
    const ticketServiceSpy = jasmine.createSpyObj<TicketService>('TicketService', ['getSingleReservation']);
    let reservationCalls = 0;
    ticketServiceSpy.getSingleReservation.and.callFake(() => {
      reservationCalls++;
      return of(reservation(reservationCalls === 1 ? TicketReservationStatus.Reserved : holdAfterFailure));
    });
    notificationSpy = jasmine.createSpyObj<NotificationService>('NotificationService', [
      'showErrorNotification',
      'showBlockingWarning',
    ]);
    bookingServiceSpy = jasmine.createSpyObj<BookingService>('BookingService', ['expireBookingSession']);
    const bookingQuerySpy = jasmine.createSpyObj<BookingQuery>('BookingQuery', ['getValue']);
    bookingQuerySpy.getValue.and.returnValue({
      currentIndex: 1,
      showId: 'show-1',
      sessionId: 'sess-1',
      reservationId: 'res-1',
      listSelectedTickets: [],
      tentativePrice: { priceTotal: 100000, priceDiscount: 20000, shipPrice: 0 },
      orderId: '',
      requireDelivery: false,
      promotionCode: 'SALE20',
      isStartedPayment: false,
    } as BookingState);

    await TestBed.configureTestingModule({
      imports: [BookingInformationComponent],
      providers: [
        provideHttpClient(withFetch()),
        { provide: TicketService, useValue: ticketServiceSpy },
        { provide: NotificationService, useValue: notificationSpy },
        { provide: BookingService, useValue: bookingServiceSpy },
        { provide: BookingQuery, useValue: bookingQuerySpy },
        { provide: AuthService, useValue: { userProfile$: of(null) } as unknown as AuthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingInformationComponent);
    fixture.detectChanges();
    await settle();
    fixture.componentInstance.purchaseFormGroup.setValue({
      name: 'Nguyễn Văn A',
      phone: '0912345678',
      email: 'a@example.com',
    });
  });

  afterEach(() => {
    fixture.destroy();
  });

  it('returns to ticket selection with the server sentence, not the fixed one', async () => {
    holdAfterFailure = TicketReservationStatus.OrderFailed;

    fixture.componentInstance.onSubmit();
    await settle();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(1);
    expect(notificationSpy.showBlockingWarning.calls.allArgs()).toEqual([[EXPIRED_MESSAGE]]);
  });

  it('keeps the customer on the form with the server sentence when the hold is still Reserved', async () => {
    holdAfterFailure = TicketReservationStatus.Reserved;

    fixture.componentInstance.onSubmit();
    await settle();

    expect(bookingServiceSpy.expireBookingSession.calls.count()).toBe(0);
    expect(notificationSpy.showErrorNotification.calls.allArgs()).toEqual([[EXPIRED_MESSAGE]]);
    expect(fixture.componentInstance.createOrderLoading).toBeFalse();
  });
});

describe('BookingInformationComponent — back from the payment step (order already created)', () => {
  let fixture: ComponentFixture<BookingInformationComponent>;
  let orderServiceSpy: jasmine.SpyObj<OrderService>;
  let bookingServiceSpy: jasmine.SpyObj<BookingService>;

  const summary = {
    orderCode: 'MT0001',
    buyerInfo: { name: 'Nguyễn Văn A', phone: '0912345678', email: 'a@example.com' },
  } as OrderSummaryResponse;

  async function setUp(state: Partial<BookingState>, summaryResult: Observable<OrderSummaryResponse>): Promise<void> {
    orderServiceSpy = jasmine.createSpyObj<OrderService>('OrderService', [
      'createOrder',
      'createPayment',
      'getCustomFields',
      'getOrderSummary',
    ]);
    orderServiceSpy.getCustomFields.and.returnValue(of([]));
    orderServiceSpy.getOrderSummary.and.returnValue(summaryResult);
    const ticketServiceSpy = jasmine.createSpyObj<TicketService>('TicketService', ['getSingleReservation']);
    ticketServiceSpy.getSingleReservation.and.returnValue(of(reservation(TicketReservationStatus.Reserved)));
    bookingServiceSpy = jasmine.createSpyObj<BookingService>('BookingService', [
      'expireBookingSession',
      'onPreviousIndex',
      'bookingInformation',
    ]);
    const bookingQuerySpy = jasmine.createSpyObj<BookingQuery>('BookingQuery', ['getValue']);
    bookingQuerySpy.getValue.and.returnValue({
      currentIndex: 1,
      showId: 'show-1',
      sessionId: 'sess-1',
      reservationId: 'res-1',
      listSelectedTickets: [],
      tentativePrice: { priceTotal: 100000, priceDiscount: 0, shipPrice: 0 },
      orderId: '',
      requireDelivery: false,
      promotionCode: '',
      isStartedPayment: false,
      ...state,
    } as BookingState);

    await TestBed.configureTestingModule({
      imports: [BookingInformationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: TicketService, useValue: ticketServiceSpy },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj<NotificationService>('NotificationService', ['showBlockingWarning']),
        },
        { provide: BookingService, useValue: bookingServiceSpy },
        { provide: BookingQuery, useValue: bookingQuerySpy },
        { provide: AuthService, useValue: { userProfile$: of(null) } as unknown as AuthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingInformationComponent);
    fixture.detectChanges();
  }

  const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const formInputs = (): number => (fixture.nativeElement as HTMLElement).querySelectorAll('app-form-input').length;

  afterEach(() => {
    fixture.destroy();
  });

  it('resumes paying the existing order instead of creating it again', async () => {
    await setUp({ orderId: 'order-1', code: 'MT0001', paymentId: 'pay-1' }, of(summary));

    fixture.componentInstance.onSubmit();

    expect(orderServiceSpy.createOrder.calls.count()).toBe(0);
    expect(orderServiceSpy.createPayment.calls.count()).toBe(0);
    expect(bookingServiceSpy.bookingInformation.calls.allArgs()).toEqual([
      [{ orderId: 'order-1', code: 'MT0001', paymentId: 'pay-1' }],
    ]);
  });

  it('shows the details saved on the order read-only, without the editable form', async () => {
    await setUp({ orderId: 'order-1', code: 'MT0001', paymentId: 'pay-1' }, of(summary));

    expect(orderServiceSpy.getOrderSummary.calls.allArgs()).toEqual([['order-1']]);
    expect(orderServiceSpy.getCustomFields.calls.count()).toBe(0);
    expect(formInputs()).toBe(0);
    expect(text()).toContain('MT0001');
    expect(text()).toContain('a@example.com');
  });

  it('still lets the customer continue when the saved details cannot be loaded', async () => {
    await setUp(
      { orderId: 'order-1', code: 'MT0001', paymentId: 'pay-1' },
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    expect(fixture.componentInstance.isLoading).toBeFalse();
    expect(formInputs()).toBe(0);
    expect(text()).toContain('MT0001');

    fixture.componentInstance.onSubmit();

    expect(bookingServiceSpy.bookingInformation.calls.count()).toBe(1);
    expect(orderServiceSpy.createOrder.calls.count()).toBe(0);
  });

  it('creates the order as before when no payment exists yet', async () => {
    await setUp({ orderId: 'order-1', code: 'MT0001', paymentId: '' }, of(summary));
    orderServiceSpy.createOrder.and.returnValue(of({ id: 'order-2', code: 'MT0002', status: 'Created' }));
    orderServiceSpy.createPayment.and.returnValue(of('pay-2'));
    fixture.componentInstance.purchaseFormGroup.setValue({
      name: 'Nguyễn Văn A',
      phone: '0912345678',
      email: 'a@example.com',
    });

    fixture.componentInstance.onSubmit();

    expect(formInputs()).toBeGreaterThan(0);
    expect(orderServiceSpy.getOrderSummary.calls.count()).toBe(0);
    expect(orderServiceSpy.createOrder.calls.count()).toBe(1);
    expect(bookingServiceSpy.bookingInformation.calls.allArgs()).toEqual([
      [{ orderId: 'order-2', code: 'MT0002', paymentId: 'pay-2' }],
    ]);
  });
});
