/** Mirror of service.order `PromotionType`. */
export type TentativePromotionType = 'COMBO' | 'COUPON' | 'NONE';

/**
 * One line of the winning promotion doc. For a `COUPON` line `name` is the code as stored in the config (casing may
 * differ from what the customer typed), and `error`/`errorMessage` explain why the code was not applied.
 */
export interface TentativePromotionDetailResponse {
  name: string;
  priceDiscount: number;
  type: TentativePromotionType;
  error?: boolean;
  errorMessage?: string | null;
}

export interface TentativePriceResponse {
  priceTotal: number;
  priceDiscount: number;
  shipPrice: number;
  /**
   * Always an array from the backend; absent whenever the price did not come from it (component default state,
   * the FE zero-ticket shortcut in `OrderService.tentativePrice`, a booking session saved before this field existed).
   */
  promotionDetails?: TentativePromotionDetailResponse[];
}
