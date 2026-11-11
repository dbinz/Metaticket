import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi, publicApi } from '../../../environments/environment';
import {
    AccessoryPinBoard,
    AccessoryPinCandidatePage,
} from '../../@core/metaticket/model';

/** Public storefront summary — ids are checksum-encoded (homepage). */
export interface PublicAccessoryShowSummary {
    id: string;
    code: string;
    name: string;
    description?: string | null;
    bannerImageUrl?: string | null;
    sessions: Array<{
        id: string;
        city: string;
        venue: string;
        date: number;
    }>;
    pickupNote?: string | null;
    /** Optional show-level tip for home delivery. */
    deliveryNote?: string | null;
}

/** Public storefront detail. */
export interface PublicAccessoryShowDetail extends PublicAccessoryShowSummary {
    allowedPaymentMethods?: string[];
}

/** City object on admin session (`CityModel` shape). */
export interface AdminAccessoryCityDto {
    id: string;
    name: string;
    code: string;
}

/** Session on admin list/detail (`datetime` = epoch millis). */
export interface AdminAccessoryShowSessionDto {
    id: string;
    city: AdminAccessoryCityDto;
    venue: string;
    datetime: number;
}

export interface AdminPickupPointDto {
    id: string;
    name: string;
    address: string;
    note?: string | null;
}

export interface AdminFulfillmentSettingsDto {
    enablePickupAtShow: boolean;
    enableMetaTicketPickup: boolean;
    enableDelivery: boolean;
    pickupAtShowSessionIds: string[];
    pickupPoints: AdminPickupPointDto[];
}

/** Row from admin fetch/create/update/status accessory-show APIs. */
export interface AdminAccessoryShowListItem {
    id: string;
    code: string;
    codePrefix: string;
    name: string;
    slug?: string | null;
    description?: string | null;
    bannerImageUrl?: string | null;
    status: string;
    saleOpenAt?: number | null;
    sessions: AdminAccessoryShowSessionDto[];
    pickupNote?: string | null;
    deliveryNote?: string | null;
    pgAccountCode?: string | null;
    allowedPaymentMethods: string[];
    mailPlugin: string;
    mailTemplateId?: string | null;
    fulfillment: AdminFulfillmentSettingsDto;
    pinOrder?: number | null;
    productCount: number;
    createdAt: number;
    lastModified: number;
}

/** Variant on admin product list. */
export interface AdminAccessoryVariantDto {
    key: string;
    label: string;
    stock: number;
    attr1?: string | null;
    attr2?: string | null;
    imageUrl?: string | null;
    options?: Record<string, string>;
    active: boolean;
}

/** Row from `GET /private/accessory/fetch/all?showId=`. */
export interface AdminAccessoryListItem {
    id: string;
    accessoryShowId: string;
    sku: string;
    name: string;
    description?: string | null;
    imageUrl?: string | null;
    imageUrls: string[];
    price: number;
    currency: string;
    stock: number;
    hasVariants: boolean;
    variantCount: number;
    active: boolean;
    attributes?: Array<{ key: string; name: string }>;
    attr1Name?: string | null;
    attr2Name?: string | null;
    minOrderQty?: number | null;
    maxOrderQty?: number | null;
    maxPerUserQty?: number | null;
    variants: AdminAccessoryVariantDto[];
    createdAt: number;
    lastModified: number;
}

/** Create body for admin accessory product (initial stock finite >= 0). */
export interface AdminAccessoryCreateBody {
    accessoryShowId: string;
    sku: string;
    name: string;
    description?: string;
    imageUrl?: string;
    imageUrls: string[];
    price: number;
    currency: string;
    stock: number;
    variants: AdminAccessoryVariantDto[];
    attr1Name?: string;
    attr2Name?: string;
    minOrderQty?: number;
    maxOrderQty?: number;
    maxPerUserQty?: number;
    active: boolean;
}

/** Catalog-only update — existing stock cells untouched; new variants may include initial stock. */
export interface AdminAccessoryCatalogUpdateBody {
    sku: string;
    name: string;
    description?: string;
    imageUrl?: string;
    imageUrls: string[];
    price: number;
    currency: string;
    variants: Array<{
        key: string;
        label: string;
        active: boolean;
        attr1?: string;
        attr2?: string;
        imageUrl?: string;
        /** Initial sellable for newly added keys only. */
        stock?: number;
    }>;
    attr1Name?: string;
    attr2Name?: string;
    minOrderQty?: number;
    maxOrderQty?: number;
    maxPerUserQty?: number;
    active: boolean;
}

