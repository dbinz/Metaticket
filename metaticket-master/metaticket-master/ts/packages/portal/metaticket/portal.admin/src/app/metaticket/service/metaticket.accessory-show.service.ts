import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AccessoryShowService } from '../../@core/metaticket/data.service';
import {
    AccessoryDiscountBatchResult,
    AccessoryDiscountModel,
    AccessoryDiscountUpsertInput,
    AccessoryOrderListModel,
    AccessoryAdminOrderExportBundle,
    AccessoryAdminOrderExportModel,
    AccessoryOrderStatsModel,
    AccessoryOrderSummaryModel,
    AccessoryProductModel,
    AccessoryProductStockAdjustInput,
    AccessoryProductUpsertInput,
    AccessoryReportFilterCreateInput,
    AccessoryReportFilterModel,
    AccessorySalesStatisticModel,
    AccessoryShowModel,
    AccessoryShowSessionModel,
    AccessoryShowStatus,
    AccessoryShowUpsertInput,
    PaymentType,
} from '../../@core/metaticket/model';
import {
    AccessoryShowGateway,
    AdminAccessoryCatalogUpdateBody,
    AdminAccessoryCreateBody,
    AdminAccessoryListItem,
    AdminDiscountBatchCreateResponseDto,
    AdminDiscountListItem,
    AdminDiscountUpsertBody,
    AdminOrderExportItemDto,
    AdminOrderExportResponseDto,
    AdminOrderListItemDto,
    AdminOrderListResponseDto,
    AdminOrderStatsDto,
    AdminReportFilterItemDto,
    AdminSalesStatisticDto,
    AdminAccessoryShowListItem,
    AdminAccessoryShowSessionDto,
    AdminAccessoryShowUpsertBody,
} from '../gateway/accessory-show.gateway';

/**
 * Accessory-show admin service — list/get/create/update/status against private accessory APIs.
 */
@Injectable()
export class MetaticketAccessoryShowService extends AccessoryShowService {
    constructor(private gateway: AccessoryShowGateway) {
        super();
    }

    /** Lists all accessory shows (Open + Closed) for the admin table. */
    getShows$(): Observable<AccessoryShowModel[]> {
        return this.gateway.fetchAllShowsAdmin().pipe(map(list => (list || []).map(s => this.fromAdminListItem(s))));
    }

    /** Loads one show for the edit form. */
    getShow$(id: string): Observable<AccessoryShowModel> {
        return this.gateway.fetchShowAdmin(id).pipe(map(item => this.fromAdminListItem(item)));
    }

    /** Creates a show; returns the persisted admin row. */
    createShow$(input: AccessoryShowUpsertInput): Observable<AccessoryShowModel> {
        return this.gateway.createShowAdmin(this.toUpsertBody(input)).pipe(map(item => this.fromAdminListItem(item)));
    }

    /** Updates a show by raw id. */
    updateShow$(id: string, input: AccessoryShowUpsertInput): Observable<AccessoryShowModel> {
        return this.gateway
            .updateShowAdmin(id, this.toUpsertBody(input))
            .pipe(map(item => this.fromAdminListItem(item)));
    }

    /** Sets Open/Closed for the list toggle. */
    setStatus$(id: string, status: AccessoryShowStatus): Observable<AccessoryShowModel> {
        return this.gateway.setShowStatusAdmin(id, status).pipe(map(item => this.fromAdminListItem(item)));
    }

    /** Lists products for one accessory show (admin). */
    getProducts$(showId: string): Observable<AccessoryProductModel[]> {
        return this.gateway
            .fetchAccessoriesAdmin(showId)
            .pipe(map(list => (list || []).map(p => this.fromAdminProduct(p))));
    }

    /** Loads one product for the edit form. */
    getProduct$(id: string): Observable<AccessoryProductModel> {
        return this.gateway.fetchAccessoryAdmin(id).pipe(map(item => this.fromAdminProduct(item)));
    }

    /** Creates a product under a show. */
    createProduct$(input: AccessoryProductUpsertInput): Observable<AccessoryProductModel> {
        return this.gateway
            .createAccessoryAdmin(this.toProductCreateBody(input))
            .pipe(map(item => this.fromAdminProduct(item)));
    }

