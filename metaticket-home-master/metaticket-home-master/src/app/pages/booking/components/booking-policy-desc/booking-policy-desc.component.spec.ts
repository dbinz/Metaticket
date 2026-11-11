import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, Subject, throwError } from 'rxjs';
import { BookingPolicyDescComponent } from './booking-policy-desc.component';
import { NotificationService, OrderService, TicketService } from 'src/app/lib/services';
import { BookingQuery, BookingService, BookingState } from 'src/app/lib/stores';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import {
  PromotionOfferResponse,
  TentativePriceResponse,
  TicketResponse,
  TicketsWithVersionResponse,
} from 'src/app/lib/entities';
import { PromotionOfferPanelComponent } from 'src/app/lib/components';

const OFFER: PromotionOfferResponse = {
  code: 'POLICY15',
  title: 'Giảm 15.000đ',
  discountType: 'AMOUNT',
  discountValue: 15000,
};

const TICKET: TicketResponse = { id: 'ticket-ga', sid: 'GA', priceCat: 'GA', basePrice: 100000, remCap: 50 };

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

describe('BookingPolicyDescComponent — MTH-5 Promotion Integration', () => {
  let component: BookingPolicyDescComponent;
  let fixture: ComponentFixture<BookingPolicyDescComponent>;
  let getPromotionOffersSpy: jasmine.Spy;
  let tentativePriceSpy: jasmine.Spy;
  let ticketServiceSpy: jasmine.SpyObj<TicketService>;
  let bookingQuerySpy: jasmine.SpyObj<BookingQuery>;

  beforeEach(async () => {
    getPromotionOffersSpy = jasmine.createSpy('getPromotionOffers').and.returnValue(of([OFFER]));
    tentativePriceSpy = jasmine
      .createSpy('tentativePrice')
      .and.returnValue(of({ priceTotal: 100000, priceDiscount: 0, shipPrice: 0 }));

    const orderServiceMock = {
      getPromotionOffers: getPromotionOffersSpy,
      tentativePrice: tentativePriceSpy,
    } as unknown as OrderService;

    ticketServiceSpy = jasmine.createSpyObj<TicketService>('TicketService', ['getAllTicket']);
    bookingQuerySpy = jasmine.createSpyObj<BookingQuery>('BookingQuery', ['getValue']);
    const bookingServiceSpy: jasmine.SpyObj<BookingService> = jasmine.createSpyObj<BookingService>('BookingService', [
      'bookingSeatMap',
    ]);
    const notificationServiceSpy: jasmine.SpyObj<NotificationService> = jasmine.createSpyObj<NotificationService>(
      'NotificationService',
      ['showErrorNotification'],
    );

    const state = {
      showId: 'show-policy-1',
      sessionId: 'sess-policy-1',
      showDetail: {
        sessions: [{ id: 'sess-policy-1', name: 'Suất Policy 1' }],
      },
    } as unknown as BookingState;
    bookingQuerySpy.getValue.and.returnValue(state);

    const emptyTicketResponse: TicketsWithVersionResponse = { tickets: [], version: Date.now() };
    ticketServiceSpy.getAllTicket.and.returnValue(of(emptyTicketResponse));

    await TestBed.configureTestingModule({
      imports: [BookingPolicyDescComponent],
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

    fixture = TestBed.createComponent(BookingPolicyDescComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  /** Renders the three code boxes: desktop sidebar, mobile bar (needs a ticket) and bottom sheet (needs it open). */
  function renderAllCodeBoxes(): void {
    component.onChangeQuantity(1, TICKET);
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
    expect(getPromotionOffersSpy).toHaveBeenCalledWith('show-policy-1', 'sess-policy-1');
  });

  it('should apply promotion code and update tentative price and couponResult', () => {
    const tentativeResponse: TentativePriceResponse = {
      priceTotal: 85000,
      priceDiscount: 15000,
      shipPrice: 0,
      promotionDetails: [
        {
          name: 'POLICY15',
          priceDiscount: 15000,
          type: 'COUPON',
          error: false,
        },
      ],
    };
    tentativePriceSpy.and.returnValue(of(tentativeResponse));

    component.onPickPromotion('POLICY15');

    expect(component.promotionCode).toBe('POLICY15');
    expect(tentativePriceSpy).toHaveBeenCalled();
    expect(component.couponResult).toEqual({
      ok: true,
      message: 'Đã áp dụng mã POLICY15',
    });
  });

  it('should clear couponResult and promotionCode on remove promotion', () => {
    component.promotionCode = 'DISCOUNT';
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

      component.promotionCode = 'FIRST';
      component.onApplyPromotion();
      component.promotionCode = 'SECOND';
      component.onApplyPromotion();

      subject2.next(priced(60000, 40000, 'SECOND'));
      expect(component.couponResult?.message).toBe('Đã áp dụng mã SECOND');

      subject1.next(priced(90000, 10000, 'FIRST'));
      expect(component.couponResult?.message).toBe('Đã áp dụng mã SECOND');
      expect(component.tentativePrice.priceTotal).toBe(60000);
      expect(component.tentativePrice.priceDiscount).toBe(40000);
    });

    it('keeps the newest price when the quantity changes twice with the same code', () => {
      const first = new Subject<TentativePriceResponse>();
      const second = new Subject<TentativePriceResponse>();
      tentativePriceSpy.and.returnValues(first.asObservable(), second.asObservable());
      component.promotionCode = 'SAME';

      component.onChangeQuantity(1, TICKET);
      component.onChangeQuantity(2, TICKET);

      second.next(priced(170000, 30000, 'SAME'));
      first.next(priced(100000, 0, 'SAME', 'Đơn cần tối thiểu 2 vé'));

      expect(component.tentativePrice.priceTotal).toBe(170000);
      expect(component.couponResult).toEqual({ ok: true, message: 'Đã áp dụng mã SAME' });
      expect(component.tentativePriceLoading).toBeFalse();
    });

    it('stays loading when an older request fails while the newer one is still pending', () => {
      const first = new Subject<TentativePriceResponse>();
      const second = new Subject<TentativePriceResponse>();
      tentativePriceSpy.and.returnValues(first.asObservable(), second.asObservable());
      component.promotionCode = 'SAME';

      component.onChangeQuantity(1, TICKET);
      component.onChangeQuantity(2, TICKET);
      first.error(new HttpErrorResponse({ status: 502 }));

      expect(component.tentativePriceLoading).toBeTrue();
      expect(component.couponResult).toBeNull();

      second.next(priced(170000, 30000, 'SAME'));
      expect(component.tentativePriceLoading).toBeFalse();
      expect(component.couponResult).toEqual({ ok: true, message: 'Đã áp dụng mã SAME' });
    });
  });

  /** Calendar session: tickets and prices are per visit date, so the code result of the previous date is void. */
  describe('visit date change (calendar session)', () => {
    function changeVisitDate(from: string, to: string): void {
      component.visitDate = to;
      component.ngOnChanges({ visitDate: new SimpleChange(from, to, false) });
    }

    beforeEach(() => {
      component.visitDate = '2026-11-01';
    });

    it('drops a price check of the previous date that answers after the change', () => {
      const pending = new Subject<TentativePriceResponse>();
      tentativePriceSpy.and.returnValue(pending.asObservable());
      component.promotionCode = 'POLICY15';
      component.onChangeQuantity(1, TICKET);

      changeVisitDate('2026-11-01', '2026-11-02');
      pending.next(priced(85000, 15000, 'POLICY15'));

      expect(component.tentativePrice).toEqual({ priceTotal: 0, priceDiscount: 0, shipPrice: 0 });
      expect(component.couponResult).toBeNull();
      expect(component.tentativePriceLoading).toBeFalse();
    });

    it('clears "Đã áp dụng" of the previous date and re-checks the kept code with the new date tickets', () => {
      tentativePriceSpy.and.returnValue(of(priced(85000, 15000, 'POLICY15')));
      component.onChangeQuantity(1, TICKET);
      component.onPickPromotion('POLICY15');
      expect(component.appliedPromotionCode).toBe('POLICY15');

      changeVisitDate('2026-11-01', '2026-11-02');

      expect(component.couponResult).toBeNull();
      expect(component.appliedPromotionCode).toBeNull();
      expect(component.promotionCode).toBe('POLICY15');

      const ticketOfNewDate: TicketResponse = { ...TICKET, id: 'ticket-ga-1102' };
      component.onChangeQuantity(2, ticketOfNewDate);

      expect(tentativePriceSpy.calls.mostRecent().args).toEqual([
        jasmine.objectContaining({
          promotionCode: 'POLICY15',
          tickets: [{ ticketId: 'ticket-ga-1102', quantity: 2, priceCat: 'GA' }],
        }),
      ]);
      expect(component.couponResult).toEqual({ ok: true, message: 'Đã áp dụng mã POLICY15' });
    });
  });

  describe('picking another code after one was applied', () => {
    beforeEach(() => {
      component.onChangeQuantity(1, TICKET);
      tentativePriceSpy.and.returnValue(of(priced(85000, 15000, 'POLICY15')));
      component.onPickPromotion('POLICY15');
      expect(component.couponResult).toEqual({ ok: true, message: 'Đã áp dụng mã POLICY15' });
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
      expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Đã áp dụng mã POLICY15');
    });
  });

  describe('code boxes and the offer panel', () => {
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
      tentativePriceSpy.and.returnValue(of(priced(85000, 15000, 'POLICY15')));
      component.onPickPromotion('POLICY15');
      // Desktop sidebar: its rows sit below the list, so closing the list moves them (the sheet and the mobile bar grow
      // from the bottom). It is `hidden laptop:flex` and the Karma window is narrower than 1024px: force it visible.
      const desktop = panels()[0];
      const desktopHost = fixture.debugElement.queryAll(By.directive(PromotionOfferPanelComponent))[0]
        .nativeElement as HTMLElement;
      desktopHost.closest<HTMLElement>('.hidden')?.style.setProperty('display', 'flex', 'important');
      fixture.detectChanges();
      desktop.open();
      fixture.detectChanges();
      expect(isListShown(desktop)).toBeTrue();
      const removeSpy = spyOn(component, 'onRemovePromotion').and.callThrough();

      pressAndRelease(removeControlNear(desktop));

      expect(removeSpy.calls.count()).toBe(1);
      expect(component.promotionCode).toBe('');
    });

    it('marks a listed code as applied only once the price check confirms it', () => {
      renderAllCodeBoxes();
      const appliedCodes = (): (string | null)[] => panels().map((panel) => panel.appliedCode);

      tentativePriceSpy.and.returnValue(of(priced(100000, 0, 'POLICY15', 'Mã đã hết lượt sử dụng')));
      component.onPickPromotion('POLICY15');
      fixture.detectChanges();
      expect(appliedCodes()).toEqual([null, null, null]);

      // Valid code, but another promotion wins: no COUPON line in the winning doc.
      tentativePriceSpy.and.returnValue(
        of({ priceTotal: 70000, priceDiscount: 30000, shipPrice: 0, promotionDetails: [] }),
      );
      component.onApplyPromotion();
      fixture.detectChanges();
      expect(appliedCodes()).toEqual([null, null, null]);

      tentativePriceSpy.and.returnValue(of(priced(85000, 15000, 'POLICY15')));
      component.onApplyPromotion();
      fixture.detectChanges();
      expect(appliedCodes()).toEqual(['POLICY15', 'POLICY15', 'POLICY15']);

      component.onChangePromotionCode('POLICY1');
      fixture.detectChanges();
      expect(appliedCodes()).toEqual([null, null, null]);
    });
  });
});
