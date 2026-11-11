import { Component, OnInit, ViewContainerRef } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { AccessoryPinShowSummary, AccessoryPinSlot } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { AccessoryShowGateway } from '../gateway/accessory-show.gateway';
import { PinAccessoryShowPickerDialogComponent } from './pin-accessory-show-picker.dialog';

const SLOT_MAX = 5;

/** Admin board: 1..5 accessory merch pins. Homepage merch strip is a later change. */
@Component({
    selector: 'pin-accessory-show',
    templateUrl: './pin-accessory-show.component.html',
    styleUrls: ['./pin-accessory-show.component.scss'],
})
export class PinAccessoryShowComponent implements OnInit {
    readonly maxSlots = SLOT_MAX;
    rows: AccessoryPinSlot[] = [];
    loading = 0;
    dirty = false;

    constructor(
        private accessoryGateway: AccessoryShowGateway,
        private dialogService: NbDialogService,
        private toastr: NbToastrService,
        private vcr: ViewContainerRef,
    ) {}

    ngOnInit(): void {
        this.reload();
    }

    get orders(): number[] {
        return this.rows.map((_, i) => i + 1);
    }

    get canAdd(): boolean {
        return this.rows.length < SLOT_MAX;
    }

    /** Loads occupied pin slots (length 0..5). */
    reload(): void {
        this.loading += 1;
        this.accessoryGateway.fetchPinBoard().subscribe({
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

    /** Replace the board with the newest-edited accessory shows (up to 5). */
    refresh(): void {
        this.loading += 1;
        this.accessoryGateway.refreshPinBoard().subscribe({
            next: board => {
                this.rows = this.normalize(board.slots);
                this.dirty = false;
                this.loading -= 1;
                this.toastr.show('Đã làm mới danh sách ghim sản phẩm', 'Thành công', {
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

    /** Append an empty row until max 5. */
    addSlot(): void {
        if (!this.canAdd) return;
        this.rows = [...this.rows, { pinOrder: this.rows.length + 1, show: null }];
        this.dirty = true;
    }

    /** Drop a row and compact pinOrder to 1..n. */
    removeSlot(row: AccessoryPinSlot): void {
        this.rows = this.rows
            .filter(r => r !== row)
            .map((r, i) => ({ ...r, pinOrder: i + 1 }));
        this.dirty = true;
    }

    /** Swap this row with the row that currently holds [newOrder] (FE only until confirm). */
    onOrderChange(row: AccessoryPinSlot, newOrder: number): void {
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
    replaceSlot(row: AccessoryPinSlot): void {
        this.dialogService
            .open(PinAccessoryShowPickerDialogComponent, {
                context: { pinOrder: row.pinOrder },
                viewContainerRef: this.vcr,
            })
            .onClose.subscribe((show?: AccessoryPinShowSummary) => {
                if (!show) return;
                const dup = this.rows.find(r => r !== row && r.show?.id === show.id);
                if (dup) {
                    this.toastr.show('Sự kiện này đã có trên bảng ghim', 'Không thể chọn trùng', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'warning',
                        duration: 5000,
                    });
                    return;
                }
                row.show = show;
                this.dirty = true;
            });
    }

    /** Persist current FE order; empty rows are dropped server-side. */
    confirm(): void {
        if (!this.dirty) return;
        this.loading += 1;
        const slots = this.rows.map(r => ({ pinOrder: r.pinOrder, showId: r.show?.id ?? null }));
        this.accessoryGateway.confirmPinBoard(slots).subscribe({
            next: board => {
                this.rows = this.normalize(board.slots);
                this.dirty = false;
                this.loading -= 1;
                this.toastr.show('Đã lưu thứ tự ghim sản phẩm', 'Thành công', {
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

    /** Banner image for a slot card. */
    imageOf(show: AccessoryPinShowSummary | null): string {
        return show?.bannerImageUrl || '';
    }

    private normalize(slots: AccessoryPinSlot[] | undefined): AccessoryPinSlot[] {
        return [...(slots || [])]
            .filter(s => s.show)
            .sort((a, b) => a.pinOrder - b.pinOrder)
            .map((s, i) => ({ pinOrder: i + 1, show: s.show }));
    }

    private fail(err: HttpErrorResponse, title: string): void {
        this.toastr.show(getErrorMessageFromResponse(err), title, {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'danger',
            duration: 30000,
        });
    }
}
