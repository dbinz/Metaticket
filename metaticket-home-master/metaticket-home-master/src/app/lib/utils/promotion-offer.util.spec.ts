import { PromotionOfferResponse, TentativePromotionDetailResponse } from 'src/app/lib/entities';
import {
  describePromotionOffer,
  describeTicketCouponResult,
  explainAccessoryRejection,
  normalizePromotionCode,
  sanitizePromotionOffers,
} from './promotion-offer.util';

/** Builds an offer with only the fields a case cares about; everything else stays "no condition". */
function offer(overrides: Partial<PromotionOfferResponse>): PromotionOfferResponse {
  return { code: 'SALE10', title: 'SALE10', discountType: 'PERCENT', discountValue: 10, ...overrides };
}

// 23:59:59 and 00:00:00 Vietnam time, written in UTC so the result cannot depend on the machine running the test.
const VN_2026_10_20_235959 = Date.UTC(2026, 9, 20, 16, 59, 59);
const VN_2026_10_21_000000 = Date.UTC(2026, 9, 20, 17, 0, 0);
const VN_2026_10_31_235959 = Date.UTC(2026, 9, 31, 16, 59, 59);

describe('describePromotionOffer', () => {
  describe('discountText', () => {
    it('shows a percent offer as "-N%"', () => {
      expect(describePromotionOffer(offer({ discountType: 'PERCENT', discountValue: 10 })).discountText).toBe('-10%');
    });

    it('shows an amount offer in VND the way the storefront prints prices', () => {
      expect(describePromotionOffer(offer({ discountType: 'AMOUNT', discountValue: 50000 })).discountText).toBe(
        '-50.000₫',
      );
    });

    it('shows a tiered offer with a fixed label', () => {
      expect(describePromotionOffer(offer({ discountType: 'TIERED', discountValue: 0 })).discountText).toBe(
        'Ưu đãi theo bậc',
      );
    });

    it('prints nothing instead of "-NaN%" / "--5.000₫" for broken values or an unknown type', () => {
      const broken = [
        offer({ discountValue: 'abc' as unknown as number }),
        offer({ discountType: 'AMOUNT', discountValue: -5000 }),
        offer({ discountValue: 0 }),
        offer({ discountType: 'BOGUS' as unknown as 'PERCENT', discountValue: 10 }),
        offer({ discountValue: null as unknown as number }),
      ];
      for (const item of broken) {
        expect(describePromotionOffer(item).discountText).withContext(JSON.stringify(item)).toBe('');
      }
    });
  });

  describe('conditions', () => {
    it('lists min order, max discount, then limited quantity, in that order', () => {
      const description = describePromotionOffer(
        offer({ minOrderAmount: 200000, maxDiscountAmount: 100000, limitedQuantity: true }),
      );
      expect(description.conditions).toEqual(['Đơn tối thiểu 200.000₫', 'Giảm tối đa 100.000₫', 'Số lượng có hạn']);
    });

    it('has no conditions when the offer has none', () => {
      expect(describePromotionOffer(offer({ minOrderAmount: 0, maxDiscountAmount: null })).conditions).toEqual([]);
    });

    it('hides the cap of an amount offer when the cap cannot bite, shows it when it can', () => {
      const capAbove = offer({ discountType: 'AMOUNT', discountValue: 50000, maxDiscountAmount: 100000 });
      const capBelow = offer({ discountType: 'AMOUNT', discountValue: 50000, maxDiscountAmount: 30000 });
      expect(describePromotionOffer(capAbove).conditions).toEqual([]);
      expect(describePromotionOffer(capBelow).conditions).toEqual(['Giảm tối đa 30.000₫']);
    });

    it('describes every tier in ascending threshold order and ignores the offer-level min/max', () => {
      const tiered = offer({
        discountType: 'TIERED',
        discountValue: 0,
        // Offer-level floor/cap are not used by tiered pricing on the backend, so they must not be advertised.
        minOrderAmount: 999000,
        maxDiscountAmount: 1000,
        tiers: [
          { minOrderAmount: 1000000, discountType: 'PERCENT', discountValue: 15 },
          { minOrderAmount: 300000, discountType: 'AMOUNT', discountValue: 20000 },
          { minOrderAmount: 500000, discountType: 'PERCENT', discountValue: 10, maxDiscountAmount: 80000 },
        ],
        limitedQuantity: true,
      });
      expect(describePromotionOffer(tiered).conditions).toEqual([
        'Đơn từ 300.000₫: giảm 20.000₫',
        'Đơn từ 500.000₫: giảm 10% (tối đa 80.000₫)',
        'Đơn từ 1.000.000₫: giảm 15%',
        'Số lượng có hạn',
      ]);
    });

    it('skips broken tiers and a non-array tiers field without throwing', () => {
      const tiered = offer({
        discountType: 'TIERED',
        tiers: [
          { minOrderAmount: 'x' as unknown as number, discountType: 'PERCENT', discountValue: 5 },
          { minOrderAmount: 100000, discountType: 'PERCENT', discountValue: -1 },
          null as unknown as { minOrderAmount: number; discountType: 'PERCENT'; discountValue: number },
          { minOrderAmount: 200000, discountType: 'AMOUNT', discountValue: 10000 },
        ],
      });
      expect(describePromotionOffer(tiered).conditions).toEqual(['Đơn từ 200.000₫: giảm 10.000₫']);
      expect(
        describePromotionOffer(offer({ discountType: 'TIERED', tiers: 'abc' as unknown as [] })).conditions,
      ).toEqual([]);
    });
  });

  describe('validityText (always Vietnam time)', () => {
    it('shows both ends of the window', () => {
      const description = describePromotionOffer(offer({ startAt: VN_2026_10_21_000000, endAt: VN_2026_10_31_235959 }));
      expect(description.validityText).toBe('Áp dụng từ 00:00 21/10/2026 đến 23:59 31/10/2026');
    });

    it('shows only the end when there is no start', () => {
      expect(describePromotionOffer(offer({ endAt: VN_2026_10_20_235959 })).validityText).toBe(
        'Hạn dùng đến 23:59 20/10/2026',
      );
    });

    it('shows only the start when there is no end', () => {
      expect(describePromotionOffer(offer({ startAt: VN_2026_10_21_000000 })).validityText).toBe(
        'Áp dụng từ 00:00 21/10/2026',
      );
    });

    it('is null without a window, and treats unusable timestamps as absent', () => {
      expect(describePromotionOffer(offer({})).validityText).toBeNull();
      expect(describePromotionOffer(offer({ startAt: null, endAt: null })).validityText).toBeNull();
      expect(
        describePromotionOffer(offer({ startAt: NaN, endAt: 'abc' as unknown as number })).validityText,
      ).toBeNull();
      // Beyond the JS Date range: Intl would throw "Invalid time value".
      expect(describePromotionOffer(offer({ endAt: 9e15 })).validityText).toBeNull();
    });

    it('formats extreme but valid timestamps without throwing', () => {
      const description = describePromotionOffer(offer({ startAt: 0, endAt: 253402300799000 }));
      expect(description.validityText).toContain('01/01/1970');
      expect(description.validityText).toContain('01/01/10000');
    });
  });
});