    /** Updates catalog fields only (stock via adjustProductStock$). */
    updateProduct$(id: string, input: AccessoryProductUpsertInput): Observable<AccessoryProductModel> {
        return this.gateway
            .updateAccessoryAdmin(id, this.toProductCatalogUpdateBody(input))
            .pipe(map(item => this.fromAdminProduct(item)));
    }

    /** Sets Active/Inactive for the list toggle. */
    setProductActive$(id: string, active: boolean): Observable<AccessoryProductModel> {
        return this.gateway.setAccessoryActiveAdmin(id, active).pipe(map(item => this.fromAdminProduct(item)));
    }

    /** Atomic sellable adjust (delta / setTo) — safe vs checkout reserve. */
    adjustProductStock$(
        id: string,
        body: AccessoryProductStockAdjustInput,
    ): Observable<AccessoryProductModel> {
        return this.gateway
            .adjustAccessoryStockAdmin(id, {
                variantKey: body.variantKey,
                delta: body.delta,
                setTo: body.setTo,
            })
            .pipe(map(item => this.fromAdminProduct(item)));
    }

    /** Lists promotions for one accessory show. */
    getDiscounts$(showId: string): Observable<AccessoryDiscountModel[]> {
        return this.gateway
            .fetchDiscountsAdmin(showId)
            .pipe(map(list => (list || []).map(d => this.fromAdminDiscount(d))));
    }

    /** Loads one promotion for the edit form. */
    getDiscount$(id: string): Observable<AccessoryDiscountModel> {
        return this.gateway.fetchDiscountAdmin(id).pipe(map(item => this.fromAdminDiscount(item)));
    }

    /** Creates a promotion under a show. */
    createDiscount$(input: AccessoryDiscountUpsertInput): Observable<AccessoryDiscountModel> {
        return this.gateway
            .createDiscountAdmin(this.toDiscountUpsertBody(input))
            .pipe(map(item => this.fromAdminDiscount(item)));
    }

    /** Batch create (partial success — created + failures). */
    createDiscountsBatch$(inputs: AccessoryDiscountUpsertInput[]): Observable<AccessoryDiscountBatchResult> {
        return this.gateway
            .createDiscountsBatchAdmin({ items: (inputs || []).map(i => this.toDiscountUpsertBody(i)) })
            .pipe(
                map((res: AdminDiscountBatchCreateResponseDto) => ({
                    created: (res.created || []).map(d => this.fromAdminDiscount(d)),
                    failures: (res.failures || []).map(f => ({ code: f.code, message: f.message })),
                })),
            );
    }

    /** Updates a promotion (code immutable server-side). */
    updateDiscount$(id: string, input: AccessoryDiscountUpsertInput): Observable<AccessoryDiscountModel> {
        return this.gateway
            .updateDiscountAdmin(id, this.toDiscountUpsertBody(input))
            .pipe(map(item => this.fromAdminDiscount(item)));
    }

    /** Sets Active/Inactive for a promotion. */
    setDiscountActive$(id: string, active: boolean): Observable<AccessoryDiscountModel> {
        return this.gateway.setDiscountActiveAdmin(id, active).pipe(map(item => this.fromAdminDiscount(item)));
    }

    /** Hard-deletes a promotion. */
    deleteDiscount$(id: string): Observable<void> {
        return this.gateway.deleteDiscountAdmin(id).pipe(map(() => undefined));
    }

    /** Lists orders + stats for one accessory show. */
    getOrders$(showId: string): Observable<AccessoryOrderListModel> {
        return this.gateway.fetchOrdersAdmin(showId).pipe(map(res => this.fromAdminOrderList(res)));
    }

    /** Loads paid sales statistic for one show (optional date window). */
    getOrderStats$(
        showId: string,
        startAt?: number | null,
        endAt?: number | null,
    ): Observable<AccessorySalesStatisticModel> {
        return this.gateway.fetchOrderStatsAdmin(showId, startAt, endAt).pipe(map(res => this.fromAdminSalesStat(res)));
    }

    /** Loads catalog products + orders for admin Excel export. */
    exportOrders$(
        showId: string,
        startAt?: number | null,
        endAt?: number | null,
    ): Observable<AccessoryAdminOrderExportBundle> {
        return this.gateway
            .exportOrdersAdmin(showId, startAt, endAt)
            .pipe(map(res => this.fromAdminOrderExportBundle(res)));
    }

