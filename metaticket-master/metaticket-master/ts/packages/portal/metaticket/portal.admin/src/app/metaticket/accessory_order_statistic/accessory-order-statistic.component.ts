import { DecimalPipe } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
    NbDialogService,
    NbGlobalPhysicalPosition,
    NbRangepickerComponent,
    NbToastrService,
} from '@nebular/theme';
import { LocalDataSource, Settings, IColumnType, Row } from 'angular2-smart-table';
import * as ExcelJS from 'exceljs';
import { firstValueFrom } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AccessoryShowService, PaymentService } from '../../@core/metaticket/data.service';
import {
    AccessoryAdminOrderExportModel,
    AccessoryOrderExportProductModel,
    AccessoryProductSalesRowModel,
    AccessorySalesStatisticModel,
    AccessoryShowModel,
    PaymentResponse,
} from '../../@core/metaticket/model';
import {
    productColumnsFromCatalog,
    productQty,
    saveWorkbook,
    variantDetailNotes,
    wrapCells,
} from '../../@core/metaticket/utils/accessory-order-excel.utils';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { DialogInputStringComponent } from '../dialog/dialog.input.string/dialog.input.string.component';

/** Row for product sales smart-table. */
interface ProductTableRow {
    name: string;
    sku: string;
    unitPriceLabel: string;
    quantity: number;
    grossLabel: string;
}

@Component({
    selector: 'accessory-order-statistic',
    templateUrl: './accessory-order-statistic.component.html',
    styleUrls: ['./accessory-order-statistic.component.scss'],
    providers: [DecimalPipe],
})
export class AccessoryOrderStatisticComponent implements OnInit {
    source: LocalDataSource = new LocalDataSource();
    loading = 0;
    shows: AccessoryShowModel[] = [];
    selectedShowId: string | null = null;
    startAt: number | null = null;
    endAt: number | null = null;
    statistic: AccessorySalesStatisticModel | null = null;
    loadedFilterName: string | null = null;

    rangepickerCtrl = this.formBuilder.group({
        rangepicker: new FormControl(undefined),
    });

    settings: Settings = {
        rowClassFunction: (_row: Row) => 'ng2-custom-actions-inline',
        hideSubHeader: false,
        actions: { columnTitle: 'Actions', add: false, edit: false, delete: false },
        columns: {
            name: { title: 'Sản phẩm', type: IColumnType.Text },
            sku: { title: 'SKU', type: IColumnType.Text },
            unitPriceLabel: { title: 'Đơn giá', type: IColumnType.Text, filter: false },
            quantity: { title: 'SL bán', type: IColumnType.Text, filter: false },
            grossLabel: { title: 'Doanh số', type: IColumnType.Text, filter: false },
        },
    };

    @ViewChild('rangepicker') rangepicker?: NbRangepickerComponent<Date>;

    constructor(
        private accessoryShowService: AccessoryShowService,
        private paymentService: PaymentService,
        private toastrService: NbToastrService,
        private dialogService: NbDialogService,
        private formBuilder: FormBuilder,
        private decimalPipe: DecimalPipe,
        private route: ActivatedRoute,
        private router: Router,
    ) {}

    /** Loads shows; applies reportKey or showId query params. */
    ngOnInit(): void {
        const reportKey = this.route.snapshot.queryParamMap.get('reportKey');
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
                    if (reportKey) {
                        this.loadFromReportKey(reportKey);
                        return;
                    }
                    const fromQuery =
                        queryShowId && this.shows.some(s => s.id === queryShowId) ? queryShowId : null;
                    const fallback = this.shows.length === 1 ? this.shows[0].id : null;
                    this.selectedShowId = fromQuery || fallback;
                    if (this.selectedShowId) {
                        this.loadStatistic();
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

    get rangepickerFormCtr(): FormControl {
        return this.rangepickerCtrl.get('rangepicker') as FormControl;
    }

    /** Formats VND amounts. */
    formatVnd(amount: number): string {
        return (this.decimalPipe.transform(amount, '1.0-0') || '0') + ' ₫';
    }

    /** Updates selected show (does not auto-query). */
    onShowChange(showId: string): void {
        this.selectedShowId = showId;
        this.loadedFilterName = null;
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { showId, reportKey: null },
            queryParamsHandling: 'merge',
            replaceUrl: true,
        });
    }

