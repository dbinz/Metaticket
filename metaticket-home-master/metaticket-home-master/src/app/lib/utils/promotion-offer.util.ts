import {
  PromotionOfferResponse,
  PromotionOfferTierResponse,
  TentativePromotionDetailResponse,
} from 'src/app/lib/entities';
import { formatPriceVnd } from './price.util';

/** Customer-facing wording of one public promotion code, built from its real conditions (no free-text blurb). */
export interface PromotionOfferDescription {
  /** `-10%`, `-50.000₫`, `Ưu đãi theo bậc`, or empty when the value is unusable. */
  discountText: string;
  conditions: string[];
  validityText: string | null;
}

export interface PromotionCodeResult {
  ok: boolean;
  message: string;
}

const ACCESSORY_GENERIC_REJECTION = 'Mã không hợp lệ hoặc đã hết hạn.';
// Largest |epoch ms| a JS Date accepts; Intl throws "Invalid time value" beyond it.
const MAX_DATE_EPOCH_MS = 8.64e15;

let vnDateTimeFormat: Intl.DateTimeFormat | undefined;

/** Same normalisation the backends use to match codes (trim + upper case), so FE comparisons agree with checkout. */
export function normalizePromotionCode(raw: string | null | undefined): string {
  return typeof raw === 'string' ? raw.trim().toUpperCase() : '';
}

/**
 * Keeps only offers the panel can actually use: the response may be `null`/not an array on an older or failing
 * backend, and duplicate codes (differing only by case) would render as two identical cards.
 */
export function sanitizePromotionOffers(raw: unknown): PromotionOfferResponse[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  const seenCodes = new Set<string>();
  const offers: PromotionOfferResponse[] = [];
  for (const item of raw as unknown[]) {
    if (item === null || typeof item !== 'object') {
      continue;
    }
    const candidate = item as PromotionOfferResponse;
    const key = normalizePromotionCode(candidate.code);
    if (!key || seenCodes.has(key)) {
      continue;
    }
    seenCodes.add(key);
    offers.push(candidate);
  }
  return offers;
}

/** Discount label, conditions and validity window of one offer, in Vietnamese and always in Vietnam time. */
export function describePromotionOffer(offer: PromotionOfferResponse): PromotionOfferDescription {
  const isTiered = offer.discountType === 'TIERED';
  const conditions: string[] = [];

  // Tiered pricing uses each tier's own floor/cap on the backend, so the offer-level ones would be misleading.
  if (!isTiered) {
    const minOrderAmount = positiveOrNull(offer.minOrderAmount);
    if (minOrderAmount !== null) {
      conditions.push(`Đơn tối thiểu ${formatPriceVnd(minOrderAmount)}`);
    }
    const cap = positiveOrNull(offer.maxDiscountAmount);
    if (cap !== null && isCapRelevant(offer, cap)) {
      conditions.push(`Giảm tối đa ${formatPriceVnd(cap)}`);
    }
  } else {
    for (const tier of sortedValidTiers(offer.tiers)) {
      conditions.push(describeTier(tier));
    }
  }
  if (offer.limitedQuantity === true) {
    // Remaining usage is deliberately not exposed by the API; only say that it is limited.
    conditions.push('Số lượng có hạn');
  }

  return {
    discountText: discountTextOf(offer),
    conditions,
    validityText: validityTextOf(offer.startAt, offer.endAt),
  };
}

/**
 * Result of the code the customer typed, read from the tentative price of a ticket order. The backend keeps only the
 * promotion doc with the largest discount, so a missing COUPON line means either a wrong code or a better promotion.
 * Returns null when no code was entered or nothing was priced (no details), i.e. there is no verdict to report.
 */
export function describeTicketCouponResult(
  code: string | null | undefined,
  details: TentativePromotionDetailResponse[] | null | undefined,
): PromotionCodeResult | null {
  const key = normalizePromotionCode(code);
  // The backend always sends an array; it is missing only when the price did not come from it, so there is nothing to report.
  if (!key || !Array.isArray(details)) {
    return null;
  }
  const line = (details as unknown[]).find(
    (item): item is TentativePromotionDetailResponse =>
      item !== null &&
      typeof item === 'object' &&
      (item as TentativePromotionDetailResponse).type === 'COUPON' &&
      normalizePromotionCode((item as TentativePromotionDetailResponse).name) === key,
  );
  if (!line) {
    return { ok: false, message: 'Mã không hợp lệ hoặc đơn đang được áp dụng ưu đãi tốt hơn' };
  }
  if (line.error === true) {
    const serverMessage = typeof line.errorMessage === 'string' ? line.errorMessage.trim() : '';
    return { ok: false, message: serverMessage || 'Không thể áp dụng mã này' };
  }
  return { ok: true, message: `Đã áp dụng mã ${key}` };
}

/**
 * Result for a code whose price check itself failed (5xx/network): it is neither applied nor refused, and the
 * previous code's "Đã áp dụng" must not stay next to it. `null` when no code was sent.
 */
