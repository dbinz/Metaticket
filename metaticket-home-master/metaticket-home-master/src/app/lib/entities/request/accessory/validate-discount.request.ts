import { AccessoryCheckoutLineRequest } from './checkout.request';

/**
 * Discount preview request (`POST accessory/discount/validate`).
 * [discountCode] is optional — omit/blank to resolve Auto campaigns only
 * (Tiered / EarlyBird); when set, BE picks the best of that coupon + autos.
 */
export interface ValidateDiscountRequest {
  discountCode?: string;
  lines: AccessoryCheckoutLineRequest[];
}
