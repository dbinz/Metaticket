import { Component, Input, OnInit } from '@angular/core';
import { NbDialogRef } from '@nebular/theme';
import { BannerPinShowSummary, EventCategoryModel } from '../../@core/metaticket/model';
import { ShowGateway } from '../gateway/show.gateway';

const PAGE_SIZE = 10;

/** Paginated show picker for one banner pin slot. Duplicates with the board are allowed. */
@Component({
    selector: 'pin-show-picker-dialog',
    templateUrl: './pin-show-picker.dialog.html',
    styleUrls: ['./pin-show-picker.dialog.scss'],
    // Overlay dialog uses AppModule injector; keep a local gateway so Replace always resolves DI
    providers: [ShowGateway],
})
export class PinShowPickerDialogComponent implements OnInit {
    @Input() pinOrder = 1;
    @Input() categories: EventCategoryModel[] = [];

    keyword = '';
    eventCategoryCode = '';
    page = 1;
    totalPages = 0;
    totalElements = 0;
    loading = false;
    items: BannerPinShowSummary[] = [];

    constructor(
        private showGateway: ShowGateway,
        public ref: NbDialogRef<PinShowPickerDialogComponent>,
    ) {}

    ngOnInit(): void {
        this.load(1);
    }

    /** Reloads the picker from page 1 after search/filter. */
    onSearch(): void {
        this.load(1);
    }

    /** Loads one 1-based page of candidates. */
    load(page: number): void {
        this.loading = true;
        this.page = page;
        this.showGateway
            .searchPinCandidates(page, PAGE_SIZE, this.keyword.trim() || undefined, this.eventCategoryCode || undefined)
            .subscribe({
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
    select(show: BannerPinShowSummary): void {
        this.ref.close(show);
    }
}
