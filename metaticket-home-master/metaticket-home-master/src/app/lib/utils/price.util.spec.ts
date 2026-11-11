import { shortPriceLabel } from './price.util';

describe('shortPriceLabel', () => {
  it('keeps small amounts in đồng', () => {
    expect(shortPriceLabel(950)).toBe('950đ');
  });

  it('uses k below one million', () => {
    expect(shortPriceLabel(120_000)).toBe('120k');
    expect(shortPriceLabel(999_940)).toBe('999,9k');
  });

  it('switches to tr when rounding reaches one million', () => {
    expect(shortPriceLabel(999_950)).toBe('1tr');
    expect(shortPriceLabel(999_999)).toBe('1tr');
  });

  it('uses tr from one million', () => {
    expect(shortPriceLabel(1_000_000)).toBe('1tr');
    expect(shortPriceLabel(1_500_000)).toBe('1,5tr');
  });
});
