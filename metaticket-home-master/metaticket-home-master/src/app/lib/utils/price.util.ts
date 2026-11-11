/** Formats a VND amount the way the storefront shows money, e.g. `120.000₫`. */
export function formatPriceVnd(value: number): string {
  return value.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' }).replace(/\s/g, '');
}

/** Compact price for a calendar day cell: 120000 → "120k", 1500000 → "1,5tr". */
export function shortPriceLabel(value: number): string {
  const format = (amount: number): string => amount.toLocaleString('vi-VN', { maximumFractionDigits: 1 });
  // Pick the unit after rounding to the shown 0.1k, otherwise 999 950 would read "1.000k" instead of "1tr".
  if (Math.round(value / 100) >= 10_000) {
    return `${format(value / 1_000_000)}tr`;
  }
  if (value >= 1_000) {
    return `${format(value / 1_000)}k`;
  }
  return `${value}đ`;
}

/**
 * "Từ …₫" label for a show / accessory-show card.
 *
 * Returns an empty string when there is no price to advertise so callers can hide the element:
 * the backend leaves `minPrice` unset for free shows and for catalogs whose products are not priced
 * yet, and rendering "Từ 0₫" there would read as a promise of a free ticket.
 */
export function minPriceLabel(minPrice?: number | null): string {
  return minPrice && minPrice > 0 ? `Từ ${formatPriceVnd(minPrice)}` : '';
}
