import { Component, OnInit, ViewContainerRef } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { NewsCategoryOption, NewsPinSlot, NewsPinSummary } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { NewsGateway } from '../gateway/news.gateway';
import { PinNewsPickerDialogComponent } from './pin-news-picker.dialog';

const SLOT_MAX = 3;

type BoardTab = 'featured' | 'category';

/** Admin boards: featured hub pins (max 3) and per-category pins (max 3). */
@Component({
    selector: 'pin-news',
    templateUrl: './pin-news.component.html',
    styleUrls: ['./pin-news.component.scss'],
})
export class PinNewsComponent implements OnInit {
    readonly maxSlots = SLOT_MAX;
    tab: BoardTab = 'featured';
    rows: NewsPinSlot[] = [];
    categories: NewsCategoryOption[] = [];
    categorySlug = '';
    loading = 0;
    dirty = false;

    constructor(
        private newsGateway: NewsGateway,
        private dialogService: NbDialogService,
        private toastr: NbToastrService,
        private vcr: ViewContainerRef,
    ) {}

    ngOnInit(): void {
        this.newsGateway.listCategories().subscribe({
            next: cats => {
                this.categories = (cats || []).filter(c => c.slug && c.slug !== 'uncategorized');
                if (!this.categorySlug && this.categories.length) {
                    this.categorySlug = this.categories[0].slug || '';
                }
            },
            error: (err: HttpErrorResponse) => this.fail(err, 'Không tải được danh mục'),
        });
        this.reload();
    }

    get orders(): number[] {
        return this.rows.map((_, i) => i + 1);
    }

    get canAdd(): boolean {
        return this.rows.length < SLOT_MAX;
    }

    get boardTitle(): string {
        return this.tab === 'featured' ? 'Ghim bài viết nổi bật' : 'Ghim theo thể loại';
    }

    /** Switch featured vs category board; unconfirmed edits are dropped. */
    onTabChange(tab: BoardTab): void {
        if (this.tab === tab) return;
        this.tab = tab;
        this.reload();
    }

    /** Reload the category board when the dropdown changes. */
    onCategoryChange(slug: string): void {
        this.categorySlug = slug;
        if (this.tab === 'category') {
            this.reload();
        }
    }

    /** Loads occupied pin slots (length 0..3). */
    reload(): void {
        if (this.tab === 'category' && !this.categorySlug) {
            this.rows = [];
            this.dirty = false;
            return;
        }
        this.loading += 1;
        const req =
            this.tab === 'featured'
                ? this.newsGateway.fetchFeaturedPinBoard()
                : this.newsGateway.fetchCategoryPinBoard(this.categorySlug);
        req.subscribe({
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

    /** Replace the board with the newest-edited published articles (up to 3). */
    refresh(): void {
        if (this.tab === 'category' && !this.categorySlug) return;
        this.loading += 1;
        const req =
            this.tab === 'featured'
                ? this.newsGateway.refreshFeaturedPinBoard()
                : this.newsGateway.refreshCategoryPinBoard(this.categorySlug);
        req.subscribe({
            next: board => {
                this.rows = this.normalize(board.slots);
                this.dirty = false;
                this.loading -= 1;
                this.toastr.show('Đã làm mới danh sách ghim bài viết', 'Thành công', {
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

    /** Append an empty row until max 3. */
    addSlot(): void {
        if (!this.canAdd) return;
        this.rows = [...this.rows, { pinOrder: this.rows.length + 1, article: null }];
        this.dirty = true;
    }

    /** Drop a row and compact pinOrder to 1..n. */
    removeSlot(row: NewsPinSlot): void {
        this.rows = this.rows
            .filter(r => r !== row)
            .map((r, i) => ({ ...r, pinOrder: i + 1 }));
        this.dirty = true;
    }

    /** Swap this row with the row that currently holds [newOrder] (FE only until confirm). */
    onOrderChange(row: NewsPinSlot, newOrder: number): void {
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
    replaceSlot(row: NewsPinSlot): void {
        this.dialogService
            .open(PinNewsPickerDialogComponent, {
                context: {
                    pinOrder: row.pinOrder,
                    categoryCode: this.tab === 'category' ? this.categorySlug : undefined,
                },
                viewContainerRef: this.vcr,
            })
            .onClose.subscribe((article?: NewsPinSummary) => {
                if (!article) return;
                const dup = this.rows.find(r => r !== row && r.article?.id === article.id);
                if (dup) {
                    this.toastr.show('Bài viết này đã có trên bảng ghim', 'Không thể chọn trùng', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'warning',
                        duration: 5000,
                    });
                    return;
                }
                row.article = article;
                this.dirty = true;
            });
    }

    /** Persist current FE order; empty rows are dropped server-side. */
    confirm(): void {
        if (!this.dirty) return;
        if (this.tab === 'category' && !this.categorySlug) return;
        this.loading += 1;
        const slots = this.rows.map(r => ({ pinOrder: r.pinOrder, articleId: r.article?.id ?? null }));
        const req =
            this.tab === 'featured'
                ? this.newsGateway.confirmFeaturedPinBoard(slots)
                : this.newsGateway.confirmCategoryPinBoard(this.categorySlug, slots);
        req.subscribe({
            next: board => {
                this.rows = this.normalize(board.slots);
                this.dirty = false;
                this.loading -= 1;
                this.toastr.show('Đã lưu thứ tự ghim bài viết', 'Thành công', {
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

    /** Thumbnail for a slot card. */
    imageOf(article: NewsPinSummary | null): string {
        return article?.thumbnailUrl || '';
    }

    private normalize(slots: NewsPinSlot[] | undefined): NewsPinSlot[] {
        return [...(slots || [])]
            .filter(s => s.article)
            .sort((a, b) => a.pinOrder - b.pinOrder)
            .map((s, i) => ({ pinOrder: i + 1, article: s.article }));
    }

    private fail(err: HttpErrorResponse, title: string): void {
        this.toastr.show(getErrorMessageFromResponse(err), title, {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'danger',
            duration: 30000,
        });
    }
}