/** @deprecated Use AdminAccessoryCreateBody / AdminAccessoryCatalogUpdateBody */
export type AdminAccessoryUpsertBody = AdminAccessoryCreateBody;

/** Body for `POST /private/accessory/stock/adjust`. */
export interface AdminAccessoryStockAdjustBody {
    variantKey?: string;
    delta?: number;
    setTo?: number;
}

/** Create/update body aligned with portal `AccessoryShowUpsertInput`. */
export interface AdminAccessoryShowUpsertBody {
    code?: string;
    codePrefix: string;
    name: string;
    slug?: string | null;
    description?: string;
    bannerImageUrl?: string;
    sessions: AdminAccessoryShowSessionDto[];
    status: string;
    saleOpenAt?: number | null;
    pickupNote?: string;
    deliveryNote?: string;
    pgAccountCode?: string;
    allowedPaymentMethods: string[];
    mailPlugin: string;
    mailTemplateId?: string;
    fulfillment: AdminFulfillmentSettingsDto;
}

/** Body for admin discount create/update. */
export interface AdminDiscountUpsertBody {
    code?: string;
    accessoryShowId: string;
    type: string;
    value: number;
    rewardType?: string | null;
    maxDiscountAmount?: number | null;
    minOrderAmount: number;
    tiers: AdminDiscountTierDto[];
    applyMode?: string;
    usageLimit?: number | null;
    startAt?: number | null;
    endAt?: number | null;
    active: boolean;
}

/** Tier on admin discount APIs. */
export interface AdminDiscountTierDto {
    minOrderAmount: number;
    type: string;
    value: number;
    maxDiscountAmount?: number | null;
}

/** Row from admin discount list/detail APIs. */
export interface AdminDiscountListItem {
    id: string;
    code: string;
    accessoryShowId?: string | null;
    type: string;
    value: number;
    rewardType?: string | null;
    maxDiscountAmount?: number | null;
    minOrderAmount: number;
    tiers: AdminDiscountTierDto[];
    applyMode: string;
    usageLimit?: number | null;
    usedCount: number;
    startAt?: number | null;
    endAt?: number | null;
    active: boolean;
}

/** One failed row from batch create. */
export interface AdminDiscountBatchFailureDto {
    code?: string | null;
    message: string;
}

/** Response from `POST accessory/discount/create/batch`. */
export interface AdminDiscountBatchCreateResponseDto {
    created: AdminDiscountListItem[];
    failures: AdminDiscountBatchFailureDto[];
}

/** Stats block from admin order list API. */
export interface AdminOrderStatsDto {
    totalOrders: number;
    paidOrders: number;
    revenue: number;
    byStatus: Record<string, number>;
}

/** Order row from admin order list API (detail fields for modal). */
export interface AdminOrderListItemDto {
    id: string;
    code: string;
    status: string;
    fulfillment: string;
    paymentId?: string | null;
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
    lines: AdminOrderExportLineDto[];
}

/** GET private/accessory/order/fetch/all response. */
export interface AdminOrderListResponseDto {
    stats: AdminOrderStatsDto;
    orders: AdminOrderListItemDto[];
}

export interface AdminSalesSummaryDto {
    totalOrders: number;
    paidOrders: number;
    unitsSold: number;
    grossSales: number;
    discountTotal: number;
    revenue: number;
    byStatus: Record<string, number>;
}

export interface AdminProductSalesRowDto {
    accessoryId: string;
    sku: string;
    name: string;
    unitPrice: number;
    quantity: number;
    grossSales: number;
}

export interface AdminSalesStatisticDto {
    summary: AdminSalesSummaryDto;
    byProduct: AdminProductSalesRowDto[];
}

export interface AdminOrderExportLineDto {
    accessoryId: string;
    sku: string;
    name: string;
    variantKey?: string | null;
    variantLabel?: string | null;
    unitPrice: number;
    quantity: number;
}

/** GET private/accessory/order/export row. */
export interface AdminOrderExportItemDto {
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
    lines: AdminOrderExportLineDto[];
}

export interface AdminOrderExportProductDto {
    accessoryId: string;
    sku: string;
    name: string;
}

