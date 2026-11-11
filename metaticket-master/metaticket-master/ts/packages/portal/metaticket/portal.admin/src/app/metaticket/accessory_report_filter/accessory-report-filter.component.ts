import { Clipboard } from '@angular/cdk/clipboard';
import { DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
    NbGlobalPhysicalPosition,
    NbToastrService,
} from '@nebular/theme';
import { LocalDataSource, Settings, IColumnType, Row } from 'angular2-smart-table';
import { tap } from 'rxjs/operators';
import { AccessoryShowService } from '../../@core/metaticket/data.service';
import { AccessoryReportFilterModel, AccessoryShowModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';

/** Smart-table row with filter actions. */
interface FilterTableRow {
    id: string;
    reportKey: string;
    reportName: string;
    username: string;
    statusLabel: string;
    showLabel: string;
    windowLabel: string;
    createdLabel: string;
    status: string;
}

@Component({
    selector: 'accessory-report-filter',
    templateUrl: './accessory-report-filter.component.html',
    styleUrls: ['./accessory-report-filter.component.scss'],
    providers: [DatePipe],
})
export class AccessoryReportFilterComponent implements OnInit {
    source: LocalDataSource = new LocalDataSource();
    loading = 0;
    shows: AccessoryShowModel[] = [];
    selectedShowId: string | null = null;
    private showNameById = new Map<string, string>();

    settings: Settings = {
        rowClassFunction: (_row: Row) => 'ng2-custom-actions-inline',
        hideSubHeader: false,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
            custom: [
                { name: 'open', title: '<i class="eva eva-eye-outline" title="Mở thống kê"></i>' },
                { name: 'copy', title: '<i class="eva eva-copy-outline" title="Copy link"></i>' },
                { name: 'toggle', title: '<i class="eva eva-swap-outline" title="Bật/Tắt"></i>' },
                { name: 'delete', title: '<i class="eva eva-trash-2-outline" title="Xóa"></i>' },
            ],
            position: 'left',
        },
        columns: {
            reportName: { title: 'Tên filter', type: IColumnType.Text },
            showLabel: { title: 'Sự kiện', type: IColumnType.Text, filter: false },
            windowLabel: { title: 'Khoảng ngày', type: IColumnType.Text, filter: false },
            statusLabel: { title: 'Trạng thái', type: IColumnType.Text, filter: false },
            username: { title: 'Người tạo', type: IColumnType.Text },
            createdLabel: { title: 'Tạo lúc', type: IColumnType.Text, filter: false },
        },
    };

    constructor(
        private accessoryShowService: AccessoryShowService,
        private toastrService: NbToastrService,
        private clipboard: Clipboard,
        private datePipe: DatePipe,
        private route: ActivatedRoute,
        private router: Router,
    ) {}

    /** Loads shows then filter list. */
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
                    this.showNameById = new Map(
                        this.shows.map(s => [s.id, `${s.code} — ${s.name}`] as [string, string]),
                    );
                    const fromQuery =
                        queryShowId && this.shows.some(s => s.id === queryShowId) ? queryShowId : null;
                    this.selectedShowId = fromQuery;
                    this.reloadFilters();
                },
                error: error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không tải được show', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Changes optional show scope for listing. */
    onShowChange(showId: string | null): void {
        this.selectedShowId = showId || null;
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { showId: this.selectedShowId },
            queryParamsHandling: 'merge',
            replaceUrl: true,
        });
        this.reloadFilters();
    }

    /** Clears show filter (list all). */
    clearShow(): void {
        this.onShowChange(null);
    }

    /** Smart-table custom actions. */
    onCustomButton(event: { action: string; data: FilterTableRow }): void {
        if (event.action === 'open') {
            window.open(this.buildPublicLink(event.data.reportKey), '_blank', 'noopener');
            return;
        }
        if (event.action === 'copy') {
            const link = this.buildPublicLink(event.data.reportKey);
            if (this.clipboard.copy(link)) {
                this.toastrService.show(link, 'Đã copy link đối tác', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'success',
                });
            } else {
                this.toastrService.show('Không copy được clipboard', 'Thất bại', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'danger',
                });
            }
            return;
        }
        if (event.action === 'toggle') {
            this.toggleStatus(event.data);
            return;
        }
        if (event.action === 'delete') {
            this.deleteFilter(event.data);
        }
    }

    /** Partner-facing public report URL (no admin chrome). */
    private buildPublicLink(reportKey: string): string {
        return `${location.protocol}//${location.host}/public/accessory/report/${encodeURIComponent(reportKey)}`;
    }

    /** Reloads filter rows for current show scope. */
    private reloadFilters(): void {
        this.loading += 1;
        this.accessoryShowService
            .getReportFilters$(this.selectedShowId)
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
                next: list => {
                    this.source.load((list || []).map(f => this.toRow(f)));
                },
                error: error => {
                    this.source.load([]);
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không tải được filters', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Toggles Active/Inactive. */
    private toggleStatus(row: FilterTableRow): void {
        const next$ =
            row.status === 'Active'
                ? this.accessoryShowService.disableReportFilter$(row.id)
                : this.accessoryShowService.enableReportFilter$(row.id);
        this.loading += 1;
        next$
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
                next: () => this.reloadFilters(),
                error: error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Không đổi trạng thái', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Confirms then deletes a filter. */
    private deleteFilter(row: FilterTableRow): void {
        const ok = window.confirm(`Xóa filter "${row.reportName}"?`);
        if (!ok) return;
        this.loading += 1;
        this.accessoryShowService
            .deleteReportFilter$(row.id)
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
                    this.toastrService.show('Đã xóa filter', 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                    this.reloadFilters();
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

    /** Maps API model → table row. */
    private toRow(f: AccessoryReportFilterModel): FilterTableRow {
        const showId = f.filter?.showId || f.showId || '';
        const start = f.filter?.startAt
            ? this.datePipe.transform(f.filter.startAt, 'dd/MM/yyyy', '+0700')
            : '…';
        const endExclusive = f.filter?.endAt;
        const endLabel = endExclusive
            ? this.datePipe.transform(endExclusive - 1, 'dd/MM/yyyy', '+0700')
            : '…';
        return {
            id: f.id,
            reportKey: f.reportKey,
            reportName: f.reportName,
            username: f.username || '—',
            status: f.status,
            statusLabel: f.status === 'Active' ? 'Active' : 'Inactive',
            showLabel: this.showNameById.get(showId) || showId || '—',
            windowLabel: `${start} → ${endLabel}`,
            createdLabel: this.datePipe.transform(f.createdAt, 'dd/MM/yyyy HH:mm', '+0700') || '',
        };
    }
}
