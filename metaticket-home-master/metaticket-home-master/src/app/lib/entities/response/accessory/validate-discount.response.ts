/** Discount type mirror of the backend `DiscountType` enum. */
export type AccessoryDiscountType = 'Percentage' | 'FixedAmount';

/**
 * Discount preview result (Req 4.1). Returns the resolved discount `amount` and
 * the resulting order `total` (`= subtotal - amount`, always `0 <= amount <= subtotal`).
 * No stored usage count is mutated by the preview (Req 4.7).
 */
export interface ValidateDiscountResponse {
  code: string;
  type: AccessoryDiscountType;
  /** Configured discount value: percent for `Percentage`, fixed VND for `FixedAmount`. */
  value: number;
  amount: number;
  subtotal: number;
  total: number;
}
