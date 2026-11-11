import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { LocalDataSource, Settings, IColumnType, Row } from 'angular2-smart-table';
import { tap } from 'rxjs/operators';
import { AccessoryShowService } from '../../@core/metaticket/data.service';
import { AccessoryProductModel, AccessoryShowModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';

@Component({
    selector: 'accessory-product-list',
    templateUrl: './accessory-product-list.component.html',
    styleUrls: ['./accessory-product-list.component.scss'],
    providers: [DecimalPipe],
})
export class AccessoryProductListComponent implements OnInit {
    source: LocalDataSource = new LocalDataSource();
    loading = 0;
    shows: AccessoryShowModel[] = [];
    selectedShowId: string | null = null;

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
                    name: 'edit-product',
                    title: '<i class="eva eva-edit-2-outline" title="Sửa"></i>',
                },
                {
                    name: 'toggle-active',
                    title: '<i class="eva eva-swap-outline" title="Đổi Active/Inactive"></i>',
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
            sku: {
                title: 'SKU',
                type: IColumnType.Text,
            },
            name: {
                title: 'Tên sản phẩm',
                type: IColumnType.Text,
            },
            priceLabel: {
                title: 'Giá',
                type: IColumnType.Text,
                filter: false,
            },
            stockLabel: {
                title: 'Tồn',
                type: IColumnType.Text,
                filter: false,
            },
            variantCount: {
                title: 'Biến thể',
                type: IColumnType.Text,
                filter: false,
            },
            activeLabel: {
                title: 'Trạng thái',
                type: IColumnType.Text,
                filter: false,
            },
            lastModified: {
                title: 'Cập nhật',
                type: IColumnType.Text,
                filter: false,
                valuePrepareFunction: (date: number) =>
                    date ? this.datePipe.transform(date, 'dd/MM/yyyy HH:mm') : '',
            },
        },
    };

    constructor(
        private accessoryShowService: AccessoryShowService,
        private toastrService: NbToastrService,
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

    /** Updates selected show, syncs query string, and reloads products. */
    onShowChange(showId: string | null): void {
        this.selectedShowId = showId || null;
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { showId: this.selectedShowId || null },
            queryParamsHandling: 'merge',
            replaceUrl: true,
        });
        if (!this.selectedShowId) {
            this.source.load([]);
            return;
        }
        this.reloadProducts();
    }

    /** Navigates to create form for the selected show. */
    createProduct(): void {
        if (!this.selectedShowId) {
            return;
        }
        this.router.navigateByUrl(
            '/portal/accessory/product/create?showId=' + encodeURIComponent(this.selectedShowId),
        );
    }

    /** Handles smart-table custom row actions. */
    onCustomButton(event: {
        action: 'edit-product' | 'toggle-active';
        data: AccessoryProductModel;
    }): void {
        switch (event.action) {
            case 'edit-product':
                this.router.navigateByUrl(
                    '/portal/accessory/product/update?id=' + encodeURIComponent(event.data.id),
                );
                break;
            case 'toggle-active':
                this.toggleActive(event.data);
                break;
            default:
                break;
        }
    }

    /** Fetches products for the currently selected show. */
    reloadProducts(): void {
        if (!this.selectedShowId) {
            return;
        }
        this.loading += 1;
        this.accessoryShowService
            .getProducts$(this.selectedShowId)
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
                next: products => {
                    this.source.load((products || []).map(p => this.toTableRow(p)));
                },
                error: error => {
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
    private toggleActive(product: AccessoryProductModel): void {
        const next = !product.active;
        const ok = window.confirm(
            `Đổi trạng thái "${product.name}" sang ${next ? 'Active' : 'Inactive'}?`,
        );
        if (!ok) return;

        this.loading += 1;
        this.accessoryShowService
            .setProductActive$(product.id, next)
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
                    this.toastrService.show(
                        `Đã đổi sang ${next ? 'Active' : 'Inactive'}`,
                        'Thành công',
                        {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'success',
                        },
                    );
                    this.reloadProducts();
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

    /** Formats a product into smart-table display fields. */
    private toTableRow(p: AccessoryProductModel) {
        const stockLabel = p.hasVariants
            ? `${p.variantCount} biến thể`
            : p.stock === -1
              ? 'Không giới hạn'
              : String(p.stock);
        return {
            ...p,
            priceLabel: `${this.decimalPipe.transform(p.price, '1.0-0') || p.price} ${p.currency || 'VND'}`,
            stockLabel,
            activeLabel: p.active ? 'Active' : 'Inactive',
        };
    }
}
