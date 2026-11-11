import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import * as ExcelJS from 'exceljs';
import { firstValueFrom } from 'rxjs';
import { tap } from 'rxjs/operators';
import { publicApi } from '../../../environments/environment';
import { AccessoryPublicOrderExportBundle } from '../../@core/metaticket/model';
import {
    productColumnsFromCatalog,
    productQty,
    saveWorkbook,
    variantDetailNotes,
    wrapCells,
} from '../../@core/metaticket/utils/accessory-order-excel.utils';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';

/** Public partner report payload from service.accessory. */
interface PublicSalesReportDto {
    reportKey: string;
    reportName: string;
    showId?: string | null;
    showCode?: string | null;
    showName?: string | null;
    startAt?: number | null;
    endAt?: number | null;
    summary: {
        totalOrders: number;
        paidOrders: number;
        unitsSold: number;
        grossSales: number;
        discountTotal: number;
        revenue: number;
    };
    byProduct: Array<{
        sku: string;
        name: string;
        unitPrice: number;
        quantity: number;
        grossSales: number;
    }>;
}

/**
 * Standalone partner view: `/public/accessory/report/:reportKey`.
 * No admin menu — only the saved sales report + order-list Excel.
 */
@Component({
    selector: 'accessory-public-report',
    templateUrl: './accessory-public-report.component.html',
    styleUrls: ['./accessory-public-report.component.scss'],
    providers: [DecimalPipe],
})
export class AccessoryPublicReportComponent implements OnInit {
    loading = 0;
    report: PublicSalesReportDto | null = null;
    errorMessage: string | null = null;
    private reportKey = '';

    constructor(
        private route: ActivatedRoute,
        private http: HttpClient,
        private toastr: NbToastrService,
        private decimalPipe: DecimalPipe,
    ) {
        this.reportKey = this.route.snapshot.paramMap.get('reportKey') || '';
    }

    /** Loads the public report by reportKey. */
    ngOnInit(): void {
        if (!this.reportKey) {
            this.errorMessage = 'Thiếu mã báo cáo';
            return;
        }
        this.loading += 1;
        this.http
            .get<PublicSalesReportDto>(publicApi(`accessory/report/${encodeURIComponent(this.reportKey)}`))
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
                    this.report = res;
                    this.errorMessage = null;
                },
                error: err => {
                    this.report = null;
                    this.errorMessage = getErrorMessageFromResponse(err) || 'Không tải được báo cáo';
                },
            });
    }

    /** Formats VND. */
    formatVnd(amount: number): string {
        return (this.decimalPipe.transform(amount, '1.0-0') || '0') + ' ₫';
    }

    /** Human-readable date window (endAt exclusive → display last inclusive day). */
    windowLabel(): string {
        if (!this.report) return '';
        const start = this.report.startAt
            ? new Date(this.report.startAt).toLocaleDateString('vi-VN')
            : '…';
        const end = this.report.endAt
            ? new Date(this.report.endAt - 1).toLocaleDateString('vi-VN')
            : '…';
        return `${start} → ${end}`;
    }

    /** Downloads partner Excel: paid orders only, lean columns + KM. */
    async downloadExcel(): Promise<void> {
        if (!this.reportKey) return;
        this.loading += 1;
        try {
            const bundle = await firstValueFrom(
                this.http.get<AccessoryPublicOrderExportBundle>(
                    publicApi(`accessory/report/${encodeURIComponent(this.reportKey)}/orders`),
                ),
            );
            await this.writePublicExcel(bundle);
            this.toastr.show('Đã tải Excel danh sách đơn', 'Excel', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'success',
            });
        } catch (err) {
            this.toastr.show(getErrorMessageFromResponse(err) || 'Không tải được Excel', 'Excel', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
        } finally {
            this.loading -= 1;
        }
    }

    /** Builds partner workbook (no payment / phone / status). */
    private async writePublicExcel(bundle: AccessoryPublicOrderExportBundle): Promise<void> {
        const products = bundle?.products || [];
        const orders = bundle?.orders || [];
        const productCols = productColumnsFromCatalog(products);
        const headers = ['STT', 'Mã đơn', 'Thời gian', 'Tên khách hàng'];
        for (const col of productCols) headers.push(col.header);
        headers.push('Chi tiết biến thể', 'Mã KM', 'Giảm giá', 'Thành tiền');

        const workbook = new ExcelJS.Workbook();
        const sheet = workbook.addWorksheet('Don hang');
        sheet.addRow(headers);

        orders.forEach((order, idx) => {
            const row: Array<string | number> = [
                idx + 1,
                order.code,
                new Date(order.createdAt).toLocaleString('vi-VN'),
                order.customerName,
            ];
            for (const col of productCols) {
                row.push(productQty(order.lines, col.accessoryId));
            }
            const notes = variantDetailNotes(order.lines);
            row.push(notes, order.discountCode || '', order.discountAmount || 0, order.total);
            const excelRow = sheet.addRow(row);
            if (notes) {
                wrapCells(excelRow, [headers.indexOf('Chi tiết biến thể') + 1]);
            }
        });

        const stamp = `${this.report?.showCode}-${(this.report?.reportKey || this.reportKey).slice(0, 8)}.xlsx`;
        await saveWorkbook(workbook, `${stamp}.xlsx`);
    }
}