/** GET private/accessory/order/export response. */
export interface AdminOrderExportResponseDto {
    products: AdminOrderExportProductDto[];
    orders: AdminOrderExportItemDto[];
}

/** GET public/accessory/report/{key}/orders row. */
export interface PublicOrderExportItemDto {
    code: string;
    customerName: string;
    createdAt: number;
    discountCode?: string | null;
    discountAmount: number;
    total: number;
    lines: AdminOrderExportLineDto[];
}

/** GET public/accessory/report/{key}/orders response. */
export interface PublicOrderExportResponseDto {
    products: AdminOrderExportProductDto[];
    orders: PublicOrderExportItemDto[];
}

export interface AdminReportFilterCriteriaDto {
    showId?: string | null;
    startAt?: number | null;
    endAt?: number | null;
}

export interface AdminReportFilterItemDto {
    id: string;
    reportKey: string;
    reportName: string;
    username: string;
    status: string;
    showId?: string | null;
    filter: AdminReportFilterCriteriaDto;
    createdAt: number;
}

/**
 * HTTP gateway for accessory catalog admin + public reads against service.accessory.
 */
@Injectable()
export class AccessoryShowGateway {
    constructor(private http: HttpClient) {}

    /** Admin list — Open + Closed, raw ObjectIds, rich admin fields. */
    fetchAllShowsAdmin(): Observable<AdminAccessoryShowListItem[]> {
        return this.http
            .get<AdminAccessoryShowListItem[]>(privateApi('accessory/show/fetch/all'))
            .pipe(take(1));
    }

    /** Admin get-by-id for the edit form. */
    fetchShowAdmin(id: string): Observable<AdminAccessoryShowListItem> {
        return this.http
            .get<AdminAccessoryShowListItem>(privateApi(`accessory/show/fetch?id=${encodeURIComponent(id)}`))
            .pipe(take(1));
    }

    /** Admin create. */
    createShowAdmin(body: AdminAccessoryShowUpsertBody): Observable<AdminAccessoryShowListItem> {
        return this.http.post<AdminAccessoryShowListItem>(privateApi('accessory/show/create'), body).pipe(take(1));
    }

    /** Admin update (code immutable server-side). */
    updateShowAdmin(id: string, body: AdminAccessoryShowUpsertBody): Observable<AdminAccessoryShowListItem> {
        return this.http
            .post<AdminAccessoryShowListItem>(privateApi(`accessory/show/update?id=${encodeURIComponent(id)}`), body)
            .pipe(take(1));
    }

    /** GET occupied accessory pin slots (flexible length, max 5). */
    fetchPinBoard(): Observable<AccessoryPinBoard> {
        return this.http.get<AccessoryPinBoard>(privateApi('accessory/show/pin/board')).pipe(take(1));
    }

    /** POST pin the newest-edited accessory shows, up to 5. */
    refreshPinBoard(): Observable<AccessoryPinBoard> {
        return this.http.post<AccessoryPinBoard>(privateApi('accessory/show/pin/refresh'), {}).pipe(take(1));
    }

    /** POST persist FE pin order; empty showId rows are dropped. */
    confirmPinBoard(slots: { pinOrder: number; showId: string | null }[]): Observable<AccessoryPinBoard> {
        return this.http
            .post<AccessoryPinBoard>(privateApi('accessory/show/pin/confirm'), { slots })
            .pipe(take(1));
    }

    /** GET paginated picker (page is 1-based). */
    searchPinCandidates(page: number, size: number, keyword?: string): Observable<AccessoryPinCandidatePage> {
        let params = new HttpParams().set('page', String(page)).set('size', String(size));
        if (keyword) params = params.set('keyword', keyword);
        return this.http
            .get<AccessoryPinCandidatePage>(privateApi('accessory/show/pin/candidates'), { params })
            .pipe(take(1));
    }

    /** Admin Open/Closed toggle. */
    setShowStatusAdmin(id: string, status: string): Observable<AdminAccessoryShowListItem> {
        return this.http
            .post<AdminAccessoryShowListItem>(privateApi(`accessory/show/status?id=${encodeURIComponent(id)}`), {
                status,
            })
            .pipe(take(1));
    }

    /** Admin products for one show (active + inactive). */
    fetchAccessoriesAdmin(showId: string): Observable<AdminAccessoryListItem[]> {
        return this.http
            .get<AdminAccessoryListItem[]>(
                privateApi(`accessory/fetch/all?showId=${encodeURIComponent(showId)}`),
            )
            .pipe(take(1));
    }