    /** Lists saved report filter presets. */
    getReportFilters$(showId?: string | null): Observable<AccessoryReportFilterModel[]> {
        return this.gateway
            .fetchReportFiltersAdmin(showId)
            .pipe(map(list => (list || []).map(f => this.fromAdminReportFilter(f))));
    }

    /** Loads Active filter by shareable reportKey. */
    getReportFilterByKey$(reportKey: string): Observable<AccessoryReportFilterModel> {
        return this.gateway
            .fetchReportFilterByKeyAdmin(reportKey)
            .pipe(map(f => this.fromAdminReportFilter(f)));
    }

    /** Creates a filter preset; returns reportKey for sharing. */
    createReportFilter$(input: AccessoryReportFilterCreateInput): Observable<string> {
        return this.gateway.createReportFilterAdmin(input).pipe(map(res => res.reportKey));
    }

    enableReportFilter$(reportId: string): Observable<AccessoryReportFilterModel> {
        return this.gateway
            .enableReportFilterAdmin(reportId)
            .pipe(map(f => this.fromAdminReportFilter(f)));
    }

    disableReportFilter$(reportId: string): Observable<AccessoryReportFilterModel> {
        return this.gateway
            .disableReportFilterAdmin(reportId)
            .pipe(map(f => this.fromAdminReportFilter(f)));
    }

    deleteReportFilter$(reportId: string): Observable<void> {
        return this.gateway.deleteReportFilterAdmin(reportId).pipe(map(() => undefined));
    }

    /** Maps portal upsert model → HTTP body. */
    private toUpsertBody(input: AccessoryShowUpsertInput): AdminAccessoryShowUpsertBody {
        return {
            code: input.code,
            codePrefix: input.codePrefix,
            name: input.name,
            slug: (input.slug || '').trim() || null,
            description: input.description,
            bannerImageUrl: input.bannerImageUrl,
            sessions: (input.sessions || []).map(s => ({
                id: s.id,
                city: {
                    id: s.city?.id || '',
                    name: s.city?.name || '',
                    code: s.city?.code || '',
                },
                venue: s.venue || '',
                datetime: s.datetime || 0,
            })),
            status: input.status,
            saleOpenAt: input.saleOpenAt ?? null,
            pickupNote: input.pickupNote,
            deliveryNote: input.deliveryNote,
            pgAccountCode: input.pgAccountCode,
            allowedPaymentMethods: input.allowedPaymentMethods || [],
            mailPlugin: input.mailPlugin || 'PluginMailTemplateAccessoryOrder',
            mailTemplateId: input.mailTemplateId,
            fulfillment: {
                enablePickupAtShow: !!input.fulfillment?.enablePickupAtShow,
                enableMetaTicketPickup: !!input.fulfillment?.enableMetaTicketPickup,
                enableDelivery: !!input.fulfillment?.enableDelivery,
                pickupAtShowSessionIds: [...(input.fulfillment?.pickupAtShowSessionIds || [])],
                pickupPoints: (input.fulfillment?.pickupPoints || []).map(p => ({
                    id: p.id,
                    name: p.name,
                    address: p.address,
                    note: p.note,
                })),
            },
        };
    }

