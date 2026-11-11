import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
    BasicResponse,
    CustomFieldTmpl,
    DownloadOrderReportRequest,
    InvoiceStatisticsByShow,
    LoadInvoiceStatisticsRequest,
    OrderPromotionModel,
    OrderResponse,
    OrderSummaryModel,
} from '../../@core/metaticket/model';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi } from '../../../environments/environment';

@Injectable()
export class OrderGateway {
    constructor(private http: HttpClient) {}

    fetchOrderByReservation(
        showId: string,
        sessionId: string,
        reservationIds: string[],
    ): Observable<OrderSummaryModel[]> {
        return this.http
            .post<OrderSummaryModel[]>(
                privateApi('/order/fetch/reservation'),
                {
                    showId: showId,
                    sessionId: sessionId,
                    reservationIds: reservationIds,
                },
                {
                    withCredentials: true,
                    headers: {
                        'Content-Type': 'application/json',
                    },
                },
            )
            .pipe(take(1));
    }

    fetchOfflinePaymentOrder(showId: string, sessionId?: string): Observable<OrderSummaryModel[]> {
        return this.http
            .post<OrderSummaryModel[]>(
                privateApi('/order/offline-payment/fetch/summary'),
                {
                    showId: showId || undefined,
                    sessionId: sessionId || undefined,
                },
                {
                    withCredentials: true,
                    headers: {
                        'Content-Type': 'application/json',
                    },
                },
            )
            .pipe(take(1));
    }

    confirmOfflinePaymentOrder(orderId: string): Observable<BasicResponse> {
        return this.http
            .post<BasicResponse>(
                privateApi('/order/offline-payment/confirm'),
                {
                    orderId: orderId,
                },
                {
                    withCredentials: true,
                    headers: {
                        'Content-Type': 'application/json',
                    },
                },
            )
            .pipe(take(1));
    }

    cancelOfflinePaymentOrder(orderId: string): Observable<BasicResponse> {
        return this.http
            .post<BasicResponse>(
                privateApi('/order/offline-payment/cancel'),
                {
                    orderId: orderId,
                },
                {
                    withCredentials: true,
                    headers: {
                        'Content-Type': 'application/json',
                    },
                },
            )
            .pipe(take(1));
    }

    fetchOrders(showId: string, sessionId?: string): Observable<OrderSummaryModel[]> {
        return this.http
            .post<OrderSummaryModel[]>(
                privateApi('/order/fetch/all'),
                {
                    showId: showId || undefined,
                    sessionId: sessionId || undefined,
                },
                {
                    withCredentials: true,
                    headers: {
                        'Content-Type': 'application/json',
                    },
                },
            )
            .pipe(take(1));
    }

    resendNotification(orderIds: string[]): Observable<Map<string, boolean>> {
        return this.http
            .post<Map<string, boolean>>(privateApi('/order/notify/resend'), orderIds, {
                withCredentials: true,
                headers: {
                    'Content-Type': 'application/json',
                },
            })
            .pipe(take(1));
    }

    getOrderCustomFields(showId: string, sessionId: string): Observable<CustomFieldTmpl[]> {
        return this.http
            .post<CustomFieldTmpl[]>(
                privateApi('/order/custom-field/fetch'),
                {
                    showId: showId,
                    sessionId: sessionId,
                },
                {
                    withCredentials: true,
                    headers: {
                        'Content-Type': 'application/json',
                    },
                },
            )
            .pipe(take(1));
    }

    updateOrderCustomFields$(
        showId: string,
        sessionId: string,
        customFields: unknown[],
    ): Observable<CustomFieldTmpl[]> {
        return this.http
            .post<CustomFieldTmpl[]>(
                privateApi('/order/custom/fields/mapping/update'),
                {
                    showId: showId,
                    sessionId: sessionId,
                    customFields: customFields,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    loadStatistic$(request: LoadInvoiceStatisticsRequest): Observable<InvoiceStatisticsByShow[]> {
        return this.http
            .post<InvoiceStatisticsByShow[]>(privateApi('/order/statistic/load'), request, {
                withCredentials: true,
            })
            .pipe(take(1));
    }

    loadReport$(request: DownloadOrderReportRequest): Observable<OrderResponse[]> {
        return this.http
            .post<OrderResponse[]>(privateApi('/order/report/load'), request, {
                withCredentials: true,
            })
            .pipe(take(1));
    }

    loadPromotions(showId: string, sessionId: string) {
        return this.http
            .get<OrderPromotionModel[]>(privateApi(`order/plugin/promotions`), {
                params: { showId: showId, sessionId: sessionId },
            })
            .pipe(take(1));
    }

    updateOrderPromotion(
        showId: string,
        sessionId: string,
        promotionId: string | null,
        pluginName: string,
        promotionData: string,
    ): Observable<any> {
        return this.http
            .post<any>(
                privateApi(`/order/promotions/update`),
                {
                    showId: showId,
                    sessionId: sessionId,
                    promotionId: promotionId,
                    pluginName: pluginName,
                    data: promotionData,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }

    cancelOrder(orderId: string): Observable<any> {
        return this.http
            .post<any>(
                privateApi('/order/cancel'),
                {
                    orderId: orderId,
                },
                {
                    withCredentials: true,
                },
            )
            .pipe(take(1));
    }
}
