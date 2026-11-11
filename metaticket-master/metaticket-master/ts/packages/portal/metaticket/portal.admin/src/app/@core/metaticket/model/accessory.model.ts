import { CityModel } from './show.model';
import { PaymentType } from './payment.model';

/** Lifecycle of an accessory merchandise show on the storefront. */
export type AccessoryShowStatus = 'Open' | 'Waiting' | 'Closed';

/**
 * A show session with a catalog City object (same shape as ticket-show sessions)
 * and a precise datetime for pickup scheduling / storefront display.
 */
export interface AccessoryShowSessionModel {
    id: string;
    city: CityModel;
    venue: string;
    /** Epoch millis — session date and time. */
    datetime: number;
}

/** Per-show META Ticket / offline pickup location configured by the operator. */
export interface AccessoryPickupPointModel {
    id: string;
    name: string;
    address: string;
    /** Free-text tip, e.g. opening hours. */
    note?: string;
}

/**
 * Which fulfillment modes customers may pick at checkout for this show.
 * At least one of the three enable flags must be true (validated in the admin form).
 */
export interface AccessoryFulfillmentSettingsModel {
    enablePickupAtShow: boolean;
    enableMetaTicketPickup: boolean;
    enableDelivery: boolean;
    /** Session ids eligible for "Nhận tại show"; required non-empty when enablePickupAtShow. */
    pickupAtShowSessionIds: string[];
    /** Pickup points; required non-empty when enableMetaTicketPickup. */
    pickupPoints: AccessoryPickupPointModel[];
}

/** Admin view of an accessory show (collection `accessory-show`). */
export interface AccessoryShowModel {
    id: string;
    code: string;
    codePrefix: string;
    name: string;
    /** Canonical public slug (`/san-pham/{slug}`); empty/null when unset. */
    slug?: string | null;
    description?: string;
    bannerImageUrl?: string;
    sessions: AccessoryShowSessionModel[];
    status: AccessoryShowStatus;
    /** Planned sale-open epoch millis when status is Waiting; omitted when unset. */
    saleOpenAt?: number | null;
    pickupNote?: string;
    /** Optional plain-text tip for "Giao tận nơi" (show-level; not the customer's address note). */
    deliveryNote?: string;
    pgAccountCode?: string;
    allowedPaymentMethods: PaymentType[];
    mailPlugin: string;
    mailTemplateId?: string;
    fulfillment: AccessoryFulfillmentSettingsModel;
    /** Optional pin rank 1..5 for storefront merch strip; null = not pinned. */
    pinOrder?: number | null;
    /** Aggregate product count — "—" in UI when unknown. */
    productCount?: number | null;
    createdAt: number;
    lastModified: number;
}

/** Compact accessory-show card on the pin board / picker. */
export interface AccessoryPinShowSummary {
    id: string;
    name: string;
    code: string;
    status: string;
    bannerImageUrl: string;
    pinOrder?: number | null;
}

export interface AccessoryPinSlot {
    pinOrder: number;
    show: AccessoryPinShowSummary | null;
}

export interface AccessoryPinBoard {
    slots: AccessoryPinSlot[];
    maxSlots: number;
}