export function describeTicketCouponCheckFailure(code: string | null | undefined): PromotionCodeResult | null {
  const key = normalizePromotionCode(code);
  return key ? { ok: false, message: `Chưa kiểm tra được mã ${key}. Vui lòng thử lại.` } : null;
}

/**
 * Message for a 422 from accessory `discount/validate`. The endpoint gives one generic reason, so when the code is
 * one of the listed offers and the cart is below its floor we can name the condition that is not met yet.
 */
export function explainAccessoryRejection(offer: PromotionOfferResponse | undefined, subtotal: number): string {
  if (!offer) {
    return ACCESSORY_GENERIC_REJECTION;
  }
  const floor =
    offer.discountType === 'TIERED'
      ? (sortedValidTiers(offer.tiers)[0]?.minOrderAmount ?? null)
      : positiveOrNull(offer.minOrderAmount);
  if (floor !== null && floor > 0 && subtotal < floor) {
    return `Đơn hàng cần tối thiểu ${formatPriceVnd(floor)} để dùng mã này`;
  }
  return ACCESSORY_GENERIC_REJECTION;
}

function discountTextOf(offer: PromotionOfferResponse): string {
  if (offer.discountType === 'TIERED') {
    return 'Ưu đãi theo bậc';
  }
  const value = positiveOrNull(offer.discountValue);
  if (value === null) {
    return '';
  }
  if (offer.discountType === 'PERCENT') {
    return `-${value}%`;
  }
  return offer.discountType === 'AMOUNT' ? `-${formatPriceVnd(value)}` : '';
}

/** A cap below a fixed amount really limits it; a cap at/above it is noise. Percent caps always matter. */
function isCapRelevant(offer: PromotionOfferResponse, cap: number): boolean {
  if (offer.discountType !== 'AMOUNT') {
    return true;
  }
  const value = positiveOrNull(offer.discountValue);
  return value === null || cap < value;
}

function sortedValidTiers(tiers: PromotionOfferTierResponse[] | null | undefined): PromotionOfferTierResponse[] {
  if (!Array.isArray(tiers)) {
    return [];
  }
  return (tiers as unknown[])
    .filter((tier): tier is PromotionOfferTierResponse => isValidTier(tier))
    .sort((a, b) => a.minOrderAmount - b.minOrderAmount);
}

function isValidTier(tier: unknown): boolean {
  if (tier === null || typeof tier !== 'object') {
    return false;
  }
  const candidate = tier as PromotionOfferTierResponse;
  return (
    finiteOrNull(candidate.minOrderAmount) !== null &&
    candidate.minOrderAmount >= 0 &&
    positiveOrNull(candidate.discountValue) !== null &&
    (candidate.discountType === 'PERCENT' || candidate.discountType === 'AMOUNT')
  );
}

function describeTier(tier: PromotionOfferTierResponse): string {
  const threshold = `Đơn từ ${formatPriceVnd(tier.minOrderAmount)}`;
  if (tier.discountType === 'AMOUNT') {
    return `${threshold}: giảm ${formatPriceVnd(tier.discountValue)}`;
  }
  const cap = positiveOrNull(tier.maxDiscountAmount);
  return `${threshold}: giảm ${tier.discountValue}%${cap === null ? '' : ` (tối đa ${formatPriceVnd(cap)})`}`;
}

function validityTextOf(startAtRaw: unknown, endAtRaw: unknown): string | null {
  const startAt = epochOrNull(startAtRaw);
  const endAt = epochOrNull(endAtRaw);
  if (startAt !== null && endAt !== null) {
    return `Áp dụng từ ${formatVnDateTime(startAt)} đến ${formatVnDateTime(endAt)}`;
  }
  if (endAt !== null) {
    return `Hạn dùng đến ${formatVnDateTime(endAt)}`;
  }
  return startAt !== null ? `Áp dụng từ ${formatVnDateTime(startAt)}` : null;
}

/** "HH:mm dd/MM/yyyy" in Asia/Ho_Chi_Minh whatever the browser timezone is (codes expire at VN wall-clock times). */
function formatVnDateTime(epochMs: number): string {
  // Built lazily: this util is re-exported by the shared barrel, so a module-level formatter would cost every page.
  vnDateTimeFormat ??= new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
  const parts: Partial<Record<Intl.DateTimeFormatPartTypes, string>> = {};
  for (const part of vnDateTimeFormat.formatToParts(epochMs)) {
    parts[part.type] = part.value;
  }
  return `${parts.hour}:${parts.minute} ${parts.day}/${parts.month}/${parts.year}`;
}

function finiteOrNull(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function positiveOrNull(value: unknown): number | null {
  const numeric = finiteOrNull(value);
  return numeric !== null && numeric > 0 ? numeric : null;
}

function epochOrNull(value: unknown): number | null {
  const numeric = finiteOrNull(value);
  return numeric !== null && Math.abs(numeric) <= MAX_DATE_EPOCH_MS ? numeric : null;
}