    /** Maps admin list/detail DTO → portal form model. */
    private fromAdminListItem(item: AdminAccessoryShowListItem): AccessoryShowModel {
        const sessions = this.mapSessions(item.sessions || []);
        const fulfillment = item.fulfillment || {
            enablePickupAtShow: true,
            enableMetaTicketPickup: false,
            enableDelivery: false,
            pickupAtShowSessionIds: sessions.map(s => s.id),
            pickupPoints: [],
        };
        return {
            id: item.id,
            code: item.code,
            codePrefix: item.codePrefix || 'AC',
            name: item.name,
            slug: item.slug || null,
            description: item.description || undefined,
            bannerImageUrl: item.bannerImageUrl || undefined,
            sessions,
            status: (item.status as AccessoryShowStatus) || 'Open',
            saleOpenAt: item.saleOpenAt || null,
            pickupNote: item.pickupNote || undefined,
            deliveryNote: item.deliveryNote || undefined,
            pgAccountCode: item.pgAccountCode || undefined,
            allowedPaymentMethods: (item.allowedPaymentMethods || []) as PaymentType[],
            mailPlugin: item.mailPlugin || 'PluginMailTemplateAccessoryOrder',
            mailTemplateId: item.mailTemplateId || undefined,
            fulfillment: {
                enablePickupAtShow: !!fulfillment.enablePickupAtShow,
                enableMetaTicketPickup: !!fulfillment.enableMetaTicketPickup,
                enableDelivery: !!fulfillment.enableDelivery,
                pickupAtShowSessionIds: [...(fulfillment.pickupAtShowSessionIds || [])],
                pickupPoints: (fulfillment.pickupPoints || []).map(p => ({
                    id: p.id,
                    name: p.name,
                    address: p.address,
                    note: p.note || undefined,
                })),
            },
            pinOrder: item.pinOrder ?? null,
            productCount: item.productCount ?? null,
            createdAt: item.createdAt || Date.now(),
            lastModified: item.lastModified || Date.now(),
        };
    }

    /** Maps admin sessions (city object + datetime) → portal session model. */
    private mapSessions(sessions: AdminAccessoryShowSessionDto[]): AccessoryShowSessionModel[] {
        return sessions.map(s => ({
            id: s.id,
            city: {
                id: s.city?.id || '',
                name: s.city?.name || '',
                code: s.city?.code || '',
            },
            venue: s.venue || '',
            datetime: s.datetime || 0,
        }));
    }

    /** Maps admin product DTO → portal product model. */
    private fromAdminProduct(item: AdminAccessoryListItem): AccessoryProductModel {
        return {
            id: item.id,
            accessoryShowId: item.accessoryShowId,
            sku: item.sku,
            name: item.name,
            description: item.description || undefined,
            imageUrl: item.imageUrl || undefined,
            imageUrls: [...(item.imageUrls || [])],
            price: item.price,
            currency: item.currency || 'VND',
            stock: item.stock,
            hasVariants: !!item.hasVariants,
            variantCount: item.variantCount ?? (item.variants || []).length,
            active: !!item.active,
            attributes: (item.attributes || []).map(a => ({ key: a.key, name: a.name })),
            attr1Name: item.attr1Name ?? null,
            attr2Name: item.attr2Name ?? null,
            minOrderQty: item.minOrderQty ?? null,
            maxOrderQty: item.maxOrderQty ?? null,
            maxPerUserQty: item.maxPerUserQty ?? null,
            variants: (item.variants || []).map(v => ({
                key: v.key,
                label: v.label,
                stock: v.stock,
                attr1: v.attr1 || undefined,
                attr2: v.attr2 || undefined,
                imageUrl: v.imageUrl || undefined,
                options: v.options || undefined,
                active: !!v.active,
            })),
            createdAt: item.createdAt || Date.now(),
            lastModified: item.lastModified || Date.now(),
        };
    }

    /** Maps portal product create → HTTP create body (finite initial stock). */
    private toProductCreateBody(input: AccessoryProductUpsertInput): AdminAccessoryCreateBody {
        return {
            accessoryShowId: input.accessoryShowId,
            sku: input.sku,
            name: input.name,
            description: input.description,
            imageUrl: input.imageUrl,
            imageUrls: [...(input.imageUrls || [])],
            price: input.price,
            currency: input.currency || 'VND',
            stock: Math.max(0, Number(input.stock) || 0),
            variants: (input.variants || []).map(v => ({
                key: v.key,
                label: v.label,
                stock: Math.max(0, Number(v.stock) || 0),
                attr1: v.attr1,
                attr2: v.attr2,
                imageUrl: v.imageUrl,
                active: v.active !== false,
            })),
            attr1Name: input.attr1Name || undefined,
            attr2Name: input.attr2Name || undefined,
            minOrderQty: input.minOrderQty ?? undefined,
            maxOrderQty: input.maxOrderQty ?? undefined,
            maxPerUserQty: input.maxPerUserQty ?? undefined,
            active: !!input.active,
        };
    }

