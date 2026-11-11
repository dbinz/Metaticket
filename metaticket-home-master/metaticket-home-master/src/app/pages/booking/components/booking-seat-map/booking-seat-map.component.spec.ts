import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Observable, of, Subject, throwError } from 'rxjs';
import { BookingSeatMapComponent } from './booking-seat-map.component';
import { NotificationService, OrderService, ShowService, TicketService } from 'src/app/lib/services';
import { BookingQuery, BookingService, BookingState } from 'src/app/lib/stores';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import {
  PromotionOfferResponse,
  SeatBlocksResponse,
  TentativePriceRequest,
  TentativePriceResponse,
  TicketsWithVersionResponse,
} from 'src/app/lib/entities';
import { PromotionOfferPanelComponent } from 'src/app/lib/components';
import { SeatPlanTriggerItem } from 'src/app/lib/dto';

const OFFER: PromotionOfferResponse = {
  code: 'SALE20',
  title: 'Giảm 20.000đ',
  discountType: 'AMOUNT',
  discountValue: 20000,
};

function seat(sid: string): SeatPlanTriggerItem {
  return { id: `ticket-${sid}`, sid, priceCat: 'VIP', displayName: sid, quantity: 1, basePrice: 100000, remCap: 1 };
}

function priced(total: number, discount: number, code: string, errorMessage?: string): TentativePriceResponse {
  return {
    priceTotal: total,
    priceDiscount: discount,
    shipPrice: 0,
    promotionDetails: [
      { name: code, priceDiscount: discount, type: 'COUPON', error: errorMessage !== undefined, errorMessage },
    ],
  };
}

