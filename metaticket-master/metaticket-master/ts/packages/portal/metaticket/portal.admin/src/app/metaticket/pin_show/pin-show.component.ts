import { Component, OnInit, ViewContainerRef } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { BannerPinSlot, BannerPinShowSummary, EventCategoryModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { ShowGateway } from '../gateway/show.gateway';
import { PinShowPickerDialogComponent } from './pin-show-picker.dialog';

const SLOT_COUNT = 10;

/** Admin board: 10 banner pin slots. Homepage top-banner logic is unchanged. */
@Component({
    selector: 'pin-show',
    templateUrl: './pin-show.component.html',
    styleUrls: ['./pin-show.component.scss'],
})
export class PinShowComponent implements OnInit {
    readonly orders = Array.from({ length: SLOT_COUNT }, (_, i) => i + 1);
    rows: BannerPinSlot[] = this.emptyRows();
    categories: EventCategoryModel[] = [];
    loading = 0;
    dirty = false;

    constructor(
        private showGateway: ShowGateway,
        private dialogService: NbDialogService,
        private toastr: NbToastrService,
        private vcr: ViewContainerRef,
    ) {}

    ngOnInit(): void {
        this.showGateway.getEventCategory().subscribe({
            next: cats => {
                this.categories = cats || [];
            },
        });
        this.reload();
    }

    /** Loads the persisted 10-slot board. */
    reload(): void {
        this.loading += 1;
        this.showGateway.fetchBannerPins().subscribe({
            next: board => {
                this.rows = this.normalize(board.slots);
                this.dirty = false;
                this.loading -= 1;
            },
            error: (err: HttpErrorResponse) => {
                this.loading -= 1;
                this.fail(err, 'Không tải được danh sách ghim');
            },
        });
    }

    /** Immediately replace the board with the 10 newest-edited shows. */
    refresh(): void {
        this.loading += 1;
        this.showGateway.refreshBannerPins().subscribe({
            next: board => {
                this.rows = this.normalize(board.slots);
                this.dirty = false;
                this.loading -= 1;
                this.toastr.show('Đã làm mới 10 sự kiện chỉnh sửa gần nhất', 'Thành công', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'success',
                    duration: 4000,
                });
            },
            error: (err: HttpErrorResponse) => {
                this.loading -= 1;
                this.fail(err, 'Làm mới thất bại');
            },
        });
    }

    /** Swap this row with the row that currently holds [newOrder] (FE only until confirm). */
    onOrderChange(row: BannerPinSlot, newOrder: number): void {
        const order = Number(newOrder);
        if (row.pinOrder === order) return;
        const other = this.rows.find(r => r.pinOrder === order);
        const oldOrder = row.pinOrder;
        row.pinOrder = order;
        if (other) {
            other.pinOrder = oldOrder;
        }
        this.rows = [...this.rows].sort((a, b) => a.pinOrder - b.pinOrder);
        this.dirty = true;
    }

    /** Opens the paginated picker and replaces this slot on the FE board. */
    replaceSlot(row: BannerPinSlot): void {
        this.dialogService
            .open(PinShowPickerDialogComponent, {
                context: { pinOrder: row.pinOrder, categories: this.categories },
                // NbDialog uses AppModule injector unless VCR is passed — ShowGateway lives on MetaticketModule
                viewContainerRef: this.vcr,
            })
            .onClose.subscribe((show?: BannerPinShowSummary) => {
                if (!show) return;
                row.show = show;
                this.dirty = true;
            });
    }

    /** Persist current FE order and replacements. */
    confirm(): void {
        if (!this.dirty) return;
        this.loading += 1;
        const slots = this.rows.map(r => ({ pinOrder: r.pinOrder, showId: r.show?.id ?? null }));
        this.showGateway.confirmBannerPins(slots).subscribe({
            next: board => {
                this.rows = this.normalize(board.slots);
                this.dirty = false;
                this.loading -= 1;
                this.toastr.show('Đã lưu thứ tự banner', 'Thành công', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'success',
                    duration: 4000,
                });
            },
            error: (err: HttpErrorResponse) => {
                this.loading -= 1;
                this.fail(err, 'Xác nhận thất bại');
            },
        });
    }

    /** Image for a slot card. */
    imageOf(show: BannerPinShowSummary | null): string {
        return show?.thumbnailUrl || show?.bannerImageUrl || '';
    }

    private emptyRows(): BannerPinSlot[] {
        return this.orders.map(pinOrder => ({ pinOrder, show: null }));
    }

    private normalize(slots: BannerPinSlot[] | undefined): BannerPinSlot[] {
        const byOrder = new Map((slots || []).map(s => [s.pinOrder, s]));
        return this.orders.map(pinOrder => ({
            pinOrder,
            show: byOrder.get(pinOrder)?.show ?? null,
        }));
    }

    private fail(err: HttpErrorResponse, title: string): void {
        this.toastr.show(getErrorMessageFromResponse(err), title, {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'danger',
            duration: 30000,
        });
    }
}