    /** Maps portal product edit → catalog update (new variants may carry initial stock). */
    private toProductCatalogUpdateBody(input: AccessoryProductUpsertInput): AdminAccessoryCatalogUpdateBody {
        return {
            sku: input.sku,
            name: input.name,
            description: input.description,
            imageUrl: input.imageUrl,
            imageUrls: [...(input.imageUrls || [])],
            price: input.price,
            currency: input.currency || 'VND',
            variants: (input.variants || []).map(v => ({
                key: v.key,
                label: v.label,
                active: v.active !== false,
                attr1: v.attr1,
                attr2: v.attr2,
                imageUrl: v.imageUrl,
                stock: Math.max(0, Number(v.stock) || 0),
            })),
            attr1Name: input.attr1Name || undefined,
            attr2Name: input.attr2Name || undefined,
            minOrderQty: input.minOrderQty ?? undefined,
            maxOrderQty: input.maxOrderQty ?? undefined,
            maxPerUserQty: input.maxPerUserQty ?? undefined,
            active: !!input.active,
        };
    }

    /** Maps admin discount DTO → portal model. */
    private fromAdminDiscount(item: AdminDiscountListItem): AccessoryDiscountModel {
        return {
            id: item.id,
            code: item.code,
            accessoryShowId: item.accessoryShowId || '',
            type: item.type as AccessoryDiscountModel['type'],
            value: item.value,
            rewardType: (item.rewardType as AccessoryDiscountModel['rewardType']) || null,
            maxDiscountAmount: item.maxDiscountAmount ?? null,
            minOrderAmount: item.minOrderAmount || 0,
            tiers: (item.tiers || []).map(t => ({
                minOrderAmount: t.minOrderAmount,
                type: t.type as 'Percentage' | 'FixedAmount',
                value: t.value,
                maxDiscountAmount: t.maxDiscountAmount ?? null,
            })),
            applyMode: (item.applyMode as AccessoryDiscountModel['applyMode']) || 'Manual',
            usageLimit: item.usageLimit ?? null,
            usedCount: item.usedCount || 0,
            startAt: item.startAt ?? null,
            endAt: item.endAt ?? null,
            active: !!item.active,
        };
    }

    /** Maps portal discount upsert → HTTP body. */
    private toDiscountUpsertBody(input: AccessoryDiscountUpsertInput): AdminDiscountUpsertBody {
        return {
            code: input.code,
            accessoryShowId: input.accessoryShowId,
            type: input.type,
            value: Number(input.value) || 0,
            rewardType: input.rewardType || null,
            maxDiscountAmount: input.maxDiscountAmount ?? null,
            minOrderAmount: Number(input.minOrderAmount) || 0,
            tiers: (input.tiers || []).map(t => ({
                minOrderAmount: Number(t.minOrderAmount) || 0,
                type: t.type,
                value: Number(t.value) || 0,
                maxDiscountAmount: t.maxDiscountAmount ?? null,
            })),
            applyMode: input.applyMode,
            usageLimit: input.usageLimit ?? null,
            startAt: input.startAt ?? null,
            endAt: input.endAt ?? null,
            active: !!input.active,
        };
    }

    /** Maps admin order list response → portal model. */
    private fromAdminOrderList(res: AdminOrderListResponseDto): AccessoryOrderListModel {
        return {
            stats: this.fromAdminOrderStats(res.stats),
            orders: (res.orders || []).map(o => this.fromAdminOrderItem(o)),
        };
    }

    /** Maps admin order stats DTO. */
    private fromAdminOrderStats(stats: AdminOrderStatsDto): AccessoryOrderStatsModel {
        return {
            totalOrders: stats?.totalOrders || 0,
            paidOrders: stats?.paidOrders || 0,
            revenue: stats?.revenue || 0,
            byStatus: stats?.byStatus || {},
        };
    }

