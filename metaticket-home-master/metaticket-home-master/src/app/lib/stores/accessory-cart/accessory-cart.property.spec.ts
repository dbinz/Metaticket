import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import * as fc from 'fast-check';

import { AuthService } from 'src/app/lib/services';
import { AccessoryCartService } from './accessory-cart.service';
import { AccessoryCartQuery } from './accessory-cart.query';
import { ACCESSORY_CART_MAX_QUANTITY, ACCESSORY_CART_MIN_QUANTITY, CartLine } from './accessory-cart.store';

/**
 * Property 14 — Cart line quantity positivity.
 *
 * Validates: Requirements 3.3
 *
 * For ANY sequence of add / adjust / remove operations applied to the client
 * cart, every line that remains stored must carry an integer quantity within
 * [1, 99] (qty < 1 removes the line; qty > 99 is rejected and the previous
 * quantity is retained). This is the universal invariant behind Req 3.3.
 */
describe('AccessoryCartService — Property 14: cart line quantity positivity', () => {
  const SHOW_ID = 'show-1';

  // Small pools keep operations colliding on the same lines so merges,
  // rejections and removals are actually exercised.
  const accessoryIdArb = fc.constantFrom('a', 'b', 'c');
  const variantKeyArb = fc.constantFrom<string | undefined>(undefined, 'M', 'L');
  // Deliberately span below 1, inside range, and above 99 to drive clamping,
  // removal and rejection paths. Include a non-integer to exercise truncation.
  const quantityArb = fc.oneof(
    fc.integer({ min: -5, max: 120 }),
    fc.constantFrom(0, 1, 99, 100, -1),
    fc.double({ min: -5, max: 120, noNaN: true }),
  );

  type Op =
    | { kind: 'add'; accessoryId: string; variantKey?: string; quantity: number }
    | { kind: 'update'; accessoryId: string; variantKey?: string; quantity: number }
    | { kind: 'remove'; accessoryId: string; variantKey?: string };

  const opArb: fc.Arbitrary<Op> = fc.oneof(
    fc.record({
      kind: fc.constant('add' as const),
      accessoryId: accessoryIdArb,
      variantKey: variantKeyArb,
      quantity: quantityArb,
    }),
    fc.record({
      kind: fc.constant('update' as const),
      accessoryId: accessoryIdArb,
      variantKey: variantKeyArb,
      quantity: quantityArb,
    }),
    fc.record({
      kind: fc.constant('remove' as const),
      accessoryId: accessoryIdArb,
      variantKey: variantKeyArb,
    }),
  );

  let service: AccessoryCartService;
  let query: AccessoryCartQuery;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { getUserId: (): string => 'user-1', isAuthenticated: (): boolean => true, authStatus$: of(true) },
        },
      ],
    });
    service = TestBed.inject(AccessoryCartService);
    query = TestBed.inject(AccessoryCartQuery);
  });

  afterEach(() => {
    service.clear();
    localStorage.clear();
  });

  function applyOp(op: Op): void {
    switch (op.kind) {
      case 'add': {
        const line: CartLine = {
          accessoryId: op.accessoryId,
          variantKey: op.variantKey,
          quantity: op.quantity,
        };
        service.addLine(line, SHOW_ID);
        break;
      }
      case 'update':
        service.updateQuantity(op.accessoryId, op.variantKey, op.quantity);
        break;
      case 'remove':
        service.removeLine(op.accessoryId, op.variantKey);
        break;
    }
  }

  it('keeps every stored line quantity an integer within [1, 99] for any op sequence', () => {
    fc.assert(
      fc.property(fc.array(opArb, { minLength: 1, maxLength: 40 }), (ops) => {
        // Fresh cart per sequence so runs are independent.
        service.clear();

        ops.forEach(applyOp);

        const lines = query.getValue().lines;
        for (const line of lines) {
          expect(Number.isInteger(line.quantity)).toBeTrue();
          expect(line.quantity).toBeGreaterThanOrEqual(ACCESSORY_CART_MIN_QUANTITY);
          expect(line.quantity).toBeLessThanOrEqual(ACCESSORY_CART_MAX_QUANTITY);
        }
      }),
      { numRuns: 50 },
    );
  });
});
