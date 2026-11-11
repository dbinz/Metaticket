import { Component, Input, OnInit } from '@angular/core';
import { NbDialogRef } from '@nebular/theme';
import { NewsPinSummary } from '../../@core/metaticket/model';
import { NewsGateway } from '../gateway/news.gateway';

const PAGE_SIZE = 10;

/** Paginated article picker for one news pin slot. */
@Component({
    selector: 'pin-news-picker-dialog',
    templateUrl: './pin-news-picker.dialog.html',
    styleUrls: ['./pin-news-picker.dialog.scss'],
    providers: [NewsGateway],
})
export class PinNewsPickerDialogComponent implements OnInit {
    @Input() pinOrder = 1;
    @Input() categoryCode: string | undefined;

    keyword = '';
    page = 1;
    totalPages = 0;
    totalElements = 0;
    loading = false;
    items: NewsPinSummary[] = [];

    constructor(
        private newsGateway: NewsGateway,
        public ref: NbDialogRef<PinNewsPickerDialogComponent>,
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
        this.newsGateway
            .searchPinCandidates(page, PAGE_SIZE, this.keyword.trim() || undefined, this.categoryCode)
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

    /** Chooses an article for the calling slot and closes. */
    select(article: NewsPinSummary): void {
        this.ref.close(article);
    }
}