    /** Admin get-by-id for the product edit form. */
    fetchAccessoryAdmin(id: string): Observable<AdminAccessoryListItem> {
        return this.http
            .get<AdminAccessoryListItem>(privateApi(`accessory/fetch?id=${encodeURIComponent(id)}`))
            .pipe(take(1));
    }

    /** Admin create product (initial sellable stock). */
    createAccessoryAdmin(body: AdminAccessoryCreateBody): Observable<AdminAccessoryListItem> {
        return this.http.post<AdminAccessoryListItem>(privateApi('accessory/create'), body).pipe(take(1));
    }

    /** Admin catalog update (no stock write). */
    updateAccessoryAdmin(id: string, body: AdminAccessoryCatalogUpdateBody): Observable<AdminAccessoryListItem> {
        return this.http
            .post<AdminAccessoryListItem>(privateApi(`accessory/update?id=${encodeURIComponent(id)}`), body)
            .pipe(take(1));
    }

    /** Admin Active/Inactive toggle. */
    setAccessoryActiveAdmin(id: string, active: boolean): Observable<AdminAccessoryListItem> {
        return this.http
            .post<AdminAccessoryListItem>(privateApi(`accessory/active?id=${encodeURIComponent(id)}`), {
                active,
            })
            .pipe(take(1));
    }

    /** Admin atomic stock adjust (delta / setTo) — does not replace catalog fields. */
    adjustAccessoryStockAdmin(id: string, body: AdminAccessoryStockAdjustBody): Observable<AdminAccessoryListItem> {
        return this.http
            .post<AdminAccessoryListItem>(
                privateApi(`accessory/stock/adjust?id=${encodeURIComponent(id)}`),
                body,
            )
            .pipe(take(1));
    }

    /** Admin discounts for one show. */
    fetchDiscountsAdmin(showId: string): Observable<AdminDiscountListItem[]> {
        return this.http
            .get<AdminDiscountListItem[]>(
                privateApi(`accessory/discount/fetch/all?showId=${encodeURIComponent(showId)}`),
            )
            .pipe(take(1));
    }

    /** Admin get discount by id. */
    fetchDiscountAdmin(id: string): Observable<AdminDiscountListItem> {
        return this.http
            .get<AdminDiscountListItem>(privateApi(`accessory/discount/fetch?id=${encodeURIComponent(id)}`))
            .pipe(take(1));
    }

    /** Admin create discount. */
    createDiscountAdmin(body: AdminDiscountUpsertBody): Observable<AdminDiscountListItem> {
        return this.http.post<AdminDiscountListItem>(privateApi('accessory/discount/create'), body).pipe(take(1));
    }

    /** Admin batch create discounts (partial success). */
    createDiscountsBatchAdmin(body: { items: AdminDiscountUpsertBody[] }): Observable<AdminDiscountBatchCreateResponseDto> {
        return this.http
            .post<AdminDiscountBatchCreateResponseDto>(privateApi('accessory/discount/create/batch'), body)
            .pipe(take(1));
    }

    /** Admin update discount. */
    updateDiscountAdmin(id: string, body: AdminDiscountUpsertBody): Observable<AdminDiscountListItem> {
        return this.http
            .post<AdminDiscountListItem>(privateApi(`accessory/discount/update?id=${encodeURIComponent(id)}`), body)
            .pipe(take(1));
    }

    /** Admin Active/Inactive toggle for discount. */
    setDiscountActiveAdmin(id: string, active: boolean): Observable<AdminDiscountListItem> {
        return this.http
            .post<AdminDiscountListItem>(privateApi(`accessory/discount/active?id=${encodeURIComponent(id)}`), {
                active,
            })
            .pipe(take(1));
    }

    /** Admin hard-delete discount. */
    deleteDiscountAdmin(id: string): Observable<{ ok: boolean }> {
        return this.http
            .post<{ ok: boolean }>(privateApi(`accessory/discount/delete?id=${encodeURIComponent(id)}`), {})
            .pipe(take(1));
    }

    /** Admin orders + stats for one show. */
    fetchOrdersAdmin(showId: string): Observable<AdminOrderListResponseDto> {
        return this.http
            .get<AdminOrderListResponseDto>(
                privateApi(`accessory/order/fetch/all?showId=${encodeURIComponent(showId)}`),
            )
            .pipe(take(1));
    }