export interface AccessoryPinCandidatePage {
    content: AccessoryPinShowSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

/** Admin view of one accessory product (collection `accessory`). */
export interface AccessoryProductModel {
    id: string;
    accessoryShowId: string;
    sku: string;
    name: string;
    description?: string;
    imageUrl?: string;
    imageUrls: string[];
    price: number;
    currency: string;
    stock: number;
    hasVariants: boolean;
    variantCount: number;
    active: boolean;
    /** Up to 2 named dimensions (attr1/attr2). Empty for single-SKU / label-only. */
    attributes: AccessoryAttributeDefModel[];
    /** Display name for attr1. */
    attr1Name?: string | null;
    /** Display name for attr2. */
    attr2Name?: string | null;
    /** Min units per checkout; empty/null = unlimited. */
    minOrderQty?: number | null;
    /** Max units per checkout; empty/null = unlimited. */
    maxOrderQty?: number | null;
    /** Max units per user; empty/null = unlimited. */
    maxPerUserQty?: number | null;
    variants: AccessoryProductVariantModel[];
    createdAt: number;
    lastModified: number;
}

/** Named attribute dimension on a product (max 2). */
export interface AccessoryAttributeDefModel {
    key: string;
    name: string;
}

/** Variant row nested under an admin product. */
export interface AccessoryProductVariantModel {
    key: string;
    label: string;
    stock: number;
    attr1?: string;
    attr2?: string;
    /** Required when the product has variants. */
    imageUrl?: string;
    options?: Record<string, string>;
    active: boolean;
}

/** Payload used by the create/edit product form (`attributes` is derived server-side from names + variant values). */
export type AccessoryProductUpsertInput = Omit<
    AccessoryProductModel,
    'id' | 'createdAt' | 'lastModified' | 'hasVariants' | 'variantCount' | 'attributes'
> & {
    id?: string;
};

/**
 * Atomic stock adjust (plan A) — prefer `delta`; use `setTo` to leave an existing unlimited cell.
 * Setting unlimited via this API is not supported.
 */
export interface AccessoryProductStockAdjustInput {
    variantKey?: string;
    delta?: number;
    setTo?: number;
}

/** Payload used by the create/edit show form (id optional when creating). */
export type AccessoryShowUpsertInput = Omit<
    AccessoryShowModel,
    'id' | 'createdAt' | 'lastModified' | 'productCount' | 'pinOrder'
> & {
    id?: string;
};

/** Campaign kind stored in accessory-discount-code. */
export type AccessoryDiscountType = 'Percentage' | 'FixedAmount' | 'Tiered' | 'EarlyBird';

/** How the campaign is applied at checkout. */
export type AccessoryDiscountApplyMode = 'Manual' | 'Auto';

/** One spend tier inside a Tiered campaign. */
export interface AccessoryDiscountTierModel {
    minOrderAmount: number;
    type: 'Percentage' | 'FixedAmount';
    value: number;
    maxDiscountAmount?: number | null;
}

/** Admin promotion / discount campaign for one accessory show. */
export interface AccessoryDiscountModel {
    id: string;
    code: string;
    accessoryShowId: string;
    type: AccessoryDiscountType;
    value: number;
    rewardType?: AccessoryDiscountType | null;
    maxDiscountAmount?: number | null;
    minOrderAmount: number;
    tiers: AccessoryDiscountTierModel[];
    applyMode: AccessoryDiscountApplyMode;
    usageLimit?: number | null;
    usedCount: number;
    startAt?: number | null;
    endAt?: number | null;
    active: boolean;
}

/** Create/edit body for admin discount form. */
export type AccessoryDiscountUpsertInput = Omit<AccessoryDiscountModel, 'id' | 'usedCount'> & {
    id?: string;
    usedCount?: number;
};

/** Partial-success result from batch discount create / Excel import. */
export interface AccessoryDiscountBatchResult {
    created: AccessoryDiscountModel[];
    failures: Array<{ code?: string | null; message: string }>;
}

/** Lifecycle status of an accessory order (matches BE enum names). */
export type AccessoryOrderStatus =
    | 'Created'
    | 'WaitingForPayment'
    | 'PaymentProcessing'
    | 'PaymentSucceed'
    | 'PaymentFailed'
    | 'PaymentCancelled'
    | 'Expired';

/** Per-show order stats; revenue/paidOrders are PaymentSucceed only. */
export interface AccessoryOrderStatsModel {
    totalOrders: number;
    paidOrders: number;
    revenue: number;
    byStatus: Record<string, number>;
}

/** Admin list row for one accessory order (includes lines for the detail modal). */
export interface AccessoryOrderSummaryModel {
    id: string;
    code: string;
    status: AccessoryOrderStatus | string;
    fulfillment: string;
    paymentId?: string | null;
    /** Buyer user id stored at checkout. */
    customerUserId?: string | null;
    showSessionId?: string | null;
    showSessionLabel?: string | null;
    pickupPointId?: string | null;
    pickupPointName?: string | null;
    pickupPointAddress?: string | null;
    subtotal: number;
    discountAmount: number;
    discountCode?: string | null;
    total: number;
    customerName: string;
    customerPhone: string;
    customerEmail: string;
    createdAt: number;
    deliveryAddress?: string | null;
    deliveryWard?: string | null;
    deliveryCity?: string | null;
    deliveryNote?: string | null;
    lines: AccessoryOrderExportLineModel[];
}

/** GET /private/accessory/order/fetch/all payload. */
export interface AccessoryOrderListModel {
    stats: AccessoryOrderStatsModel;
    orders: AccessoryOrderSummaryModel[];
}

/** Paid-only sales summary for Thống kê. */
export interface AccessorySalesSummaryModel {
    totalOrders: number;
    paidOrders: number;
    unitsSold: number;
    grossSales: number;
    discountTotal: number;
    revenue: number;
    byStatus: Record<string, number>;
}

/** Per-product aggregation row. */
export interface AccessoryProductSalesRowModel {
    accessoryId: string;
    sku: string;
    name: string;
    unitPrice: number;
    quantity: number;
    grossSales: number;
}

/** GET /private/accessory/order/stats payload. */
export interface AccessorySalesStatisticModel {
    summary: AccessorySalesSummaryModel;
    byProduct: AccessoryProductSalesRowModel[];
}

/** Saved statistic filter criteria. */
export interface AccessoryReportFilterCriteriaModel {
    showId?: string | null;
    startAt?: number | null;
    endAt?: number | null;
}

/** Admin Filters list/detail row. */
export interface AccessoryReportFilterModel {
    id: string;
    reportKey: string;
    reportName: string;
    username: string;
    status: 'Active' | 'Inactive' | string;
    showId?: string | null;
    filter: AccessoryReportFilterCriteriaModel;
    createdAt: number;
}

/** Create body for a shareable filter preset. */
export interface AccessoryReportFilterCreateInput {
    reportName: string;
    username?: string;
    showId: string;
    startAt?: number | null;
    endAt?: number | null;
}

/** Snapshotted line on an export row. */
export interface AccessoryOrderExportLineModel {
    accessoryId: string;
    sku: string;
    name: string;
    variantKey?: string | null;
    variantLabel?: string | null;
    unitPrice: number;
    quantity: number;
}

/** Catalog product column returned with Excel export payloads. */
export interface AccessoryOrderExportProductModel {
    accessoryId: string;
    sku: string;
    name: string;
}

/** Admin Excel source row (all statuses). */
export interface AccessoryAdminOrderExportModel {
    id: string;
    code: string;
    status: string;
    fulfillment: string;
    paymentId?: string | null;
    showSessionId?: string | null;
    showSessionLabel?: string | null;
    pickupPointId?: string | null;
    pickupPointName?: string | null;
    pickupPointAddress?: string | null;
    customerName: string;
    customerPhone: string;
    customerEmail: string;
    createdAt: number;
    discountCode?: string | null;
    discountAmount: number;
    subtotal: number;
    total: number;
    deliveryAddress?: string | null;
    deliveryWard?: string | null;
    deliveryCity?: string | null;
    deliveryNote?: string | null;
    lines: AccessoryOrderExportLineModel[];
}

/** Admin Excel payload: show catalog + orders. */
export interface AccessoryAdminOrderExportBundle {
    products: AccessoryOrderExportProductModel[];
    orders: AccessoryAdminOrderExportModel[];
}

/** Public partner Excel source row (paid only). */
export interface AccessoryPublicOrderExportModel {
    code: string;
    customerName: string;
    createdAt: number;
    discountCode?: string | null;
    discountAmount: number;
    total: number;
    lines: AccessoryOrderExportLineModel[];
}

/** Public Excel payload: show catalog + paid orders. */
export interface AccessoryPublicOrderExportBundle {
    products: AccessoryOrderExportProductModel[];
    orders: AccessoryPublicOrderExportModel[];
}
