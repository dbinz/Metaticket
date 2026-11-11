/**
 * Response shapes for the accessory storefront ("Sản phẩm"), served by
 * `service.accessory` via the nginx `public/accessory/*` path prefix.
 *
 * These mirror the public projection of the backend data models
 * (`AccessoryShowDocument`, `AccessoryDocument`, `AccessoryVariant`) described in
 * the accessory-sales design. Ids are the public-facing `Checksum`-encoded ids;
 * raw MongoDB ObjectIds never leave the service.
 */

/**
 * A show session (venue/date/city) the accessory show runs at, shown on the storefront banner.
 * `date` is epoch millis (matching the rest of the app's date handling).
 */
export interface AccessoryShowSessionResponse {
  id: string; // stable session id; echoed back at checkout as `showSessionId`
  city: string; // e.g. "TP.HCM"
  venue: string; // e.g. "Nhà thi đấu Rạch Miễu"
  date: number; // epoch millis
}

/** A selectable option of an accessory. Stock `-1` denotes unlimited. */
export interface AccessoryVariantResponse {
  key: string; // stable id within the accessory, e.g. "M-BLACK"
  label: string; // combined display label, e.g. "M · Đen"
  stock: number; // remaining units; -1 = unlimited
  /** Keyed option values (`attr1` / `attr2`). */
  options?: Record<string, string>;
  /** Required when the product has variants; drives gallery + cart thumbnail. */
  imageUrl?: string;
  active?: boolean; // false = combination withdrawn from sale (disabled on storefront)
}

/** Named product attribute dimension (max 2). */
export interface AccessoryAttributeDefResponse {
  key: string; // "attr1" | "attr2"
  name: string; // display label
}

/** A purchasable product belonging to an accessory show. */
export interface AccessoryResponse {
  id: string; // encoded id (used as cart line accessoryId)
  sku: string;
  name: string;
  description?: string;
  imageUrl?: string; // primary image (grid card / cart line thumbnail)
  imageUrls?: string[]; // full gallery for the product-detail popup; falls back to [imageUrl]
  price: number; // VND
  currency?: string;
  stock: number; // used ONLY when `variants` is empty; -1 = unlimited
  /** Backend flag; storefront also treats non-empty `variants` as variant product. */
  hasVariants?: boolean;
  /** Up to 2 named dimensions for independent selectors. */
  attributes?: AccessoryAttributeDefResponse[];
  /** Min units of this product per checkout; omit/null = no product minimum. */
  minOrderQty?: number | null;
  /** Max units of this product per checkout; omit/null = no product maximum. */
  maxOrderQty?: number | null;
  /** Max units per customer for this product; omit/null = no per-user cap. */
  maxPerUserQty?: number | null;
  /** Units already counted toward maxPerUserQty for the logged-in customer. */
  myPurchasedQty?: number | null;
  variants: AccessoryVariantResponse[]; // empty = single SKU; non-empty = pick one
  /** Public catalog only returns active products; keep optional for older payloads. */
  active?: boolean;
}

/** An accessory show grouping a set of accessories, returned only while `Open`. */
export interface AccessoryShowResponse {
  id: string; // encoded id
  code: string; // human-readable code (e.g. "TF2026")
  /** Canonical slug for `/san-pham/{slug}`; empty/absent falls back to encoded id in links. */
  slug?: string;
  name: string;
  description?: string;
  bannerImageUrl?: string;
  /** Sessions (venue/date/city) shown on the storefront banner. */
  sessions?: AccessoryShowSessionResponse[];
  status?: string; // 'Open' | 'Waiting' | 'Closed'
  /** Planned sale-open epoch millis when Waiting. */
  saleOpenAt?: number | null;
  /** Pin rank 1..5 when ghim. */
  pinOrder?: number | null;
  /**
   * Lowest price (VND) among the show's active products, for the "Từ …₫" card label.
   * Absent when nothing in the catalog is priced yet.
   */
  minPrice?: number | null;
  /** Optional tip for PickupAtShow, shown when the buyer chooses that mode. */
  pickupNote?: string;
  /** Optional tip for home delivery, shown when the buyer chooses Giao tận nơi. */
  deliveryNote?: string;
  /**
   * Payment methods offered for this show, as backend `PaymentType` enum names
   * (e.g. "VietQr", "QrCode", "InternationalCard"). Present on the detail endpoint.
   * Empty/absent => the storefront shows all available methods.
   */
  allowedPaymentMethods?: string[];
  /**
   * Fulfillment modes + META Ticket pickup points. Present on the detail endpoint
   * (`GET public/accessory/show/fetch`).
   */
  fulfillment?: AccessoryFulfillmentResponse;
  /** Present on the detail endpoint (and optionally on the listing). */
  accessories?: AccessoryResponse[];
}

/** Spring-like page for `GET public/accessory/show/fetch/all?page=&size=` (0-based `number`). */
export interface AccessoryShowListPage {
  content: AccessoryShowResponse[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
  numberOfElements: number;
}

/** Fulfillment modes the storefront may offer for a show (from public detail). */
export interface AccessoryFulfillmentResponse {
  enablePickupAtShow: boolean;
  enableMetaTicketPickup: boolean;
  enableDelivery: boolean;
  /** When non-empty, only these session ids are selectable for PickupAtShow. */
  pickupAtShowSessionIds: string[];
  pickupPoints: AccessoryPickupPointResponse[];
}

/** Pickup point embedded under `fulfillment.pickupPoints`. */
export interface AccessoryPickupPointResponse {
  id: string;
  name: string;
  address: string;
  note?: string | null;
}