    /** Captures date range; end becomes exclusive next-day midnight (ms). */
    onDateSelected(picker: NbRangepickerComponent<Date>): void {
        this.startAt = picker?.value?.start ? Date.parse(String(picker.value.start)) : null;
        this.endAt = picker?.value?.end
            ? Date.parse(String(picker.value.end)) + 24 * 60 * 60 * 1000
            : null;
    }

    /** Clears selection and results. */
    clear(): void {
        this.selectedShowId = null;
        this.startAt = null;
        this.endAt = null;
        this.statistic = null;
        this.loadedFilterName = null;
        this.rangepickerFormCtr.setValue(undefined);
        this.source.load([]);
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { showId: null, reportKey: null },
            replaceUrl: true,
        });
    }

    /** Loads statistic for current show + date range. */
    loadStatistic(): void {
        if (!this.selectedShowId) {
            this.toastrService.show('Vui lòng chọn sự kiện bán hàng', 'Thiếu thông tin', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        this.loading += 1;
        this.accessoryShowService
            .getOrderStats$(this.selectedShowId, this.startAt, this.endAt)
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
                    this.statistic = res;
                    this.source.load((res.byProduct || []).map(p => this.toProductRow(p)));
                },
                error: error => {
                    this.statistic = null;
                    this.source.load([]);
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không tải được thống kê', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Saves current query as a shareable filter preset. */
    saveFilter(): void {
        if (!this.selectedShowId) {
            this.toastrService.show('Vui lòng chọn sự kiện bán hàng trước', 'Thiếu thông tin', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        this.dialogService
            .open(DialogInputStringComponent, {
                context: { title: 'Nhập tên filter muốn lưu', inputData: '' },
            })
            .onClose.subscribe((reportName: string | number | undefined) => {
                if (reportName === undefined || reportName === null) return;
                const name = String(reportName).trim();
                if (!name) {
                    this.toastrService.show('Tên filter không để trống', 'Không lưu được', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                    });
                    return;
                }
                this.persistFilter(name);
            });
    }

    /** POSTs the filter; username is optional metadata only. */
    private persistFilter(reportName: string): void {
        this.loading += 1;
        this.accessoryShowService
            .createReportFilter$({
                reportName,
                username: '',
                showId: this.selectedShowId!,
                startAt: this.startAt ?? null,
                endAt: this.endAt ?? null,
            })
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
                next: reportKey => {
                    const link = this.buildShareLink(reportKey);
                    this.toastrService.show(`Đã lưu. Link: ${link}`, 'Filter đã lưu', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                        duration: 10000,
                    });
                },
                error: error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không lưu được filter', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Downloads order-list Excel (admin columns + dynamic product qty columns). */
    async downloadExcel(): Promise<void> {
        if (!this.selectedShowId) {
            this.toastrService.show('Vui lòng chọn sự kiện bán hàng trước', 'Thiếu thông tin', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }

        this.loading += 1;
        try {
            const bundle = await firstValueFrom(
                this.accessoryShowService.exportOrders$(this.selectedShowId, this.startAt, this.endAt),
            );
            const orders = bundle?.orders || [];
            // Zero-pay / missing paymentId → skip lookup; Excel payment columns stay blank.
            const paymentIds = orders.map(o => o.paymentId).filter((id): id is string => !!id);
            let payments: PaymentResponse[] = [];
            if (paymentIds.length) {
                try {
                    payments = (await firstValueFrom(this.paymentService.getPayments(paymentIds))) || [];
                } catch {
                    // Payment enrich is best-effort — still export order rows with empty payment cols.
                    payments = [];
                }
            }
            const paymentById = new Map(payments.map(p => [p.id, p]));
            await this.writeAdminExcel(bundle?.products || [], orders, paymentById);
            this.toastrService.show('Đã tải file Excel danh sách đơn', 'Excel', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'success',
            });
        } catch (error) {
            this.toastrService.show(getErrorMessageFromResponse(error), 'Không tải được Excel', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
        } finally {
            this.loading -= 1;
        }
    }

    /** Builds the admin workbook: identity/payment/customer + product columns + KM. */
    private async writeAdminExcel(
        products: AccessoryOrderExportProductModel[],
        orders: AccessoryAdminOrderExportModel[],
        paymentById: Map<string, PaymentResponse>,
    ): Promise<void> {
        const productCols = productColumnsFromCatalog(products);
        const hasDelivery = orders.some(o => !!(o.deliveryAddress || o.deliveryCity || o.deliveryWard));
        const hasPickup = orders.some(o => !!(o.showSessionId || o.pickupPointId));
        const headers = [
            'STT',
            'Mã',
            'Trạng thái đơn hàng',
            'Mã Giao Dịch',
            'Gateway',
            'Tài Khoản',
            'Hình thức nhận',
        ];
        if (hasPickup) {
            headers.push('Điểm nhận');
        }
        headers.push(
            'Tên Khách Hàng',
            'Số Điện Thoại',
            'Mail',
            'Thời Gian',
        );
        if (hasDelivery) {
            headers.push('Địa Chỉ', 'Phường/Xã', 'Tỉnh/TP', 'Ghi chú giao hàng');
        }
        for (const col of productCols) headers.push(col.header);
        headers.push('Chi tiết biến thể', 'Mã KM', 'Giảm giá', 'Thành tiền');

        const workbook = new ExcelJS.Workbook();
        workbook.creator = 'MetaTicket Admin';
        const sheet = workbook.addWorksheet('Don hang');
        sheet.addRow(headers);

        orders.forEach((order, idx) => {
            const payment = order.paymentId ? paymentById.get(order.paymentId) : undefined;
            const row: Array<string | number> = [
                idx + 1,
                order.code,
                order.status,
                payment?.paymentNo || '',
                payment?.gateway || '',
                payment?.pgAccountCode || '',
                order.fulfillment,
            ];
            if (hasPickup) {
                row.push(order.showSessionLabel || order.pickupPointName || '');
            }
            row.push(
                order.customerName,
                order.customerPhone,
                order.customerEmail,
                new Date(order.createdAt).toLocaleString('vi-VN'),
            );
            if (hasDelivery) {
                row.push(
                    order.deliveryAddress || '',
                    order.deliveryWard || '',
                    order.deliveryCity || '',
                    order.deliveryNote || '',
                );
            }
            for (const col of productCols) {
                row.push(productQty(order.lines, col.accessoryId));
            }
            const variantNotes = variantDetailNotes(order.lines);
            row.push(variantNotes, order.discountCode || '', order.discountAmount || 0, order.total);
            const excelRow = sheet.addRow(row);
            if (variantNotes) {
                wrapCells(excelRow, [headers.indexOf('Chi tiết biến thể') + 1]);
            }
        });

        const show = this.shows.find(s => s.id === this.selectedShowId);
        const showLabel = show ? `${show.code}` : this.selectedShowId!;
        await saveWorkbook(workbook, `accessory-orders-${showLabel}-${this.dateStamp()}.xlsx`);
    }

    /** Filename-safe local date stamp. */
    private dateStamp(): string {
        const d = new Date();
        const pad = (n: number) => String(n).padStart(2, '0');
        return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(
            d.getMinutes(),
        )}`;
    }

    /** Applies a saved Active filter by reportKey. */
    private loadFromReportKey(reportKey: string): void {
        this.loading += 1;
        this.accessoryShowService
            .getReportFilterByKey$(reportKey)
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
                next: filter => {
                    this.loadedFilterName = filter.reportName;
                    this.selectedShowId = filter.filter.showId || filter.showId || null;
                    this.startAt = filter.filter.startAt ?? null;
                    this.endAt = filter.filter.endAt ?? null;
                    if (this.selectedShowId) {
                        this.loadStatistic();
                    }
                },
                error: error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không mở được filter', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Share URL for partners (standalone public report UI). */
    private buildShareLink(reportKey: string): string {
        return `${location.protocol}//${location.host}/public/accessory/report/${encodeURIComponent(reportKey)}`;
    }

    /** Maps product sales row for the table. */
    private toProductRow(p: AccessoryProductSalesRowModel): ProductTableRow {
        return {
            name: p.name,
            sku: p.sku,
            unitPriceLabel: this.formatVnd(p.unitPrice),
            quantity: p.quantity,
            grossLabel: this.formatVnd(p.grossSales),
        };
    }
}
