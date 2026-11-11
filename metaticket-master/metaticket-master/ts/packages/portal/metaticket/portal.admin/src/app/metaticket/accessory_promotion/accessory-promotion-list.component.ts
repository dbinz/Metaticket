import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { LocalDataSource, Settings, IColumnType, Row } from 'angular2-smart-table';
import { tap } from 'rxjs/operators';
import { AccessoryShowService } from '../../@core/metaticket/data.service';
import {
    AccessoryDiscountModel,
    AccessoryDiscountTierModel,
    AccessoryDiscountUpsertInput,
    AccessoryShowModel,
} from '../../@core/metaticket/model';
import {
    AccessoryDiscountExcelRow,
    downloadDiscountExport,
    downloadDiscountTemplate,
    parseDiscountImportFile,
} from '../../@core/metaticket/utils/accessory-discount-excel.utils';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import {
    AccessoryPromotionBatchDialogComponent,
    AccessoryPromotionBatchDialogResult,
} from './accessory-promotion-batch-dialog.component';

@Component({
    selector: 'accessory-promotion-list',
    templateUrl: './accessory-promotion-list.component.html',
    styleUrls: ['./accessory-promotion-list.component.scss'],
    providers: [DecimalPipe],
})
export class AccessoryPromotionListComponent implements OnInit {
    @ViewChild('importFileInput') importFileInput?: ElementRef<HTMLInputElement>;

    source: LocalDataSource = new LocalDataSource();
    loading = 0;
    shows: AccessoryShowModel[] = [];
    selectedShowId: string | null = null;
    /** Last loaded discounts (for Excel export). */
    private discounts: AccessoryDiscountModel[] = [];

