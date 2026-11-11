import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { LocalDataSource, Settings, IColumnType, Row } from 'angular2-smart-table';
import { tap } from 'rxjs/operators';
import { AccessoryShowService } from '../../@core/metaticket/data.service';
import {
    AccessoryOrderStatsModel,
    AccessoryOrderSummaryModel,
    AccessoryShowModel,
} from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { AccessoryOrderDetailDialogComponent } from './accessory-order-detail-dialog.component';

/** Row shape for the orders smart-table. */
interface OrderTableRow {
    id: string;
    code: string;
    statusLabel: string;
    totalLabel: string;
    timeLabel: string;
    customerName: string;
    customerPhone: string;
    customerEmail: string;
}

const STATUS_LABELS: Record<string, string> = {
    Created: 'Đã tạo',
    WaitingForPayment: 'Chờ thanh toán',
    PaymentProcessing: 'Đang thanh toán',
    PaymentSucceed: 'Đã thanh toán',
    PaymentFailed: 'Thanh toán thất bại',
    PaymentCancelled: 'Đã hủy',
    Expired: 'Hết hạn',
};

/** Preferred status order for the by-status chips. */
const STATUS_ORDER = [
    'PaymentSucceed',
    'WaitingForPayment',
    'PaymentProcessing',
    'Created',
    'PaymentFailed',
    'PaymentCancelled',
    'Expired',
];

@Component({
    selector: 'accessory-order-list',
    templateUrl: './accessory-order-list.component.html',
    styleUrls: ['./accessory-order-list.component.scss'],
    providers: [DecimalPipe, DatePipe],
})
export class AccessoryOrderListComponent implements OnInit {
    source: LocalDataSource = new LocalDataSource();
    loading = 0;
    shows: AccessoryShowModel[] = [];
    selectedShowId: string | null = null;
    stats: AccessoryOrderStatsModel | null = null;
    statusChips: Array<{ status: string; label: string; count: number }> = [];

    private ordersById = new Map<string, AccessoryOrderSummaryModel>();

    settings: Settings = {
        selectMode: 'single',
        rowClassFunction: (_row: Row) => 'ng2-custom-actions-inline order-row-clickable',
        hideSubHeader: false,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
        },
        columns: {
            code: { title: 'Mã đơn', type: IColumnType.Text },
            statusLabel: { title: 'Trạng thái', type: IColumnType.Text },
            totalLabel: { title: 'Thành tiền', type: IColumnType.Text, filter: false },
            timeLabel: { title: 'Thời gian', type: IColumnType.Text, filter: false },
            customerName: { title: 'Tên khách hàng', type: IColumnType.Text },
            customerPhone: { title: 'Số điện thoại', type: IColumnType.Text },
            customerEmail: { title: 'Email', type: IColumnType.Text },
        },
    };

    constructor(
        private accessoryShowService: AccessoryShowService,
        private toastrService: NbToastrService,
        private dialogService: NbDialogService,
        private datePipe: DatePipe,
        private decimalPipe: DecimalPipe,
        private route: ActivatedRoute,
        private router: Router,
    ) {}

    /** Loads show options; restores `showId` query param when present. */
    ngOnInit(): void {
        const queryShowId = this.route.snapshot.queryParamMap.get('showId');
        this.loading += 1;
        this.accessoryShowService
            .getShows$()
            .pipe(
                tap({
                    next: () => {
                        this.loading -= 1;
                    },
                    error: () => {
                        this.loading -= 1;
                    },
                }),
            )
            .subscribe({
                next: shows => {
                    this.shows = shows || [];
                    const fromQuery =
                        queryShowId && this.shows.some(s => s.id === queryShowId) ? queryShowId : null;
                    const fallback = this.shows.length === 1 ? this.shows[0].id : null;
                    const initial = fromQuery || fallback;
                    if (initial) {
                        this.onShowChange(initial);
                    }
                },
                error: error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không tải được show', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Formats VND for stat cards. */
    formatVnd(amount: number): string {
        return (this.decimalPipe.transform(amount, '1.0-0') || '0') + ' ₫';
    }

    /** Changes selected show and reloads orders/stats. */
    onShowChange(showId: string): void {
        this.selectedShowId = showId;
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { showId },
            queryParamsHandling: 'merge',
            replaceUrl: true,
        });
        this.reloadOrders();
    }

    /** Opens order detail modal when a table row is clicked. */
    onUserRowSelect(event: { data?: OrderTableRow }): void {
        const id = event?.data?.id;
        if (!id) return;
        const order = this.ordersById.get(id);
        if (!order) {
            this.toastrService.show('Không tìm thấy chi tiết đơn hàng', 'Lỗi', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        this.dialogService.open(AccessoryOrderDetailDialogComponent, {
            context: { order },
            closeOnBackdropClick: true,
            closeOnEsc: true,
        });
    }

    /** Fetches orders + stats for the selected show. */
    private reloadOrders(): void {
        if (!this.selectedShowId) {
            this.stats = null;
            this.statusChips = [];
            this.ordersById.clear();
            this.source.load([]);
            return;
        }
        this.loading += 1;
        this.accessoryShowService
            .getOrders$(this.selectedShowId)
            .pipe(
                tap({
                    next: () => {
                        this.loading -= 1;
                    },
                    error: () => {
                        this.loading -= 1;
                    },
                }),
            )
            .subscribe({
                next: res => {
                    this.stats = res.stats;
                    this.statusChips = this.buildStatusChips(res.stats.byStatus || {});
                    const orders = res.orders || [];
                    this.ordersById = new Map(orders.map(o => [o.id, o]));
                    this.source.load(orders.map(o => this.toTableRow(o)));
                },
                error: error => {
                    this.stats = null;
                    this.statusChips = [];
                    this.ordersById.clear();
                    this.source.load([]);
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không tải được đơn hàng', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Builds ordered status chips for the stats strip. */
    private buildStatusChips(byStatus: Record<string, number>): Array<{
        status: string;
        label: string;
        count: number;
    }> {
        const keys = [
            ...STATUS_ORDER.filter(s => (byStatus[s] || 0) > 0),
            ...Object.keys(byStatus).filter(s => !STATUS_ORDER.includes(s) && (byStatus[s] || 0) > 0),
        ];
        return keys.map(status => ({
            status,
            label: STATUS_LABELS[status] || status,
            count: byStatus[status] || 0,
        }));
    }

    /** Maps an order summary into a smart-table row. */
    private toTableRow(order: AccessoryOrderSummaryModel): OrderTableRow {
        return {
            id: order.id,
            code: order.code,
            statusLabel: STATUS_LABELS[order.status] || order.status,
            totalLabel: this.formatVnd(order.total),
            timeLabel:
                this.datePipe.transform(order.createdAt, 'dd/MM/yyyy HH:mm', '+0700') ||
                String(order.createdAt),
            customerName: order.customerName,
            customerPhone: order.customerPhone,
            customerEmail: order.customerEmail,
        };
    }
}
