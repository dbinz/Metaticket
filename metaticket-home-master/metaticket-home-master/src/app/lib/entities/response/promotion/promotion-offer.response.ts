/** Mirror of `PromotionOffer.discountType` (service.order `api` source set), shared by the ticket and accessory flows. */
export type PromotionOfferDiscountType = 'PERCENT' | 'AMOUNT' | 'TIERED';

/** One spend threshold of a `TIERED` offer: orders from `minOrderAmount` VND get this tier's discount. */
export interface PromotionOfferTierResponse {
  minOrderAmount: number;
  discountType: PromotionOfferDiscountType;
  /** Percent for `PERCENT`, VND for `AMOUNT`. */
  discountValue: number;
  maxDiscountAmount?: number | null;
}

/**
 * A publicly listed promotion code (`order/promotions/offers`, `accessory/discount/offers`).
 * Display data only: the server never sends internal ids or the remaining usage count.
 */
export interface PromotionOfferResponse {
  code: string;
  /** Ticket: campaign name (falls back to the code); accessory: the code itself. */
  title: string;
  discountType: PromotionOfferDiscountType;
  /** Percent for `PERCENT`, VND for `AMOUNT`, 0 for `TIERED`. */
  discountValue: number;
  maxDiscountAmount?: number | null;
  minOrderAmount?: number | null;
  tiers?: PromotionOfferTierResponse[];
  /** Epoch ms. */
  startAt?: number | null;
  /** Epoch ms. */
  endAt?: number | null;
  limitedQuantity?: boolean;
}