describe('describeTicketCouponResult', () => {
  const coupon = (overrides: Partial<TentativePromotionDetailResponse>): TentativePromotionDetailResponse => ({
    name: 'Sale10',
    priceDiscount: 10000,
    type: 'COUPON',
    ...overrides,
  });

  it('returns null when no code was entered', () => {
    expect(describeTicketCouponResult('', [coupon({})])).toBeNull();
    expect(describeTicketCouponResult('   ', [coupon({})])).toBeNull();
    expect(describeTicketCouponResult(null, [coupon({})])).toBeNull();
  });

  it('reports success when the COUPON line matches the code, ignoring case and surrounding spaces', () => {
    expect(describeTicketCouponResult('  sAlE10 ', [coupon({})])).toEqual({
      ok: true,
      message: 'Đã áp dụng mã SALE10',
    });
  });

  it('reports the server error message of a matching COUPON line', () => {
    const details = [coupon({ priceDiscount: 0, error: true, errorMessage: 'Mã đã được sử dụng hết' })];
    expect(describeTicketCouponResult('SALE10', details)).toEqual({ ok: false, message: 'Mã đã được sử dụng hết' });
  });

  it('falls back to a fixed message when a matching error line has no message', () => {
    const details = [coupon({ error: true, errorMessage: '  ' })];
    expect(describeTicketCouponResult('sale10', details)).toEqual({ ok: false, message: 'Không thể áp dụng mã này' });
  });

  it('explains a missing COUPON line as "invalid or a better promotion won"', () => {
    const expected = { ok: false, message: 'Mã không hợp lệ hoặc đơn đang được áp dụng ưu đãi tốt hơn' };
    expect(describeTicketCouponResult('SALE10', [])).toEqual(expected);
    expect(describeTicketCouponResult('SALE10', [coupon({ type: 'COMBO' })])).toEqual(expected);
    expect(describeTicketCouponResult('SALE10', [coupon({ name: 'OTHER' })])).toEqual(expected);
  });

  it('reports nothing when the price carries no promotion details (local zero-ticket shortcut)', () => {
    // The backend always sends an array; only the FE shortcut without tickets omits it, so there is no verdict yet.
    expect(describeTicketCouponResult('SALE10', undefined)).toBeNull();
    expect(describeTicketCouponResult('SALE10', null)).toBeNull();
    expect(describeTicketCouponResult('SALE10', 'oops' as unknown as [])).toBeNull();
  });
});

