import { DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { NewsService } from '../../@core/metaticket/data.service';
import { NewsArticleRow, NewsCategoryOption } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { IColumnType, LocalDataSource, Settings } from 'angular2-smart-table';
import { tap } from 'rxjs/operators';

/**
 * News admin list: visibility in portal; create/edit redirects to WordPress on news.*.
 */
@Component({
    templateUrl: './news-list.component.html',
    styleUrls: ['./news-list.component.scss'],
    selector: 'news-list',
})
export class NewsListComponent implements OnInit {
    source: LocalDataSource = new LocalDataSource();
    loading = 0;
    searchText = '';
    categoryFilter = '';
    categories: NewsCategoryOption[] = [];

    settings: Settings = {
        rowClassFunction: () => 'ng2-custom-actions-inline',
        hideSubHeader: true,
        actions: {
            columnTitle: 'Thao tác',
            add: false,
            edit: false,
            delete: false,
            custom: [
                {
                    name: 'edit-article',
                    title: '<i class="eva eva-edit-2-outline" title="Sửa trên WordPress"></i>',
                },
                {
                    name: 'toggle-visibility',
                    title: '<i class="eva eva-eye-outline" title="Ẩn/Hiện"></i>',
                },
            ],
            position: 'right',
        },
        columns: {
            title: {
                title: 'Tiêu đề',
                type: IColumnType.Text,
            },
            categoriesLabel: {
                title: 'Danh mục',
                type: IColumnType.Text,
            },
            visibleLabel: {
                title: 'Hiển thị',
                type: IColumnType.Text,
            },
            modifiedAt: {
                title: 'Cập nhật',
                type: IColumnType.Text,
                valuePrepareFunction: (value: string) => {
                    if (!value) return '';
                    return this.datePipe.transform(value, 'dd/MM/yyyy HH:mm') || value;
                },
            },
        },
    };

    constructor(
        private newsService: NewsService,
        private toastrService: NbToastrService,
        private datePipe: DatePipe,
    ) {}

    ngOnInit(): void {
        this.loadCategories();
        this.loadArticles();
    }

    /** Redirect to WordPress new-post screen on news.* subdomain. */
    openCreateOnWordpress(): void {
        window.location.href = this.wordpressEditorUrl();
    }

    loadCategories(): void {
        this.newsService.listCategories().subscribe(
            categories => {
                this.categories = categories || [];
            },
            () => {
                this.categories = [];
            },
        );
    }

    loadArticles(): void {
        this.loading += 1;
        this.newsService
            .listArticles(0, 100, this.searchText, this.categoryFilter || undefined)
            .pipe(
                tap(
                    () => {
                        this.loading -= 1;
                    },
                    () => {
                        this.loading -= 1;
                    },
                ),
            )
            .subscribe(
                page => {
                    const rows = (page?.content || []).map(row => this.toTableRow(row));
                    this.source.load(rows);
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Tải danh sách tin tức thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    onSearch(): void {
        this.loadArticles();
    }

    onCustomButton(event: {
        action: 'edit-article' | 'toggle-visibility';
        data: NewsArticleRow & { categoriesLabel: string; visibleLabel: string };
    }): void {
        const row = event.data;
        switch (event.action) {
            case 'edit-article':
                // Content editing lives on WordPress CMS, not embedded in portal
                window.location.href = this.wordpressEditorUrl(row.id);
                break;
            case 'toggle-visibility':
                this.toggleVisibility(row);
                break;
        }
    }

    /** Build WP admin URL: admin.* → news.*, same protocol/port (local nginx). */
    private wordpressEditorUrl(postId?: number): string {
        const protocol = window.location.protocol;
        const host = window.location.hostname.replace(/^admin\./, 'news.');
        const port = window.location.port ? `:${window.location.port}` : '';
        const wpBase = `${protocol}//${host}${port}`;
        if (postId != null && Number.isFinite(postId)) {
            return `${wpBase}/wp-admin/post.php?post=${encodeURIComponent(String(postId))}&action=edit`;
        }
        return `${wpBase}/wp-admin/post-new.php`;
    }

    private toggleVisibility(row: NewsArticleRow): void {
        this.loading += 1;
        this.newsService
            .setVisibility(row.id, !row.visible)
            .pipe(
                tap(
                    () => {
                        this.loading -= 1;
                    },
                    () => {
                        this.loading -= 1;
                    },
                ),
            )
            .subscribe(
                () => {
                    this.toastrService.show(
                        row.visible ? 'Đã ẩn bài viết' : 'Đã hiển thị bài viết',
                        'Thành công',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                    );
                    this.loadArticles();
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Cập nhật trạng thái thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    private toTableRow(row: NewsArticleRow) {
        return {
            ...row,
            categoriesLabel: (row.categories || []).join(', '),
            visibleLabel: row.visible ? 'Hiện' : 'Ẩn',
        };
    }
}
