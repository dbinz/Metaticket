import { DatePipe } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { ShowService } from '../../@core/metaticket/data.service';
import { ShowModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { LocalDataSource, Settings, IColumnType, Row } from 'angular2-smart-table';
import { take, tap, timeout } from 'rxjs/operators';

@Component({
    templateUrl: './shows.component.html',
    // styleUrls: ['./shows.component.scss'],
    selector: 'shows',
})
export class ShowsComponent {
    source: LocalDataSource = new LocalDataSource();
    loading: number = 0;

    settings: Settings = {
        rowClassFunction: (row: Row) => 'ng2-custom-actions-inline',
        hideSubHeader: true,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
            custom: [
                {
                    name: 'edit-show',
                    title: '<i class="eva eva-edit-2-outline" title="Edit"></i>',
                },
                {
                    name: 'view-session',
                    title: '<i class="eva eva-list-outline" title="Sessions"></i>',
                },
                {
                    name: 'get-detail-link',
                    title: '<i class="eva eva-link-2-outline" title="Lấy link trang bán vé"></i>',
                },
            ],
            position: 'left',
        },
        columns: {
            index: {
                title: 'STT',
                type: IColumnType.Html,
                filter: false,
                valuePrepareFunction: (value, row, cell) => {
                    const pager = this.source.getPaging();
                    const page = pager.page; // Trang hiện tại
                    const perPage = pager.perPage; // Số dòng mỗi trang

                    // Công thức: (Trang - 1) * Số dòng + Chỉ mục hàng hiện tại + 1
                    return (page - 1) * perPage + cell.getRow().index + 1;
                },
            },
            code: {
                title: 'Mã sự kiện',
                type: IColumnType.Text,
            },
            showName: {
                title: 'Tên sự kiện',
                type: IColumnType.Text,
            },
            createdAt: {
                title: 'Ngày tạo',
                type: IColumnType.Text,
                valuePrepareFunction: date => {
                    if (date) {
                        // Định dạng dd/MM/yyyy cho ngày tháng Việt Nam
                        return this.datePipe.transform(date, 'dd/MM/yyyy HH:mm');
                    }
                    return '';
                },
            },
        },
    };

    constructor(
        private showService: ShowService,
        private router: Router,
        private toastrService: NbToastrService,
        private datePipe: DatePipe,
        private clipboard: Clipboard,
    ) {
        this.loading += 1;
        showService
            .getShows$()
            .pipe(
                tap(
                    e => {
                        this.loading -= 1;
                    },
                    e => {
                        this.loading -= 1;
                    },
                ),
            )
            .subscribe(
                shows => {
                    if (!shows) return;
                    shows = shows.sort((s1, s2) => (s1.createdAt > s2.createdAt ? -1 : 1));
                    this.source.load(shows);
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    onCustomButton(event: { action: 'view-session' | 'edit-show' | 'get-detail-link'; data: ShowModel }) {
        switch (event.action) {
            case 'view-session': {
                this.router.navigateByUrl('/portal/show/session/create?showId=' + event.data.id);
                break;
            }
            case 'edit-show': {
                this.router.navigateByUrl('/portal/show/update?id=' + event.data.id);
                break;
            }
            case 'get-detail-link': {
                this.copyDetailLink(event.data);
                break;
            }
            default: {
                break;
            }
        }
    }

    /**
     * Retrieve the homepage Show_Detail_Page_Link for the given show from the Detail_Link_Endpoint
     * and copy it to the system clipboard.
     *
     * - Requests the link via `getShowDetailLink$`, bounded by `take(1)` + a 10s `timeout`.
     * - The clipboard write lives strictly inside the success path, so a failed/timed-out request
     *   never touches the clipboard and preserves its contents.
     * - On clipboard write failure, retrieval failure, or timeout, shows an error toast.
     */
    copyDetailLink(show: ShowModel) {
        if (!show?.id) {
            this.toastrService.show('Sự kiện chưa được lưu', 'Lấy link thất bại', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }

        this.loading += 1;
        this.showService
            .getShowDetailLink$(show.id)
            .pipe(
                take(1),
                timeout(10000),
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
                link => {
                    const copied = this.clipboard.copy(link);
                    if (!copied) {
                        this.toastrService.show('Không thể sao chép link vào clipboard', 'Sao chép link thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                        return;
                    }
                    this.toastrService.show(`${show.showName || show.code || ''}`, 'Lấy link thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                () => {
                    this.toastrService.show('Không thể lấy link chi tiết sự kiện, vui lòng thử lại', 'Lấy link thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    sync() {
        this.loading += 1;
        this.showService
            .syncShowData$()
            .pipe(
                tap(
                    e => {
                        this.loading -= 1;
                    },
                    e => {
                        this.loading -= 1;
                    },
                ),
            )
            .subscribe(
                () => {
                    this.toastrService.show('Đồng bộ thành công', 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Đồng bộ thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }
}