describe('explainAccessoryRejection', () => {
  const generic = 'Mã không hợp lệ hoặc đã hết hạn.';

  it('keeps the generic message for a code that is not in the public list', () => {
    expect(explainAccessoryRejection(undefined, 100000)).toBe(generic);
  });

  it('names the missing order floor when the cart is below it', () => {
    expect(explainAccessoryRejection(offer({ minOrderAmount: 200000 }), 150000)).toBe(
      'Đơn hàng cần tối thiểu 200.000₫ để dùng mã này',
    );
  });

  it('keeps the generic message once the floor is met or when there is no floor', () => {
    expect(explainAccessoryRejection(offer({ minOrderAmount: 200000 }), 200000)).toBe(generic);
    expect(explainAccessoryRejection(offer({ minOrderAmount: 0 }), 0)).toBe(generic);
    expect(explainAccessoryRejection(offer({ minOrderAmount: null }), 1)).toBe(generic);
  });

  it('uses the lowest tier as the floor of a tiered code', () => {
    const tiered = offer({
      discountType: 'TIERED',
      minOrderAmount: 0,
      tiers: [
        { minOrderAmount: 500000, discountType: 'PERCENT', discountValue: 10 },
        { minOrderAmount: 300000, discountType: 'AMOUNT', discountValue: 20000 },
      ],
    });
    expect(explainAccessoryRejection(tiered, 100000)).toBe('Đơn hàng cần tối thiểu 300.000₫ để dùng mã này');
    expect(explainAccessoryRejection(tiered, 300000)).toBe(generic);
  });
});

describe('normalizePromotionCode', () => {
  it('trims and upper-cases, and maps missing input to an empty string', () => {
    expect(normalizePromotionCode('  sale 10\t')).toBe('SALE 10');
    expect(normalizePromotionCode(undefined)).toBe('');
    expect(normalizePromotionCode(42 as unknown as string)).toBe('');
  });
});

describe('sanitizePromotionOffers', () => {
  it('returns an empty list for anything that is not an array', () => {
    for (const raw of [null, undefined, 'abc', 42, { code: 'X' }]) {
      expect(sanitizePromotionOffers(raw)).withContext(String(raw)).toEqual([]);
    }
  });

  it('drops entries without a usable code and keeps the first of codes that differ only by case/space', () => {
    const first = offer({ code: 'Sale10', title: 'first' });
    const raw: unknown[] = [
      null,
      'x',
      { title: 'no code' },
      offer({ code: '   ' }),
      first,
      offer({ code: ' SALE10 ' }),
    ];
    expect(sanitizePromotionOffers(raw)).toEqual([first]);
  });
});