describe('BookingSeatMapComponent — MTH-5 Promotion Integration', () => {
  let component: BookingSeatMapComponent;
  let fixture: ComponentFixture<BookingSeatMapComponent>;
  let getPromotionOffersSpy: jasmine.Spy;
  let tentativePriceSpy: jasmine.Spy;
  let ticketServiceSpy: jasmine.SpyObj<TicketService>;
  let bookingQuerySpy: jasmine.SpyObj<BookingQuery>;

  beforeEach(async () => {
    const mockPlanner = class {
      initialize(): Promise<void> {
        return Promise.resolve();
      }
      registerListener(): void {}
      updateTicketsStatus(): void {}
      deselectTicket(): void {}
    };
    const metaticketMock: Record<string, unknown> = {};
    metaticketMock['PlannerWrapper'] = mockPlanner;

    const globalObj = window as unknown as Record<string, unknown>;
    globalObj['metaticket'] = metaticketMock;

    getPromotionOffersSpy = jasmine.createSpy('getPromotionOffers').and.returnValue(of([OFFER]));
    tentativePriceSpy = jasmine
      .createSpy('tentativePrice')
      .and.returnValue(of({ priceTotal: 100000, priceDiscount: 0, shipPrice: 0 }));

    const orderServiceMock = {
      getPromotionOffers: getPromotionOffersSpy,
      tentativePrice: tentativePriceSpy,
    } as unknown as OrderService;

    ticketServiceSpy = jasmine.createSpyObj<TicketService>('TicketService', ['getAllTicket', 'getTicketByTime']);
    bookingQuerySpy = jasmine.createSpyObj<BookingQuery>('BookingQuery', ['getValue']);
    const bookingServiceSpy: jasmine.SpyObj<BookingService> = jasmine.createSpyObj<BookingService>('BookingService', [
      'bookingSeatMap',
    ]);
    const notificationServiceSpy: jasmine.SpyObj<NotificationService> = jasmine.createSpyObj<NotificationService>(
      'NotificationService',
      ['showErrorNotification'],
    );

    const state = {
      showId: 'show-seat-1',
      sessionId: 'sess-seat-1',
      showDetail: {
        sessions: [{ id: 'sess-seat-1', name: 'Suất 1' }],
      },
    } as unknown as BookingState;
    bookingQuerySpy.getValue.and.returnValue(state);

    const emptyTickets: TicketsWithVersionResponse = { tickets: [], version: Date.now() };
    ticketServiceSpy.getAllTicket.and.returnValue(of(emptyTickets));
    ticketServiceSpy.getTicketByTime.and.returnValue(of(emptyTickets));

    await TestBed.configureTestingModule({
      imports: [BookingSeatMapComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: OrderService, useValue: orderServiceMock },
        { provide: TicketService, useValue: ticketServiceSpy },
        { provide: BookingQuery, useValue: bookingQuerySpy },
        { provide: BookingService, useValue: bookingServiceSpy },
        { provide: NotificationService, useValue: notificationServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingSeatMapComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    const globalObj = window as unknown as Record<string, unknown>;
    delete globalObj['metaticket'];
  });

  /** Renders the three code boxes: desktop sidebar, mobile bar (needs a ticket) and bottom sheet (needs it open). */
  function renderAllCodeBoxes(): void {
    component.listSelectedTickets = [seat('A1')];
    component.bottomSheetVisible = true;
    fixture.detectChanges();
    fixture.detectChanges();
  }

  function panels(): PromotionOfferPanelComponent[] {
    return fixture.debugElement
      .queryAll(By.directive(PromotionOfferPanelComponent))
      .map((debugEl) => debugEl.componentInstance as PromotionOfferPanelComponent);
  }

  function codeInputOf(panel: PromotionOfferPanelComponent): HTMLInputElement {
    const input = panel.anchor?.querySelector<HTMLInputElement>('app-input input');
    if (!input) {
      throw new Error('panel without a code input in its anchor');
    }
    return input;
  }

  /** Trash control of the "Giảm giá" row in the same code box as [panel] (the nearest such row around it). */
  function removeControlNear(panel: PromotionOfferPanelComponent): HTMLElement {
    const isDiscountLabel = (p: HTMLElement): boolean => p.textContent?.trim() === 'Giảm giá';
    const panelHost = fixture.debugElement
      .queryAll(By.directive(PromotionOfferPanelComponent))
      .find((debugEl) => debugEl.componentInstance === panel)?.nativeElement as HTMLElement | undefined;
    let scope = panelHost?.parentElement ?? null;
    let label: HTMLElement | undefined;
    while (scope && !label) {
      label = Array.from(scope.querySelectorAll<HTMLElement>('p')).find(isDiscountLabel);
      scope = scope.parentElement;
    }
    const icon = label?.parentElement?.querySelector<HTMLElement>('img[alt$="ic_trash.svg"]');
    if (!icon) {
      throw new Error('no discount row with a remove control near the panel');
    }
    return icon.closest<HTMLElement>('button') ?? icon;
  }

  /**
   * A real tap: pointerdown, then the click on release. The browser hit-tests the release point, so a control that the
   * press moved (e.g. the list above it collapsed) no longer gets the click. Positions are compared instead of
   * hit-testing because the small Karma window puts the fixed mobile order bar over the sheet.
   */
  function pressAndRelease(control: HTMLElement): void {
    const before = control.getBoundingClientRect();
    control.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    fixture.detectChanges();
    const after = control.getBoundingClientRect();
    if (control.isConnected && after.top === before.top && after.left === before.left) {
      control.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    }
    fixture.detectChanges();
  }

  function isListShown(panel: PromotionOfferPanelComponent): boolean {
    const host = fixture.debugElement
      .queryAll(By.directive(PromotionOfferPanelComponent))
      .find((debugEl) => debugEl.componentInstance === panel);
    return ((host?.nativeElement as HTMLElement | undefined)?.textContent ?? '').includes('Mỗi đơn áp dụng một mã');
  }

  it('should initialize promotionOfferSource with showId and sessionId', () => {
    expect(component).toBeTruthy();
    expect(component.promotionOfferSource).toBeDefined();

    component.promotionOfferSource();
    expect(getPromotionOffersSpy).toHaveBeenCalledWith('show-seat-1', 'sess-seat-1');
  });

  it('should apply promotion code and update tentative price and couponResult', () => {
    const tentativeResponse: TentativePriceResponse = {
      priceTotal: 80000,
      priceDiscount: 20000,
      shipPrice: 0,
      promotionDetails: [
        {
          name: 'DISCOUNT20',
          priceDiscount: 20000,
          type: 'COUPON',
          error: false,
        },
      ],
    };
    tentativePriceSpy.and.returnValue(of(tentativeResponse));

    component.onPickPromotion('DISCOUNT20');

    expect(component.promotionCode).toBe('DISCOUNT20');
    expect(tentativePriceSpy).toHaveBeenCalled();
    expect(component.couponResult).toEqual({
      ok: true,
      message: 'Đã áp dụng mã DISCOUNT20',
    });
  });

  it('should report invalid or rejected coupon in couponResult', () => {
    const tentativeResponse: TentativePriceResponse = {
      priceTotal: 100000,
      priceDiscount: 0,
      shipPrice: 0,
      promotionDetails: [
        {
          name: 'EXPIRED10',
          priceDiscount: 0,
          type: 'COUPON',
          error: true,
          errorMessage: 'Mã đã hết hạn sử dụng',
        },
      ],
    };
    tentativePriceSpy.and.returnValue(of(tentativeResponse));

    component.onPickPromotion('EXPIRED10');

    expect(component.couponResult).toEqual({
      ok: false,
      message: 'Mã đã hết hạn sử dụng',
    });
  });

  it('should clear couponResult and promotionCode on remove promotion', () => {
    component.promotionCode = 'SAVED';
    component.couponResult = { ok: true, message: 'Đã áp dụng' };

    component.onRemovePromotion();

    expect(component.promotionCode).toBe('');
    expect(component.couponResult).toBeNull();
  });

  describe('out-of-order tentative responses (seq guard)', () => {
    it('ignores the older response when two codes are applied quickly', () => {
      const subject1 = new Subject<TentativePriceResponse>();
      const subject2 = new Subject<TentativePriceResponse>();
      tentativePriceSpy.and.returnValues(subject1.asObservable(), subject2.asObservable());

      component.promotionCode = 'CODE1';
      component.onApplyPromotion();
      component.promotionCode = 'CODE2';
      component.onApplyPromotion();

      subject2.next(priced(70000, 30000, 'CODE2'));
      expect(component.couponResult?.message).toBe('Đã áp dụng mã CODE2');

      subject1.next(priced(90000, 10000, 'CODE1'));
      expect(component.couponResult?.message).toBe('Đã áp dụng mã CODE2');
      expect(component.tentativePrice.priceTotal).toBe(70000);
      expect(component.tentativePrice.priceDiscount).toBe(30000);
    });

    it('keeps the newest price when the seats change twice with the same code', () => {
      const first = new Subject<TentativePriceResponse>();
      const second = new Subject<TentativePriceResponse>();
      tentativePriceSpy.and.returnValues(first.asObservable(), second.asObservable());
      component.promotionCode = 'SAME';

      component.seatPlanListener({ eventType: 'item-selected', items: [], triggerItem: seat('A1') });
      component.seatPlanListener({ eventType: 'item-selected', items: [], triggerItem: seat('A2') });

      second.next(priced(160000, 40000, 'SAME'));
      first.next(priced(100000, 0, 'SAME', 'Đơn cần tối thiểu 2 vé'));

      expect(component.tentativePrice.priceTotal).toBe(160000);
      expect(component.couponResult).toEqual({ ok: true, message: 'Đã áp dụng mã SAME' });
      expect(component.tentativePriceLoading).toBeFalse();
    });

    it('stays loading when an older request fails while the newer one is still pending', () => {
      const first = new Subject<TentativePriceResponse>();
      const second = new Subject<TentativePriceResponse>();
      tentativePriceSpy.and.returnValues(first.asObservable(), second.asObservable());
      component.promotionCode = 'SAME';

      component.seatPlanListener({ eventType: 'item-selected', items: [], triggerItem: seat('A1') });
      component.seatPlanListener({ eventType: 'item-selected', items: [], triggerItem: seat('A2') });
      first.error(new HttpErrorResponse({ status: 502 }));

      expect(component.tentativePriceLoading).toBeTrue();
      expect(component.couponResult).toBeNull();

      second.next(priced(160000, 40000, 'SAME'));
      expect(component.tentativePriceLoading).toBeFalse();
      expect(component.couponResult).toEqual({ ok: true, message: 'Đã áp dụng mã SAME' });
    });
  });

  describe('picking another code after one was applied', () => {
    beforeEach(() => {
      component.listSelectedTickets = [seat('A1')];
      tentativePriceSpy.and.returnValue(of(priced(80000, 20000, 'SALE20')));
      component.onPickPromotion('SALE20');
      expect(component.couponResult).toEqual({ ok: true, message: 'Đã áp dụng mã SALE20' });
    });

    it('drops the previous result as soon as the new code is picked', () => {
      tentativePriceSpy.and.returnValue(new Subject<TentativePriceResponse>().asObservable());

      component.onPickPromotion('VIP50');

      expect(component.promotionCode).toBe('VIP50');
      expect(component.couponResult).toBeNull();
    });

    it('reports the failed check against the new code, not "Đã áp dụng" of the old one', () => {
      tentativePriceSpy.and.returnValue(throwError(() => new HttpErrorResponse({ status: 502 })));

      component.onPickPromotion('VIP50');
      fixture.detectChanges();

      expect(component.couponResult).toEqual({ ok: false, message: 'Chưa kiểm tra được mã VIP50. Vui lòng thử lại.' });
      expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Đã áp dụng mã SALE20');
    });
  });

  describe('code boxes and the offer panel', () => {
    it('keeps offers closed when the remove suffix receives focus and clears the coupon with one price check', () => {
      renderAllCodeBoxes();
      tentativePriceSpy.and.returnValue(of(priced(80000, 20000, 'SALE20')));
      component.onPickPromotion('SALE20');
      fixture.detectChanges();
      const all = panels();
      all.forEach((panel) => panel.close());
      fixture.detectChanges();
      const mobileBar = all[1];
      const removeButton = mobileBar.anchor?.querySelector<HTMLButtonElement>('button[aria-label="Gỡ mã giảm giá"]');
      if (!removeButton) {
        throw new Error('missing mobile coupon remove button');
      }
      tentativePriceSpy.calls.reset();
      tentativePriceSpy.and.returnValue(of(priced(100000, 0, '')));

      removeButton.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      fixture.detectChanges();
      expect(isListShown(mobileBar)).toBeFalse();

      removeButton.click();
      fixture.detectChanges();
      expect(tentativePriceSpy.calls.count()).toBe(1);
      const request = tentativePriceSpy.calls.mostRecent().args[0] as TentativePriceRequest;
      expect(request.promotionCode).toBeUndefined();
      expect(component.promotionCode).toBe('');
      expect(component.couponResult).toBeNull();
      expect(component.tentativePrice.priceDiscount).toBe(0);
      expect(isListShown(mobileBar)).toBeFalse();
    });

    it('opens the panel when any of the three code inputs gets focus, with one request in total', () => {
      renderAllCodeBoxes();
      const all = panels();
      expect(all.length).toBe(3);
      // nz-drawer's focus trap focuses the sheet's code input when it opens, which already opened that panel.
      all.forEach((panel) => panel.close());
      fixture.detectChanges();

      all.forEach((panel) => {
        expect(isListShown(panel)).toBeFalse();
        codeInputOf(panel).dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
        fixture.detectChanges();
        expect(isListShown(panel)).toBeTrue();
      });
      expect(getPromotionOffersSpy.calls.count()).toBe(1);
    });

    it('keeps the panel open when the customer presses the code input again', () => {
      renderAllCodeBoxes();
      const [desktop] = panels();
      const input = codeInputOf(desktop);

      input.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      input.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      input.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      fixture.detectChanges();

      expect(isListShown(desktop)).toBeTrue();
      expect(getPromotionOffersSpy.calls.count()).toBe(1);
    });

    it('removes the code with one press while the offer list is open', () => {
      renderAllCodeBoxes();
      tentativePriceSpy.and.returnValue(of(priced(80000, 20000, 'SALE20')));
      component.onPickPromotion('SALE20');
      fixture.detectChanges();
      // Bottom sheet: the layout shown at the Karma window width (the desktop sidebar needs >= 1024px).
      const sheet = panels()[2];
      sheet.open();
      fixture.detectChanges();
      expect(isListShown(sheet)).toBeTrue();
      const removeSpy = spyOn(component, 'onRemovePromotion').and.callThrough();

      pressAndRelease(removeControlNear(sheet));

      expect(removeSpy.calls.count()).toBe(1);
      expect(component.promotionCode).toBe('');
    });

    it('marks a listed code as applied only once the price check confirms it', () => {
      renderAllCodeBoxes();
      const appliedCodes = (): (string | null)[] => panels().map((panel) => panel.appliedCode);

      tentativePriceSpy.and.returnValue(of(priced(100000, 0, 'SALE20', 'Mã đã hết lượt sử dụng')));
      component.onPickPromotion('SALE20');
      fixture.detectChanges();
      expect(appliedCodes()).toEqual([null, null, null]);

      // Valid code, but another promotion wins: no COUPON line in the winning doc.
      tentativePriceSpy.and.returnValue(
        of({ priceTotal: 70000, priceDiscount: 30000, shipPrice: 0, promotionDetails: [] }),
      );
      component.onApplyPromotion();
      fixture.detectChanges();
      expect(appliedCodes()).toEqual([null, null, null]);

      tentativePriceSpy.and.returnValue(of(priced(80000, 20000, 'SALE20')));
      component.onApplyPromotion();
      fixture.detectChanges();
      expect(appliedCodes()).toEqual(['SALE20', 'SALE20', 'SALE20']);

      component.onChangePromotionCode('SALE2');
      fixture.detectChanges();
      expect(appliedCodes()).toEqual([null, null, null]);
    });
  });
});

describe('BookingSeatMapComponent — lone-seat rule', () => {
  // Row A: one block A1..A6 as an admin reviewed it in "Phân chia ghế".
  const ROW_A: SeatBlocksResponse = { blocks: [['A1', 'A2', 'A3', 'A4', 'A5', 'A6']], stale: false };

  let component: BookingSeatMapComponent;
  let fixture: ComponentFixture<BookingSeatMapComponent>;
  let ticketServiceSpy: jasmine.SpyObj<TicketService>;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;
  let showServiceSpy: jasmine.SpyObj<ShowService>;
  let ticketsOnLoad: TicketsWithVersionResponse;

  function ticketsFor(sids: string[], remCap = 1): TicketsWithVersionResponse {
    return {
      tickets: sids.map((sid) => ({ id: `ticket-${sid}`, sid, priceCat: 'VIP', basePrice: 100000, remCap })),
      version: Date.now(),
    };
  }

  async function setup(
    session: Record<string, unknown>,
    seatBlocks: Observable<SeatBlocksResponse> = of(ROW_A),
  ): Promise<void> {
    const plannerMock = class {
      initialize(): Promise<unknown> {
        return Promise.resolve({});
      }
      registerListener(): void {}
      setDisabled(): void {}
      clearSelection(): void {}
    };
    const metaticketMock: Record<string, unknown> = {};
    metaticketMock['PlannerWrapper'] = plannerMock;
    const globalObj = window as unknown as Record<string, unknown>;
    globalObj['metaticket'] = metaticketMock;

    ticketServiceSpy = jasmine.createSpyObj<TicketService>('TicketService', [
      'getAllTicket',
      'getTicketByTime',
      'createReservation',
    ]);
    ticketServiceSpy.getAllTicket.and.returnValue(of(ticketsOnLoad));
    ticketServiceSpy.getTicketByTime.and.returnValue(of({ tickets: [], version: Date.now() }));
    // Never answers: the specs only count whether a reservation was attempted.
    ticketServiceSpy.createReservation.and.returnValue(new Subject<{ reservationId: string }>());
    notificationServiceSpy = jasmine.createSpyObj<NotificationService>('NotificationService', [
      'showErrorNotification',
    ]);
    showServiceSpy = jasmine.createSpyObj<ShowService>('ShowService', ['getSeatBlocks']);
    showServiceSpy.getSeatBlocks.and.returnValue(seatBlocks);
    const bookingQuerySpy = jasmine.createSpyObj<BookingQuery>('BookingQuery', ['getValue']);
    bookingQuerySpy.getValue.and.returnValue({
      showId: 'show-gap-1',
      sessionId: 'sess-gap-1',
      showDetail: { sessions: [{ id: 'sess-gap-1', seatPlanId: 'plan-1', ...session }] },
    } as unknown as BookingState);
    const orderServiceMock = {
      getPromotionOffers: jasmine.createSpy('getPromotionOffers').and.returnValue(of([])),
      tentativePrice: jasmine
        .createSpy('tentativePrice')
        .and.returnValue(of({ priceTotal: 0, priceDiscount: 0, shipPrice: 0 })),
    } as unknown as OrderService;

    await TestBed.configureTestingModule({
      imports: [BookingSeatMapComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: OrderService, useValue: orderServiceMock },
        { provide: TicketService, useValue: ticketServiceSpy },
        { provide: ShowService, useValue: showServiceSpy },
        { provide: BookingQuery, useValue: bookingQuerySpy },
        {
          provide: BookingService,
          useValue: jasmine.createSpyObj<BookingService>('BookingService', ['bookingSeatMap']),
        },
        { provide: NotificationService, useValue: notificationServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingSeatMapComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  /** Lets nz-modal attach its overlay; not whenStable(): the 5 s ticket poll keeps the zone busy. */
  async function renderDialog(): Promise<void> {
    fixture.detectChanges();
    await new Promise<void>((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  }

  beforeEach(() => {
    ticketsOnLoad = ticketsFor(['A1', 'A2', 'A3', 'A4', 'A5', 'A6']);
  });

  afterEach(() => {
    const globalObj = window as unknown as Record<string, unknown>;
    delete globalObj['metaticket'];
    if (component?.interval) {
      clearInterval(component.interval);
    }
  });

  it('refuses a choice that leaves a lone seat and suggests neighbouring seats instead', async () => {
    await setup({ edType: 'SeatPlanner', preventSingleSeatGap: true });
    component.listSelectedTickets = [seat('A2'), seat('A3')];

    component.createReservation();

    expect(showServiceSpy.getSeatBlocks.calls.mostRecent().args[0]).toBe('plan-1');
    expect(ticketServiceSpy.createReservation.calls.count()).toBe(0);
    expect(component.seatGapDialog).toEqual({
      loneSeats: 'A1',
      suggestions: [{ replace: 'A2, A3', options: ['A1, A2', 'A3, A4'] }],
    });
    // A dialog the customer closes, not a self-dismissing toast.
    expect(notificationServiceSpy.showErrorNotification.calls.count()).toBe(0);
  });

  it('shows the dialog until the customer closes it, each option on its own line', async () => {
    await setup({ edType: 'SeatPlanner', preventSingleSeatGap: true });
    component.listSelectedTickets = [seat('A2'), seat('A3')];

    component.createReservation();
    await renderDialog();

    const dialog = document.querySelector('.seat-gap-dialog');
    const options = Array.from(document.querySelectorAll('.seat-gap-dialog .seat-gap-option')).map((el) =>
      el.textContent?.trim(),
    );
    expect(options).toEqual(['A1, A2', 'A3, A4']);
    expect(dialog?.textContent).toContain('hoặc');
    expect(component.seatGapDialog).not.toBeNull();

    document.querySelector<HTMLButtonElement>('.seat-gap-dialog app-button button')?.click();
    fixture.detectChanges();
    expect(component.seatGapDialog).toBeNull();
  });

  it('lets a choice without lone seats through', async () => {
    await setup({ edType: 'SeatPlanner', preventSingleSeatGap: true });
    component.listSelectedTickets = [seat('A1'), seat('A2')];

    component.createReservation();

    expect(ticketServiceSpy.createReservation.calls.count()).toBe(1);
    expect(notificationServiceSpy.showErrorNotification.calls.count()).toBe(0);
  });

  it('neither loads blocks nor checks when the session leaves the rule off or does not say', async () => {
    await setup({ edType: 'SeatPlanner' });
    component.listSelectedTickets = [seat('A2'), seat('A3')];

    component.createReservation();

    expect(showServiceSpy.getSeatBlocks.calls.count()).toBe(0);
    expect(ticketServiceSpy.createReservation.calls.count()).toBe(1);
  });

  it('does not check sessions sold by ticket type (policy config)', async () => {
    await setup({ edType: 'PolicyConfig', preventSingleSeatGap: true });
    component.listSelectedTickets = [seat('A2'), seat('A3')];

    component.createReservation();

    expect(showServiceSpy.getSeatBlocks.calls.count()).toBe(0);
    expect(ticketServiceSpy.createReservation.calls.count()).toBe(1);
  });

  it('judges with the seat states of the latest poll, not the first load', async () => {
    await setup({ edType: 'SeatPlanner', preventSingleSeatGap: true });
    component.listSelectedTickets = [seat('A3'), seat('A4')];
    // A1 is bought by someone else after the page loaded: A2 would now be left alone.
    ticketServiceSpy.getTicketByTime.and.returnValue(of(ticketsFor(['A1'], 0)));
    (component as unknown as Record<string, () => void>)['_getTicketByTime']();

    component.createReservation();

    expect(ticketServiceSpy.createReservation.calls.count()).toBe(0);
    expect(component.seatGapDialog?.loneSeats).toBe('A2');
  });

  it('shows seat ids from the seat plan as text, not markup', async () => {
    ticketsOnLoad = ticketsFor(['<b>X</b>', 'A2', 'A3', 'A4', 'A5', 'A6']);
    await setup(
      { edType: 'SeatPlanner', preventSingleSeatGap: true },
      of({ blocks: [['<b>X</b>', 'A2', 'A3', 'A4', 'A5', 'A6']], stale: false }),
    );
    component.listSelectedTickets = [seat('A2'), seat('A3')];

    component.createReservation();
    await renderDialog();

    const dialog = document.querySelector('.seat-gap-dialog');
    expect(dialog?.textContent).toContain('<b>X</b>');
    expect(dialog?.querySelector('b')).toBeNull();
  });

  it('ignores blocks saved before the seat plan was last edited', async () => {
    await setup({ edType: 'SeatPlanner', preventSingleSeatGap: true }, of({ ...ROW_A, stale: true }));
    component.listSelectedTickets = [seat('A2'), seat('A3')];

    component.createReservation();

    expect(ticketServiceSpy.createReservation.calls.count()).toBe(1);
  });

  it('never blocks the sale when the seat plan has no blocks (404)', async () => {
    const warnSpy = spyOn(console, 'warn');
    await setup(
      { edType: 'SeatPlanner', preventSingleSeatGap: true },
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    component.listSelectedTickets = [seat('A2'), seat('A3')];

    component.createReservation();

    expect(ticketServiceSpy.createReservation.calls.count()).toBe(1);
    expect(warnSpy.calls.count()).toBe(0);
  });

  it('never blocks the sale when the blocks cannot be loaded, and says why in the console', async () => {
    const warnSpy = spyOn(console, 'warn');
    await setup(
      { edType: 'SeatPlanner', preventSingleSeatGap: true },
      throwError(() => new HttpErrorResponse({ status: 502 })),
    );
    component.listSelectedTickets = [seat('A2'), seat('A3')];

    component.createReservation();

    expect(ticketServiceSpy.createReservation.calls.count()).toBe(1);
    expect(warnSpy.calls.count()).toBe(1);
  });

  it('lets the customer through while the blocks are still loading', async () => {
    await setup({ edType: 'SeatPlanner', preventSingleSeatGap: true }, new Subject<SeatBlocksResponse>());
    component.listSelectedTickets = [seat('A2'), seat('A3')];

    component.createReservation();

    expect(ticketServiceSpy.createReservation.calls.count()).toBe(1);
  });
});