    /** Maps one admin order list row (keeps lines for the detail modal). */
    private fromAdminOrderItem(item: AdminOrderListItemDto): AccessoryOrderSummaryModel {
        return {
            id: item.id,
            code: item.code,
            status: item.status,
            fulfillment: item.fulfillment,
            paymentId: item.paymentId ?? null,
            customerUserId: item.customerUserId ?? null,
            showSessionId: item.showSessionId ?? null,
            showSessionLabel: item.showSessionLabel ?? null,
            pickupPointId: item.pickupPointId ?? null,
            pickupPointName: item.pickupPointName ?? null,
            pickupPointAddress: item.pickupPointAddress ?? null,
            subtotal: item.subtotal,
            discountAmount: item.discountAmount || 0,
            discountCode: item.discountCode ?? null,
            total: item.total,
            customerName: item.customerName,
            customerPhone: item.customerPhone,
            customerEmail: item.customerEmail,
            createdAt: item.createdAt,
            deliveryAddress: item.deliveryAddress ?? null,
            deliveryWard: item.deliveryWard ?? null,
            deliveryCity: item.deliveryCity ?? null,
            deliveryNote: item.deliveryNote ?? null,
            lines: (item.lines || []).map(l => ({
                accessoryId: l.accessoryId,
                sku: l.sku,
                name: l.name,
                variantKey: l.variantKey ?? null,
                variantLabel: l.variantLabel ?? null,
                unitPrice: l.unitPrice,
                quantity: l.quantity,
            })),
        };
    }

    /** Maps admin Excel export bundle. */
    private fromAdminOrderExportBundle(res: AdminOrderExportResponseDto): AccessoryAdminOrderExportBundle {
        return {
            products: (res.products || []).map(p => ({
                accessoryId: p.accessoryId,
                sku: p.sku,
                name: p.name,
            })),
            orders: (res.orders || []).map(o => this.fromAdminOrderExport(o)),
        };
    }

    /** Maps admin Excel export row. */
    private fromAdminOrderExport(item: AdminOrderExportItemDto): AccessoryAdminOrderExportModel {
        return {
            id: item.id,
            code: item.code,
            status: item.status,
            fulfillment: item.fulfillment,
            paymentId: item.paymentId ?? null,
            showSessionId: item.showSessionId ?? null,
            showSessionLabel: item.showSessionLabel ?? null,
            pickupPointId: item.pickupPointId ?? null,
            pickupPointName: item.pickupPointName ?? null,
            pickupPointAddress: item.pickupPointAddress ?? null,
            customerName: item.customerName,
            customerPhone: item.customerPhone,
            customerEmail: item.customerEmail,
            createdAt: item.createdAt,
            discountCode: item.discountCode ?? null,
            discountAmount: item.discountAmount || 0,
            subtotal: item.subtotal,
            total: item.total,
            deliveryAddress: item.deliveryAddress ?? null,
            deliveryWard: item.deliveryWard ?? null,
            deliveryCity: item.deliveryCity ?? null,
            deliveryNote: item.deliveryNote ?? null,
            lines: (item.lines || []).map(l => ({
                accessoryId: l.accessoryId,
                sku: l.sku,
                name: l.name,
                variantKey: l.variantKey ?? null,
                variantLabel: l.variantLabel ?? null,
                unitPrice: l.unitPrice,
                quantity: l.quantity,
            })),
        };
    }

    /** Maps sales statistic DTO. */
    private fromAdminSalesStat(res: AdminSalesStatisticDto): AccessorySalesStatisticModel {
        const s = res.summary || ({} as AdminSalesStatisticDto['summary']);
        return {
            summary: {
                totalOrders: s.totalOrders || 0,
                paidOrders: s.paidOrders || 0,
                unitsSold: s.unitsSold || 0,
                grossSales: s.grossSales || 0,
                discountTotal: s.discountTotal || 0,
                revenue: s.revenue || 0,
                byStatus: s.byStatus || {},
            },
            byProduct: (res.byProduct || []).map(p => ({
                accessoryId: p.accessoryId,
                sku: p.sku,
                name: p.name,
                unitPrice: p.unitPrice,
                quantity: p.quantity,
                grossSales: p.grossSales,
            })),
        };
    }

    /** Maps report filter DTO. */
    private fromAdminReportFilter(item: AdminReportFilterItemDto): AccessoryReportFilterModel {
        return {
            id: item.id,
            reportKey: item.reportKey,
            reportName: item.reportName,
            username: item.username || '',
            status: item.status,
            showId: item.showId ?? null,
            filter: {
                showId: item.filter?.showId ?? null,
                startAt: item.filter?.startAt ?? null,
                endAt: item.filter?.endAt ?? null,
            },
            createdAt: item.createdAt,
        };
    }
}
