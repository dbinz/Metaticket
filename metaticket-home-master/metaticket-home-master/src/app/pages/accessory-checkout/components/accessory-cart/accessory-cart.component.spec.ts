import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { AccessoryCartComponent } from './accessory-cart.component';
import { PromotionOfferSource } from 'src/app/lib/components';
import { AccessoryService, AuthService, NotificationService } from 'src/app/lib/services';
import {
  AccessoryCartQuery,
  AccessoryCartService,
  AccessoryCheckoutQuery,
  AccessoryCheckoutService,
  AccessoryCheckoutState,
  Cart,
  CartLine,
} from 'src/app/lib/stores';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccessoryShowResponse, PromotionOfferResponse, ValidateDiscountResponse } from 'src/app/lib/entities';

function validated(code: string, amount: number): ValidateDiscountResponse {
  return { code, type: 'FixedAmount', value: amount, amount, subtotal: 50000, total: 50000 - amount };
}

describe('AccessoryCartComponent — MTH-5 Promotion Integration', () => {
  let component: AccessoryCartComponent;
  let fixture: ComponentFixture<AccessoryCartComponent>;
  let validateDiscountSpy: jasmine.Spy;
  let getDiscountOffersSpy: jasmine.Spy;
  let cartQuerySpy: jasmine.SpyObj<AccessoryCartQuery>;
  let checkoutQuerySpy: jasmine.SpyObj<AccessoryCheckoutQuery>;
  let checkoutServiceSpy: jasmine.SpyObj<AccessoryCheckoutService>;
  let cartServiceSpy: jasmine.SpyObj<AccessoryCartService>;
  let linesSubject: BehaviorSubject<CartLine[]>;

  const mockShow: AccessoryShowResponse = {
    id: 'show-acc-1',
    code: 'ACC2026',
    name: 'Show Phụ Kiện',
    status: 'Open',
    accessories: [],
  };

  const sampleOffers: PromotionOfferResponse[] = [
    {
      code: 'MIN100K',
      title: 'Giảm 10k cho đơn 100k',
      discountType: 'AMOUNT',
      discountValue: 10000,
      minOrderAmount: 100000,
      limitedQuantity: false,
    },
    {
      code: 'FREE50',
      title: 'Giảm 50k',
      discountType: 'AMOUNT',
      discountValue: 50000,
      limitedQuantity: false,
    },
  ];

  beforeEach(async () => {
    linesSubject = new BehaviorSubject<CartLine[]>([
      {
        accessoryId: 'item-1',
        name: 'Áo thun',
        unitPrice: 50000,
        quantity: 1,
      },
    ]);

    validateDiscountSpy = jasmine.createSpy('validateDiscount');
    getDiscountOffersSpy = jasmine.createSpy('getDiscountOffers').and.returnValue(of(sampleOffers));
    const getAccessorySpy = jasmine.createSpy('getAccessory');

    const accessoryServiceMock = {
      validateDiscount: validateDiscountSpy,
      getDiscountOffers: getDiscountOffersSpy,
      getAccessory: getAccessorySpy,
    } as unknown as AccessoryService;

    cartQuerySpy = jasmine.createSpyObj<AccessoryCartQuery>('AccessoryCartQuery', ['getValue'], {
      lines$: linesSubject.asObservable(),
    });
    checkoutQuerySpy = jasmine.createSpyObj<AccessoryCheckoutQuery>('AccessoryCheckoutQuery', ['getValue']);
    checkoutServiceSpy = jasmine.createSpyObj<AccessoryCheckoutService>('AccessoryCheckoutService', [
      'applyDiscount',
      'clearDiscount',
      'setSubtotal',
    ]);
    cartServiceSpy = jasmine.createSpyObj<AccessoryCartService>('AccessoryCartService', [
      'updateQuantity',
      'removeLine',
    ]);
    const notificationServiceSpy: jasmine.SpyObj<NotificationService> = jasmine.createSpyObj<NotificationService>(
      'NotificationService',
      ['showErrorNotification', 'showSuccessNotification'],
    );
    const authServiceSpy: jasmine.SpyObj<AuthService> = jasmine.createSpyObj<AuthService>('AuthService', [
      'isAuthenticated',
    ]);
    const routerSpy: jasmine.SpyObj<Router> = jasmine.createSpyObj<Router>('Router', ['navigate']);

    const cartState: Cart = {
      accessoryShowId: 'show-acc-1',
      lines: [],
    };
    cartQuerySpy.getValue.and.returnValue(cartState);

    const checkoutState = {} as unknown as AccessoryCheckoutState;
    checkoutQuerySpy.getValue.and.returnValue(checkoutState);

    await TestBed.configureTestingModule({
      imports: [AccessoryCartComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AccessoryService, useValue: accessoryServiceMock },
        { provide: AccessoryCartQuery, useValue: cartQuerySpy },
        { provide: AccessoryCheckoutQuery, useValue: checkoutQuerySpy },
        { provide: AccessoryCheckoutService, useValue: checkoutServiceSpy },
        { provide: AccessoryCartService, useValue: cartServiceSpy },
        { provide: NotificationService, useValue: notificationServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AccessoryCartComponent);
    component = fixture.componentInstance;
    component.show = mockShow;
    fixture.detectChanges();
  });

  /** The page's offer source; fails the test instead of asserting non-null with `!`. */
  function offerSource(): PromotionOfferSource {
    const source = component.promotionOfferSource;
    if (!source) {
      throw new Error('promotionOfferSource is null');
    }
    return source;
  }

  it('should initialize promotionOfferSource with accessoryShowId', () => {
    expect(component).toBeTruthy();
    expect(component.promotionOfferSource).not.toBeNull();

    offerSource()().subscribe((offers) => {
      expect(offers).toEqual(sampleOffers);
    });
    expect(getDiscountOffersSpy).toHaveBeenCalledWith('show-acc-1');
  });

  it('should apply discount on picking promotion from panel', () => {
    validateDiscountSpy.and.returnValue(
      of({
        code: 'FREE50',
        amount: 50000,
        subtotal: 50000,
        total: 0,
        type: 'FixedAmount',
        value: 50000,
      }),
    );

    component.onPickPromotion('FREE50');

    expect(component.discountControl.value).toBe('FREE50');
    expect(validateDiscountSpy).toHaveBeenCalled();
    expect(component.discountApplied).toBeTrue();
    expect(component.discountAmount).toBe(50000);
    expect(component.discountError).toBe('');
  });

  it('should explain rejection when subtotal does not meet offer minOrderAmount', () => {
    offerSource()().subscribe();

    component.subtotal = 50000;
    component.discountControl.setValue('MIN100K');
    validateDiscountSpy.and.returnValue(throwError(() => ({ status: 422, error: 'Invalid discount' })));

    component.onApplyDiscount();

    expect(component.discountApplied).toBeFalse();
    expect(component.discountError).toBe('Đơn hàng cần tối thiểu 100.000₫ để dùng mã này');
  });

  it('should show generic error message if rejected code is not in offers', () => {
    offerSource()().subscribe();

    component.subtotal = 50000;
    component.discountControl.setValue('UNKNOWN_CODE');
    validateDiscountSpy.and.returnValue(throwError(() => ({ status: 422, error: 'Invalid discount' })));

    component.onApplyDiscount();

    expect(component.discountApplied).toBeFalse();
    expect(component.discountError).toBe('Mã không hợp lệ hoặc đã hết hạn.');
  });

  it('should create a new promotionOfferSource reference when show changes', () => {
    const originalSource = component.promotionOfferSource;

    component.show = { ...mockShow, id: 'show-acc-2' };
    const updatedState: Cart = {
      accessoryShowId: 'show-acc-2',
      lines: [],
    };
    cartQuerySpy.getValue.and.returnValue(updatedState);

    component.ngOnChanges({
      show: {
        currentValue: component.show,
        previousValue: mockShow,
        firstChange: false,
        isFirstChange: (): boolean => false,
      },
    });

    expect(component.promotionOfferSource).not.toBe(originalSource);
    offerSource()();
    expect(getDiscountOffersSpy).toHaveBeenCalledWith('show-acc-2');
  });

  it('explains a rejection even when the offer list contains null entries', () => {
    getDiscountOffersSpy.and.returnValue(of([null, sampleOffers[0]] as unknown as PromotionOfferResponse[]));
    offerSource()().subscribe();
    component.subtotal = 50000;
    validateDiscountSpy.and.returnValue(throwError(() => ({ status: 422, error: 'Invalid discount' })));

    component.onPickPromotion('MIN100K');

    expect(component.discountError).toBe('Đơn hàng cần tối thiểu 100.000₫ để dùng mã này');
    expect(component.discountLoading).toBeFalse();
  });

  it('opens the offer panel when the code input gets focus, with a single request', () => {
    const host = fixture.nativeElement as HTMLElement;
    const input = host.querySelector<HTMLInputElement>('input[nz-input]');
    expect(input).not.toBeNull();
    expect(host.textContent).not.toContain('Mỗi đơn áp dụng một mã');

    input?.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    input?.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    input?.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    fixture.detectChanges();

    expect(host.textContent).toContain('Mỗi đơn áp dụng một mã');
    expect(getDiscountOffersSpy.calls.count()).toBe(1);
  });

  describe('codes picked while an earlier validation is in flight', () => {
    let first: Subject<ValidateDiscountResponse>;
    let second: Subject<ValidateDiscountResponse>;

    beforeEach(() => {
      first = new Subject<ValidateDiscountResponse>();
      second = new Subject<ValidateDiscountResponse>();
      validateDiscountSpy.and.returnValues(first.asObservable(), second.asObservable());
    });

    it('keeps the last picked code when the earlier response arrives later', () => {
      component.onPickPromotion('MIN100K');
      component.onPickPromotion('FREE50');

      second.next(validated('FREE50', 50000));
      first.next(validated('MIN100K', 10000));

      expect(component.discountControl.value).toBe('FREE50');
      expect(component.discountAmount).toBe(50000);
      expect(checkoutServiceSpy.applyDiscount.calls.allArgs().map((args) => args[0])).toEqual(['FREE50']);
      expect(component.discountLoading).toBeFalse();
    });

    it('ignores a late rejection of the earlier code', () => {
      component.onPickPromotion('MIN100K');
      component.onPickPromotion('FREE50');

      second.next(validated('FREE50', 50000));
      first.error({ status: 422, error: 'Invalid discount' });

      expect(component.discountApplied).toBeTrue();
      expect(component.discountError).toBe('');
      expect(checkoutServiceSpy.clearDiscount.calls.count()).toBe(0);
    });

    it('stays loading until the last picked code answers', () => {
      component.onPickPromotion('MIN100K');
      component.onPickPromotion('FREE50');

      first.next(validated('MIN100K', 10000));

      expect(component.discountLoading).toBeTrue();
      expect(component.discountApplied).toBeFalse();
    });

    it('does not re-apply a code whose answer arrives after the customer removed the discount', () => {
      component.onPickPromotion('MIN100K');
      first.next(validated('MIN100K', 10000));
      expect(component.discountApplied).toBeTrue();

      component.onPickPromotion('FREE50');
      component.onRemoveDiscount();
      second.next(validated('FREE50', 50000));

      expect(component.discountApplied).toBeFalse();
      expect(component.discountControl.value).toBe('');
      expect(component.discountLoading).toBeFalse();
    });
  });

  describe('switching code or cart while a code is applied or being checked', () => {
    let first: Subject<ValidateDiscountResponse>;
    let second: Subject<ValidateDiscountResponse>;

    beforeEach(() => {
      first = new Subject<ValidateDiscountResponse>();
      second = new Subject<ValidateDiscountResponse>();
      validateDiscountSpy.and.returnValues(first.asObservable(), second.asObservable());
    });

    it('drops the applied code before checking another one picked from the panel', () => {
      component.onPickPromotion('MIN100K');
      first.next(validated('MIN100K', 10000));
      expect(component.discountApplied).toBeTrue();

      component.onPickPromotion('FREE50');

      // While FREE50 is pending the banner must not show "FREE50 applied" with MIN100K's amount.
      expect(component.discountApplied).toBeFalse();
      expect(component.discountAmount).toBe(0);
      expect(checkoutServiceSpy.clearDiscount.calls.count()).toBeGreaterThan(0);

      second.error({ status: 422, error: 'Invalid discount' });

      expect(component.discountApplied).toBeFalse();
      expect(component.discountError).not.toBe('');
      expect(checkoutServiceSpy.applyDiscount.calls.allArgs().map((args) => args[0])).toEqual(['MIN100K']);
    });

    it('ignores an answer computed for the cart as it was before the customer changed it', () => {
      component.onPickPromotion('FREE50');
      expect(component.discountLoading).toBeTrue();

      linesSubject.next([{ accessoryId: 'item-1', name: 'Áo thun', unitPrice: 50000, quantity: 2 }]);
      first.next(validated('FREE50', 50000));

      expect(component.discountApplied).toBeFalse();
      expect(component.discountLoading).toBeFalse();
      expect(checkoutServiceSpy.applyDiscount.calls.count()).toBe(0);
    });
  });
});
