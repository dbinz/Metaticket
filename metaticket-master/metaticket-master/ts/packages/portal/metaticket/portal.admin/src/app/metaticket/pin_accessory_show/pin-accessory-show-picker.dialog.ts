import { Component, Input, OnInit } from '@angular/core';
import { NbDialogRef } from '@nebular/theme';
import { AccessoryPinShowSummary } from '../../@core/metaticket/model';
import { AccessoryShowGateway } from '../gateway/accessory-show.gateway';

const PAGE_SIZE = 10;

/** Paginated accessory-show picker for one pin slot. */
@Component({
    selector: 'pin-accessory-show-picker-dialog',
    templateUrl: './pin-accessory-show-picker.dialog.html',
    styleUrls: ['./pin-accessory-show-picker.dialog.scss'],
    providers: [AccessoryShowGateway],
})
export class PinAccessoryShowPickerDialogComponent implements OnInit {
    @Input() pinOrder = 1;

    keyword = '';
    page = 1;
    totalPages = 0;
    totalElements = 0;
    loading = false;
    items: AccessoryPinShowSummary[] = [];

    constructor(
        private accessoryGateway: AccessoryShowGateway,
        public ref: NbDialogRef<PinAccessoryShowPickerDialogComponent>,
    ) {}

    ngOnInit(): void {
        this.load(1);
    }

    /** Reloads the picker from page 1 after search. */
    onSearch(): void {
        this.load(1);
    }

    /** Loads one 1-based page of candidates. */
    load(page: number): void {
        this.loading = true;
        this.page = page;
        this.accessoryGateway.searchPinCandidates(page, PAGE_SIZE, this.keyword.trim() || undefined).subscribe({
            next: res => {
                this.items = res.content || [];
                this.page = res.page || page;
                this.totalPages = res.totalPages || 0;
                this.totalElements = res.totalElements || 0;
                this.loading = false;
            },
            error: () => {
                this.loading = false;
            },
        });
    }

    /** Chooses a show for the calling slot and closes. */
    select(show: AccessoryPinShowSummary): void {
        this.ref.close(show);
    }
}
