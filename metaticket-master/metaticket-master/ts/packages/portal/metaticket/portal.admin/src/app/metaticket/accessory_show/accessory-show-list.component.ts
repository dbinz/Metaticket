import { DatePipe } from '@angular/common';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { LocalDataSource, Settings, IColumnType, Row } from 'angular2-smart-table';
import { tap } from 'rxjs/operators';
import { AccessoryShowService } from '../../@core/metaticket/data.service';
import { AccessoryShowModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';

@Component({
    selector: 'accessory-show-list',
    templateUrl: './accessory-show-list.component.html',
    styleUrls: ['./accessory-show-list.component.scss'],
})
export class AccessoryShowListComponent {
    source: LocalDataSource = new LocalDataSource();
    loading = 0;

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
                    name: 'edit-show',
                    title: '<i class="eva eva-edit-2-outline" title="Sửa"></i>',
                },
                {
                    name: 'toggle-status',
                    title: '<i class="eva eva-swap-outline" title="Open/Waiting → Closed, Closed → Open"></i>',
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
            code: {
                title: 'Mã show',
                type: IColumnType.Text,
            },
            name: {
                title: 'Tên show',
                type: IColumnType.Text,
            },
            status: {
                title: 'Trạng thái',
                type: IColumnType.Text,
                valuePrepareFunction: (status: string) =>
                    status === 'Waiting' ? 'Chờ mở bán' : status,
            },
            sessionCount: {
                title: 'Số session',
                type: IColumnType.Text,
                filter: false,
            },
            productCountLabel: {
                title: 'Số SP',
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
        private router: Router,
        private toastrService: NbToastrService,
        private datePipe: DatePipe,
    ) {
        this.reload();
    }

    /** Loads accessory shows into the smart-table source. */
    reload(): void {
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
                    const rows = shows.map(s => ({
                        ...s,
                        sessionCount: s.sessions?.length ?? 0,
                        productCountLabel:
                            s.productCount === null || s.productCount === undefined ? '—' : String(s.productCount),
                    }));
                    this.source.load(rows);
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

    /** Handles smart-table custom row actions. */
    onCustomButton(event: {
        action: 'edit-show' | 'toggle-status';
        data: AccessoryShowModel;
    }): void {
        switch (event.action) {
            case 'edit-show':
                this.router.navigateByUrl('/portal/accessory/show/update?id=' + event.data.id);
                break;
            case 'toggle-status':
                this.toggleStatus(event.data);
                break;
            default:
                break;
        }
    }

    /** Confirms then flips Open/Waiting → Closed and Closed → Open. */
    private toggleStatus(show: AccessoryShowModel): void {
        const next = show.status === 'Closed' ? 'Open' : 'Closed';
        const ok = window.confirm(`Đổi trạng thái show "${show.name}" sang ${next}?`);
        if (!ok) return;

        this.loading += 1;
        this.accessoryShowService
            .setStatus$(show.id, next)
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
                    this.toastrService.show(`Đã đổi sang ${next}`, 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                    this.reload();
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
}