    /** Admin sales statistic (optional date range, endAt exclusive). */
    fetchOrderStatsAdmin(
        showId: string,
        startAt?: number | null,
        endAt?: number | null,
    ): Observable<AdminSalesStatisticDto> {
        const params = new URLSearchParams();
        params.set('showId', showId);
        if (startAt != null) params.set('startAt', String(startAt));
        if (endAt != null) params.set('endAt', String(endAt));
        return this.http
            .get<AdminSalesStatisticDto>(privateApi(`accessory/order/stats?${params.toString()}`))
            .pipe(take(1));
    }

    /** Admin Excel source: catalog products + orders in optional date window. */
    exportOrdersAdmin(
        showId: string,
        startAt?: number | null,
        endAt?: number | null,
    ): Observable<AdminOrderExportResponseDto> {
        const params = new URLSearchParams();
        params.set('showId', showId);
        if (startAt != null) params.set('startAt', String(startAt));
        if (endAt != null) params.set('endAt', String(endAt));
        return this.http
            .get<AdminOrderExportResponseDto>(privateApi(`accessory/order/export?${params.toString()}`))
            .pipe(take(1));
    }

    /** Public partner Excel source (catalog + paid orders for Active reportKey). */
    fetchPublicReportOrders(reportKey: string): Observable<PublicOrderExportResponseDto> {
        return this.http
            .get<PublicOrderExportResponseDto>(
                publicApi(`accessory/report/${encodeURIComponent(reportKey)}/orders`),
            )
            .pipe(take(1));
    }

    /** Admin list saved report filters. */
    fetchReportFiltersAdmin(showId?: string | null): Observable<AdminReportFilterItemDto[]> {
        const q = showId ? `?showId=${encodeURIComponent(showId)}` : '';
        return this.http
            .get<AdminReportFilterItemDto[]>(privateApi(`accessory/report-filter/fetch/all${q}`))
            .pipe(take(1));
    }

    /** Admin load Active filter by shareable reportKey. */
    fetchReportFilterByKeyAdmin(reportKey: string): Observable<AdminReportFilterItemDto> {
        return this.http
            .get<AdminReportFilterItemDto>(
                privateApi(`accessory/report-filter/fetch?reportKey=${encodeURIComponent(reportKey)}`),
            )
            .pipe(take(1));
    }

    /** Admin create report filter; returns reportKey. */
    createReportFilterAdmin(body: {
        reportName: string;
        username?: string;
        showId: string;
        startAt?: number | null;
        endAt?: number | null;
    }): Observable<{ reportKey: string }> {
        return this.http
            .post<{ reportKey: string }>(privateApi('accessory/report-filter/create'), body)
            .pipe(take(1));
    }

    enableReportFilterAdmin(reportId: string): Observable<AdminReportFilterItemDto> {
        return this.http
            .post<AdminReportFilterItemDto>(privateApi('accessory/report-filter/enable'), { reportId })
            .pipe(take(1));
    }

    disableReportFilterAdmin(reportId: string): Observable<AdminReportFilterItemDto> {
        return this.http
            .post<AdminReportFilterItemDto>(privateApi('accessory/report-filter/disable'), { reportId })
            .pipe(take(1));
    }

    deleteReportFilterAdmin(reportId: string): Observable<{ ok: boolean }> {
        return this.http
            .post<{ ok: boolean }>(privateApi('accessory/report-filter/delete'), { reportId })
            .pipe(take(1));
    }

    /** GET /api/public/accessory/show/fetch/all — paginated storefront listing. */
    fetchAllShows(page = 0, size = 6): Observable<{ content: PublicAccessoryShowSummary[] }> {
        let params = new HttpParams().set('page', String(page)).set('size', String(size));
        return this.http
            .get<{ content: PublicAccessoryShowSummary[] }>(publicApi('accessory/show/fetch/all'), { params })
            .pipe(take(1));
    }

    /** GET /api/public/accessory/show/fetch?id= — one Open show. */
    fetchShow(id: string): Observable<PublicAccessoryShowDetail> {
        return this.http
            .get<PublicAccessoryShowDetail>(publicApi(`accessory/show/fetch?id=${encodeURIComponent(id)}`))
            .pipe(take(1));
    }
}
