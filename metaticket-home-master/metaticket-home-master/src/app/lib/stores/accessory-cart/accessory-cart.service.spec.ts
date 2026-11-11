import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { storageKeys } from 'src/app/lib/constants';
import { AuthService } from 'src/app/lib/services';
import { AccessoryCartService } from './accessory-cart.service';
import { AccessoryCartQuery } from './accessory-cart.query';
import { Cart } from './accessory-cart.store';

/**
 * Unit tests for the client cart's restore / clear behaviour and variant-vs
 * single-SKU line recording.
 *
 * Validates: Requirements 2.3, 2.4 (variant add-to-cart gating — the
 * `variantKey` recorded on add), 3.4 (clear on successful checkout), 3.7
 * (restore from `localStorage` on load).
 */
describe('AccessoryCartService — restore / clear / variant recording', () => {
  const SHOW_ID = 'show-1';
  const USER_ID = 'user-1';
  // The cart persists under a per-user key (Req: cart is scoped per logged-in user).
  const CART_KEY = `${storageKeys.accessoryCart}:${USER_ID}`;

  let service: AccessoryCartService;
  let query: AccessoryCartQuery;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      // Stub AuthService: a fixed logged-in user so the cart keys under CART_KEY and never pulls in
      // Router/HttpClient (the real AuthService's deps).
      providers: [
        {
          provide: AuthService,
          useValue: { getUserId: (): string => USER_ID, isAuthenticated: (): boolean => true, authStatus$: of(true) },
        },
      ],
    });
    service = TestBed.inject(AccessoryCartService);
    query = TestBed.inject(AccessoryCartQuery);
    service.clear();
  });

  afterEach(() => {
    service.clear();
    localStorage.clear();
  });

  describe('add records variantKey iff the product has a variant (Req 2.6 / 2.7)', () => {
    it('records the selected variantKey for a variant product', () => {
      const result = service.addLine({ accessoryId: 'tshirt', variantKey: 'M', quantity: 2 }, SHOW_ID);

      expect(result.ok).toBeTrue();
      const lines = query.getValue().lines;
      expect(lines.length).toBe(1);
      expect(lines[0].accessoryId).toBe('tshirt');
      expect(lines[0].variantKey).toBe('M');
      expect(lines[0].quantity).toBe(2);
    });

    it('records no variantKey for a single-SKU product', () => {
      const result = service.addLine({ accessoryId: 'poster', quantity: 1 }, SHOW_ID);

      expect(result.ok).toBeTrue();
      const lines = query.getValue().lines;
      expect(lines.length).toBe(1);
      expect(lines[0].accessoryId).toBe('poster');
      expect(lines[0].variantKey).toBeUndefined();
    });

    it('treats different variants of the same accessory as distinct lines', () => {
      service.addLine({ accessoryId: 'tshirt', variantKey: 'M', quantity: 1 }, SHOW_ID);
      service.addLine({ accessoryId: 'tshirt', variantKey: 'L', quantity: 3 }, SHOW_ID);

      const lines = query.getValue().lines;
      expect(lines.length).toBe(2);
      expect(lines.find((l) => l.variantKey === 'M')?.quantity).toBe(1);
      expect(lines.find((l) => l.variantKey === 'L')?.quantity).toBe(3);
    });

    it('persists the added line to localStorage under the cart key (Req 3.2)', () => {
      service.addLine({ accessoryId: 'tshirt', variantKey: 'M', quantity: 2 }, SHOW_ID);

      const raw = localStorage.getItem(CART_KEY);
      expect(raw).not.toBeNull();
      const saved = JSON.parse(raw as string) as Cart;
      expect(saved.accessoryShowId).toBe(SHOW_ID);
      expect(saved.lines[0].variantKey).toBe('M');
    });
  });

  describe('restore from localStorage on load (Req 3.7)', () => {
    it('restores a persisted cart into client-side state', () => {
      const persisted: Cart = {
        accessoryShowId: SHOW_ID,
        lines: [
          { accessoryId: 'tshirt', variantKey: 'M', quantity: 2 },
          { accessoryId: 'poster', quantity: 1 },
        ],
      };
      localStorage.setItem(CART_KEY, JSON.stringify(persisted));

      service.restore();

      const state = query.getValue();
      expect(state.accessoryShowId).toBe(SHOW_ID);
      expect(state.lines.length).toBe(2);
      expect(state.lines[0].variantKey).toBe('M');
      expect(state.lines[1].variantKey).toBeUndefined();
    });

    it('leaves the cart empty when nothing is persisted', () => {
      service.restore();

      const state = query.getValue();
      expect(state.accessoryShowId).toBe('');
      expect(state.lines.length).toBe(0);
    });

    it('drops persisted lines with out-of-range quantities during restore', () => {
      const persisted: Cart = {
        accessoryShowId: SHOW_ID,
        lines: [
          { accessoryId: 'ok', quantity: 5 },
          { accessoryId: 'too-big', quantity: 500 },
          { accessoryId: 'zero', quantity: 0 },
        ],
      };
      localStorage.setItem(CART_KEY, JSON.stringify(persisted));

      service.restore();

      const lines = query.getValue().lines;
      expect(lines.length).toBe(1);
      expect(lines[0].accessoryId).toBe('ok');
    });
  });

  describe('clear on successful checkout (Req 3.4)', () => {
    it('empties the cart and removes the persisted cart from localStorage', () => {
      service.addLine({ accessoryId: 'tshirt', variantKey: 'M', quantity: 2 }, SHOW_ID);
      expect(localStorage.getItem(CART_KEY)).not.toBeNull();

      service.clear();

      const state = query.getValue();
      expect(state.accessoryShowId).toBe('');
      expect(state.lines.length).toBe(0);
      expect(localStorage.getItem(CART_KEY)).toBeNull();
    });
  });
});
