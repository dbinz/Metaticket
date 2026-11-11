import { Injectable } from '@angular/core';
import { OrderService } from '../../@core/metaticket/data.service';
import { Observable } from 'rxjs';
import {
    CustomFieldTmpl,
    DownloadOrderReportRequest,
    InvoiceStatisticsByShow,
    LoadInvoiceStatisticsRequest,
    OrderResponse,
} from '../';
import { OrderGateway } from '../gateway/order.gateway';

@Injectable()
export class MetaticketOrderService extends OrderService {
    constructor(private orderGateway: OrderGateway) {
        super();
    }

    override getOrderCustomFields(showId: string, sessionId: string): Observable<CustomFieldTmpl[]> {
        return this.orderGateway.getOrderCustomFields(showId, sessionId);
    }

    override updateOrderCustomFields(
        showId: string,
        sessionId: string,
        customFields: unknown[],
    ): Observable<CustomFieldTmpl[]> {
        return this.orderGateway.updateOrderCustomFields$(showId, sessionId, customFields);
    }

    override loadStatistic(request: LoadInvoiceStatisticsRequest): Observable<InvoiceStatisticsByShow[]> {
        return this.orderGateway.loadStatistic$(request);
    }

    override loadReport(request: DownloadOrderReportRequest): Observable<OrderResponse[]> {
        return this.orderGateway.loadReport$(request);
    }

    override getPromotions(showId: string, sessionId: string): Observable<any> {
        return this.orderGateway.loadPromotions(showId, sessionId);
    }

    override updatePromotion(
        showId: string,
        sessionId: string,
        promotionId: string | null,
        pluginName: string,
        promotionData: string,
    ): Observable<any> {
        return this.orderGateway.updateOrderPromotion(showId, sessionId, promotionId, pluginName, promotionData);
    }
}