    settings: Settings = {
        rowClassFunction: (_row: Row) => 'ng2-custom-actions-inline',
        hideSubHeader: false,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
            custom: [
                {
                    name: 'edit-promo',
                    title: '<i class="eva eva-edit-2-outline" title="Sửa"></i>',
                },
                {
                    name: 'toggle-active',
                    title: '<i class="eva eva-swap-outline" title="Đổi Active/Inactive"></i>',
                },
                {
                    name: 'delete-promo',
                    title: '<i class="eva eva-trash-2-outline" title="Xóa"></i>',
                },
            ],
            position: 'left',
        },
        columns: {
            index: {
                title: 'STT',
                type: IColumnType.Html,
                filter: false,
                valuePrepareFunction: (_value, _row, cell) => {
                    const pager = this.source.getPaging();
                    return (pager.page - 1) * pager.perPage + cell.getRow().index + 1;
                },
            },
            code: { title: 'Mã', type: IColumnType.Text },
            typeLabel: { title: 'Loại', type: IColumnType.Text, filter: false },
            applyModeLabel: { title: 'Áp dụng', type: IColumnType.Text, filter: false },
            valueLabel: { title: 'Giá trị', type: IColumnType.Text, filter: false },
            usageLabel: { title: 'Lượt dùng', type: IColumnType.Text, filter: false },
            windowLabel: { title: 'Thời gian', type: IColumnType.Text, filter: false },
            activeLabel: { title: 'Trạng thái', type: IColumnType.Text, filter: false },
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
                    } else {
                        this.source.load([]);
                    }
                },
                error: error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không tải được danh sách show', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Updates selected show, syncs query string, and reloads promotions. */
    onShowChange(showId: string | null): void {
        this.selectedShowId = showId || null;
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { showId: this.selectedShowId || null },
            queryParamsHandling: 'merge',
            replaceUrl: true,
        });
        if (!this.selectedShowId) {
            this.discounts = [];
            this.source.load([]);
            return;
        }
        this.reloadPromotions();
    }

    /** Navigates to create form for the selected show. */
    createPromotion(): void {
        if (!this.selectedShowId) {
            return;
        }
        this.router.navigateByUrl(
            '/portal/accessory/promotion/create?showId=' + encodeURIComponent(this.selectedShowId),
        );
    }

    /** Opens batch-create dialog; API runs here so NbDialog stays free of module providers. */
    openBatchCreate(): void {
        if (!this.selectedShowId) {
            return;
        }
        this.dialogService
            .open(AccessoryPromotionBatchDialogComponent, {
                context: { showId: this.selectedShowId },
                closeOnBackdropClick: false,
            })
            .onClose.subscribe((result: AccessoryPromotionBatchDialogResult | null) => {
                if (!result?.inputs?.length) {
                    return;
                }
                this.loading += 1;
                this.accessoryShowService.createDiscountsBatch$(result.inputs).subscribe({
                    next: res => {
                        this.loading -= 1;
                        const failN = res.failures?.length || 0;
                        const okN = res.created?.length || 0;
                        this.toastrService.show(
                            `Tạo ${okN} mã, lỗi ${failN}`,
                            failN ? 'Hoàn tất có lỗi' : 'Thành công',
                            {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: failN ? 'warning' : 'success',
                                duration: failN ? 12000 : 4000,
                            },
                        );
                        if (failN) {
                            console.warn('discount batch failures', res.failures);
                        }
                        this.reloadPromotions();
                    },
                    error: error => {
                        this.loading -= 1;
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                });
            });
    }

    /** Downloads Excel template with sample rows. */
    async downloadTemplate(): Promise<void> {
        try {
            await downloadDiscountTemplate();
        } catch (e) {
            this.toastrService.show((e as Error).message || 'Tải template thất bại', 'Lỗi', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
            });
        }
    }

    /** Exports current show discounts to Excel. */
    async exportExcel(): Promise<void> {
        if (!this.selectedShowId) {
            return;
        }
        if (!this.discounts.length) {
            this.toastrService.show('Không có khuyến mãi để export', 'Trống', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        const show = this.shows.find(s => s.id === this.selectedShowId);
        const stamp = this.datePipe.transform(Date.now(), 'yyyyMMdd-HHmm') || 'export';
        const rows = this.discounts.map(d => this.toExcelRow(d));
        try {
            await downloadDiscountExport(rows, `accessory-discounts-${show?.code || 'show'}-${stamp}.xlsx`);
        } catch (e) {
            this.toastrService.show((e as Error).message || 'Export thất bại', 'Lỗi', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
            });
        }
    }

    /** Opens hidden file input for Excel import. */
    triggerImport(): void {
        if (!this.selectedShowId) {
            return;
        }
        this.importFileInput?.nativeElement?.click();
    }

    /** Parses Excel then batch-creates discounts for the selected show. */
    async onImportFileSelected(event: Event): Promise<void> {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        input.value = '';
        if (!file || !this.selectedShowId) {
            return;
        }
        try {
            const buffer = await file.arrayBuffer();
            const rows = await parseDiscountImportFile(buffer);
            if (!rows.length) {
                this.toastrService.show('File không có dòng Code hợp lệ', 'Trống', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                });
                return;
            }
            if (rows.length > 200) {
                this.toastrService.show('Tối đa 200 mã / lần import', 'Không hợp lệ', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                });
                return;
            }
            const inputs: AccessoryDiscountUpsertInput[] = [];
            for (const row of rows) {
                const mapped = this.fromExcelRow(row, this.selectedShowId);
                if (typeof mapped === 'string') {
                    this.toastrService.show(`${row.code}: ${mapped}`, 'Dòng lỗi', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'warning',
                        duration: 8000,
                    });
                    return;
                }
                inputs.push(mapped);
            }
            this.loading += 1;
            this.accessoryShowService.createDiscountsBatch$(inputs).subscribe({
                next: res => {
                    this.loading -= 1;
                    const failN = res.failures?.length || 0;
                    const okN = res.created?.length || 0;
                    this.toastrService.show(`Import: tạo ${okN}, lỗi ${failN}`, failN ? 'Có lỗi' : 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: failN ? 'warning' : 'success',
                        duration: failN ? 12000 : 4000,
                    });
                    if (failN) {
                        console.warn('discount import failures', res.failures);
                    }
                    this.reloadPromotions();
                },
                error: error => {
                    this.loading -= 1;
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Import thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
        } catch (e) {
            this.toastrService.show((e as Error).message || 'Không đọc được file', 'Lỗi', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
            });
        }
    }

    /** Handles smart-table custom row actions. */
    onCustomButton(event: {
        action: 'edit-promo' | 'toggle-active' | 'delete-promo';
        data: AccessoryDiscountModel;
    }): void {
        switch (event.action) {
            case 'edit-promo':
                this.router.navigateByUrl(
                    '/portal/accessory/promotion/update?id=' + encodeURIComponent(event.data.id),
                );
                break;
            case 'toggle-active':
                this.toggleActive(event.data);
                break;
            case 'delete-promo':
                this.deletePromo(event.data);
                break;
            default:
                break;
        }
    }

    /** Fetches promotions for the currently selected show. */
    reloadPromotions(): void {
        if (!this.selectedShowId) {
            return;
        }
        this.loading += 1;
        this.accessoryShowService
            .getDiscounts$(this.selectedShowId)
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
                next: items => {
                    this.discounts = items || [];
                    this.source.load(this.discounts.map(d => this.toTableRow(d)));
                },
                error: error => {
                    this.discounts = [];
                    this.source.load([]);
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Confirms then flips Active ↔ Inactive. */
    private toggleActive(item: AccessoryDiscountModel): void {
        const next = !item.active;
        const ok = window.confirm(
            `Đổi trạng thái "${item.code}" sang ${next ? 'Active' : 'Inactive'}?`,
        );
        if (!ok) return;

        this.loading += 1;
        this.accessoryShowService
            .setDiscountActive$(item.id, next)
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
                next: () => {
                    this.toastrService.show(`Đã đổi sang ${next ? 'Active' : 'Inactive'}`, 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                    this.reloadPromotions();
                },
                error: error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Confirms then hard-deletes a promotion (server rejects if already used). */
    private deletePromo(item: AccessoryDiscountModel): void {
        const ok = window.confirm(
            item.usedCount > 0
                ? `"${item.code}" đã dùng ${item.usedCount} lượt — server sẽ từ chối xóa. Nên tắt Active. Vẫn thử xóa?`
                : `Xóa khuyến mãi "${item.code}"? Thao tác không hoàn tác.`,
        );
        if (!ok) return;

        this.loading += 1;
        this.accessoryShowService
            .deleteDiscount$(item.id)
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
                next: () => {
                    this.toastrService.show('Đã xóa khuyến mãi', 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                    this.reloadPromotions();
                },
                error: error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không xóa được', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    private toExcelRow(d: AccessoryDiscountModel): AccessoryDiscountExcelRow {
        return {
            code: d.code,
            type: d.type,
            value: d.value,
            rewardType: d.rewardType || '',
            maxDiscountAmount: d.maxDiscountAmount ?? null,
            minOrderAmount: d.minOrderAmount || 0,
            usageLimit: d.usageLimit ?? null,
            startAt: d.startAt ?? null,
            endAt: d.endAt ?? null,
            active: !!d.active,
            tiersJson: d.type === 'Tiered' ? JSON.stringify(d.tiers || []) : '',
        };
    }

    /** Maps one Excel row → upsert input, or error message string. */
    private fromExcelRow(
        row: AccessoryDiscountExcelRow,
        showId: string,
    ): AccessoryDiscountUpsertInput | string {
        const type = row.type as AccessoryDiscountModel['type'];
        if (!['Percentage', 'FixedAmount', 'Tiered', 'EarlyBird'].includes(type)) {
            return 'Type không hợp lệ';
        }
        let tiers: AccessoryDiscountTierModel[] = [];
        if (type === 'Tiered') {
            if (!row.tiersJson?.trim()) {
                return 'Tiered cần TiersJson';
            }
            try {
                const parsed = JSON.parse(row.tiersJson) as AccessoryDiscountTierModel[];
                if (!Array.isArray(parsed) || !parsed.length) {
                    return 'TiersJson phải là mảng không rỗng';
                }
                tiers = parsed;
            } catch {
                return 'TiersJson không phải JSON hợp lệ';
            }
        }
        return {
            code: row.code,
            accessoryShowId: showId,
            type,
            value: Number(row.value) || 0,
            rewardType: type === 'EarlyBird' ? (row.rewardType as AccessoryDiscountModel['rewardType']) || 'Percentage' : null,
            maxDiscountAmount: row.maxDiscountAmount,
            minOrderAmount: Number(row.minOrderAmount) || 0,
            tiers,
            applyMode: 'Manual',
            usageLimit: row.usageLimit,
            startAt: row.startAt,
            endAt: row.endAt,
            active: row.active !== false,
        };
    }

    /** Formats a discount into smart-table display fields. */
    private toTableRow(d: AccessoryDiscountModel) {
        const fmt = (n: number) => this.decimalPipe.transform(n, '1.0-0') || String(n);
        let valueLabel = '—';
        if (d.type === 'Percentage') {
            valueLabel = `${d.value}%` + (d.maxDiscountAmount ? ` (cap ${fmt(d.maxDiscountAmount)})` : '');
        } else if (d.type === 'FixedAmount') {
            valueLabel = `${fmt(d.value)}đ`;
        } else if (d.type === 'EarlyBird') {
            valueLabel =
                d.rewardType === 'FixedAmount'
                    ? `${fmt(d.value)}đ`
                    : `${d.value}%` + (d.maxDiscountAmount ? ` (cap ${fmt(d.maxDiscountAmount)})` : '');
        } else if (d.type === 'Tiered') {
            valueLabel = `${d.tiers?.length || 0} mốc`;
        }

        const start = d.startAt ? this.datePipe.transform(d.startAt, 'dd/MM/yy HH:mm') : '…';
        const end = d.endAt ? this.datePipe.transform(d.endAt, 'dd/MM/yy HH:mm') : '…';

        return {
            ...d,
            typeLabel: d.type,
            applyModeLabel: d.applyMode === 'Auto' ? 'Tự động' : 'Nhập mã',
            valueLabel,
            usageLabel:
                d.usageLimit == null ? `${d.usedCount} / ∞` : `${d.usedCount} / ${d.usageLimit}`,
            windowLabel: !d.startAt && !d.endAt ? 'Không giới hạn' : `${start} → ${end}`,
            activeLabel: d.active ? 'Active' : 'Inactive',
        };
    }
}

