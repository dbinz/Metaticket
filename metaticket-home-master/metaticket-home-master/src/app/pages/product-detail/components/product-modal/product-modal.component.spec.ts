import { Router } from '@angular/router';
import { ProductModalComponent } from './product-modal.component';
import { AccessoryResponse, AccessoryShowResponse } from 'src/app/lib/entities';
import { AccessoryService, AuthService, NotificationService } from 'src/app/lib/services';
import {
  AccessoryCartQuery,
  AccessoryCartResult,
  AccessoryCartService,
  AccessoryCheckoutService,
} from 'src/app/lib/stores';

/**
 * Unit tests for the product picker popup's "Add to cart" gating and the
 * `variantKey` recorded when a line is added.
 *
 * Validates: Requirements 2.3 (add-to-cart disabled until a variant is chosen),
 * 2.4 (enabled once a variant is chosen), 2.2 (single-SKU always enabled), plus
 * the resulting line recording a `variantKey` iff the product has variants
 * (Req 2.6 / 2.7).
 *
 * The gating (`canAddToCart`) and add flow (`onAddToCart`) are pure view logic
 * over the bound product, so the component is exercised directly with
 * lightweight test doubles rather than a full TestBed render.
 */
describe('ProductModalComponent — variant add-to-cart gating', () => {
  let component: ProductModalComponent;
  let cartService: jasmine.SpyObj<{ addLine: (...args: unknown[]) => AccessoryCartResult }>;
  let checkoutService: jasmine.SpyObj<{ clearDiscount: () => void }>;
  let notification: jasmine.SpyObj<{
    showSuccessNotification: (m: string) => void;
    showErrorNotification: (m: string) => void;
  }>;
  let authService: jasmine.SpyObj<{ isAuthenticated: () => boolean }>;
  let router: jasmine.SpyObj<{ navigate: (...args: unknown[]) => Promise<boolean> }>;

  const variantProduct: AccessoryResponse = {
    id: 'tshirt',
    sku: 'TS-1',
    name: 'Áo thun',
    price: 250000,
    stock: 0,
    active: true,
    variants: [
      { key: 'M', label: 'Size M', stock: 10 },
      { key: 'L', label: 'Size L', stock: 5 },
    ],
  };

  const singleSkuProduct: AccessoryResponse = {
    id: 'poster',
    sku: 'PS-1',
    name: 'Poster',
    price: 90000,
    stock: 20,
    active: true,
    variants: [],
  };

  const show: AccessoryShowResponse = {
    id: 'show-1',
    code: 'TF2026',
    name: 'The Flob',
    status: 'Open',
    accessories: [variantProduct, singleSkuProduct],
  };

  function createComponent(product: AccessoryResponse): ProductModalComponent {
    const cartQuery = jasmine.createSpyObj<AccessoryCartQuery>('AccessoryCartQuery', ['getValue']);
    const accessoryService = jasmine.createSpyObj<AccessoryService>('AccessoryService', ['getAccessory']);
    const c = new ProductModalComponent(
      cartService as unknown as AccessoryCartService,
      cartQuery,
      checkoutService as unknown as AccessoryCheckoutService,
      accessoryService,
      notification as unknown as NotificationService,
      authService as unknown as AuthService,
      router as unknown as Router,
    );
    c.show = show;
    c.product = product;
    return c;
  }

  beforeEach(() => {
    cartService = jasmine.createSpyObj<{ addLine: (...args: unknown[]) => AccessoryCartResult }>(
      'AccessoryCartService',
      ['addLine'],
    );
    cartService.addLine.and.returnValue({ ok: true });
    checkoutService = jasmine.createSpyObj<{ clearDiscount: () => void }>('AccessoryCheckoutService', [
      'clearDiscount',
    ]);
    notification = jasmine.createSpyObj<{
      showSuccessNotification: (m: string) => void;
      showErrorNotification: (m: string) => void;
    }>('NotificationService', ['showSuccessNotification', 'showErrorNotification']);
    // A logged-in customer by default so the add-to-cart login gate is satisfied.
    authService = jasmine.createSpyObj<{ isAuthenticated: () => boolean }>('AuthService', ['isAuthenticated']);
    authService.isAuthenticated.and.returnValue(true);
    router = jasmine.createSpyObj<{ navigate: (...args: unknown[]) => Promise<boolean> }>('Router', ['navigate']);
    router.navigate.and.returnValue(Promise.resolve(true));
  });

  it('keeps "Add to cart" disabled for a variant product until a variant is selected (Req 2.3)', () => {
    component = createComponent(variantProduct);
    expect(component.canAddToCart).toBeFalse();
  });

  it('enables "Add to cart" once a variant is selected (Req 2.4)', () => {
    component = createComponent(variantProduct);
    component.onSelectVariant(variantProduct.variants[0]);
    expect(component.canAddToCart).toBeTrue();
  });

  it('keeps "Add to cart" enabled for a single-SKU product (Req 2.2)', () => {
    component = createComponent(singleSkuProduct);
    expect(component.canAddToCart).toBeTrue();
  });

  it('records the line WITH the selected variantKey for a variant product (Req 2.6)', () => {
    component = createComponent(variantProduct);
    component.onSelectVariant(variantProduct.variants[1]); // Size L

    component.onAddToCart();

    expect(cartService.addLine).toHaveBeenCalledTimes(1);
    const [line, showId] = cartService.addLine.calls.mostRecent().args as [
      { accessoryId: string; variantKey?: string; variantLabel?: string },
      string,
    ];
    expect(line.accessoryId).toBe('tshirt');
    expect(line.variantKey).toBe('L');
    expect(line.variantLabel).toBe('Size L');
    expect(showId).toBe('show-1');
  });

  it('records the line WITHOUT a variantKey for a single-SKU product (Req 2.7)', () => {
    component = createComponent(singleSkuProduct);

    component.onAddToCart();

    expect(cartService.addLine).toHaveBeenCalledTimes(1);
    const [line] = cartService.addLine.calls.mostRecent().args as [{ accessoryId: string; variantKey?: string }];
    expect(line.accessoryId).toBe('poster');
    expect(line.variantKey).toBeUndefined();
  });

  it('does not add a variant product to the cart while no variant is selected (Req 2.3)', () => {
    component = createComponent(variantProduct);
    component.onAddToCart();
    expect(cartService.addLine).not.toHaveBeenCalled();
  });

  it('requires login before adding to cart: redirects to login and adds nothing when anonymous', () => {
    authService.isAuthenticated.and.returnValue(false);
    component = createComponent(singleSkuProduct);

    component.onAddToCart();

    expect(cartService.addLine).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalled();
  });
});
