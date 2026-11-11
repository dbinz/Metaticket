import { Clipboard } from '@angular/cdk/clipboard';
import { AsyncPipe, DatePipe, NgForOf, NgIf } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import {
    NbAutocompleteModule,
    NbButtonModule,
    NbCardModule,
    NbDialogService,
    NbGlobalPhysicalPosition,
    NbInputModule,
    NbSpinnerModule,
    NbToastrService,
} from '@nebular/theme';
import { AdminService, ShowService } from '../../@core/metaticket/data.service';
import { ReportFilterResponse, ShowModel, ShowSessionModel } from '../../@core/metaticket/model';
import { formatDateTimeVN } from '../../@core/metaticket/utils/datetime.utils';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { DialogConfirmComponent } from '../dialog/dialog.confirm/dialog.confirm.component';
import { Angular2SmartTableModule, IColumnType, LocalDataSource, ViewCell, Settings } from 'angular2-smart-table';
import removeAccents from 'remove-accents';
import { BehaviorSubject, ReplaySubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';

@Component({
    selector: 'report-filter',
    templateUrl: 'report.filter.component.html',
    providers: [],
    standalone: true,
    imports: [
        NgIf,
        NbButtonModule,
        NbCardModule,
        Angular2SmartTableModule,
        NbSpinnerModule,
        NbAutocompleteModule,
        AsyncPipe,
        NbInputModule,
        NgForOf,
    ],
})
export class ReportFilterComponent implements OnInit {
    source: LocalDataSource = new LocalDataSource();
    loading: number = 0;
    private data$: BehaviorSubject<ReportFilterResponse[]> = new BehaviorSubject([]);

    shows: Map<string, { showName: string; sessions: Map<string, string> }> = new Map();
    showList: ShowModel[];
    sessionList: ShowSessionModel[] = [];
    filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>();
    filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>();

    private selectedShow: ShowModel;
    private selectedSession: ShowSessionModel;

    @ViewChild('autoInputShow') inputShow;
    @ViewChild('autoInputSession') inputSession;

    constructor(
        private adminService: AdminService,
        private showService: ShowService,
        private toastService: NbToastrService,
        private dialogService: NbDialogService,
        private clipboard: Clipboard,
        private datePipe: DatePipe,
    ) {}

    ngOnInit(): void {
        this.loading += 1;
        this.showService
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
                    shows.forEach(show => {
                        const currShow = {
                            showName: show.showName || show.code,
                            sessions: new Map(),
                        };
                        this.shows.set(show.id, currShow);
                        show.sessions.forEach(session => {
                            currShow.sessions.set(session.id, session.sessionName || session.code);
                        });
                    });
                    this.showList = shows.sort((s1, s2) => (s1.createdAt > s2.createdAt ? -1 : 1));
                    this.filteredShowList$.next(this.showList);
                },
                error => {
                    this.toastService.show(getErrorMessageFromResponse(error), `Không thể tải danh sách show`, {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
        this.data$.subscribe(data => {
            this.loadData(data);
        });
    }

    loadReportFilter() {
        this.loading += 1;
        this.adminService
            .loadReportFilterList$({
                showId: this.selectedShow?.id,
                sessionId: this.selectedSession?.id,
            })
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
                (rps: ReportFilterResponse[]) => {
                    this.data$.next(rps);
                },
                error => {
                    this.toastService.show(
                        getErrorMessageFromResponse(error),
                        `Không thể tải danh sách Report Filter`,
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
            );
    }

    private loadData(rps: ReportFilterResponse[]) {
        this.source.load([]).then(() => {
            this.source.load(
                rps.map((r, index) => {
                    const s: any = structuredClone(r);
                    s.comp = this;
                    s.filter = JSON.stringify(s.filter, (key, value) => {
                        if (key === 'startDate' || key === 'endDate')
                            return value ? formatDateTimeVN(value) : undefined;
                        return value || undefined;
                    });
                    return s;
                }),
            );
        });
    }

    clear() {
        this.inputShow.nativeElement.value = '';
        this.inputSession.nativeElement.value = '';
        this.selectedShow = undefined;
        this.selectedSession = undefined;
        this.sessionList = [];
        this.source = new LocalDataSource();
    }

    clearSession() {
        this.selectedSession = undefined;
        this.source = new LocalDataSource();
    }

    onInputShowChange(value: string) {
        this.filteredShowList$.next(this.filterShow(value));
    }

    onInputSessionChange(value: string) {
        this.filteredSessionList$.next(this.filterSession(value));
    }

    onSelectionShowChange($event: string) {
        if (!this.inputShow) return;

        this.clearSession();

        this.filteredShowList$.next(this.showList.filter(s => s.id === $event));
        this.selectedShow = this.showList.filter(s => s.id === $event)[0];
        this.inputShow.nativeElement.value = this.selectedShow?.showName;

        this.inputSession.value = undefined;
        this.onSelectionSessionChange(undefined);

        this.sessionList = this.selectedShow.sessions;
        this.filteredSessionList$.next(this.sessionList);
        if (this.sessionList.length === 1) {
            this.inputSession.value = this.sessionList[0].id;
            this.onSelectionSessionChange(this.sessionList[0].id);
        }
    }

    getSessionName(session: ShowSessionModel): string {
        let name: string = '';
        if (session) {
            if (session.code?.trim()?.length > 0 && session.sessionName)
                name = `${session.code} - ${session.sessionName}`;
            else if (session.code?.trim()?.length > 0 && !session.sessionName) name = session.code;
            else if (session.sessionName && session.code?.trim()?.length <= 0) name = session.sessionName;
        }
        return name;
    }

    onSelectionSessionChange(sessionId: string) {
        if (!this.inputSession) return;

        this.filteredSessionList$.next(this.sessionList.filter(s => s.id === sessionId));
        this.selectedSession = this.sessionList.filter(s => s.id === sessionId)[0];
        this.inputSession.nativeElement.value = this.getSessionName(this.selectedSession);
        if (!this.selectedSession) return;
    }

    private filterShow(input: string): ShowModel[] {
        const filterValue = removeAccents(input.toLowerCase());
        return this.showList.filter(
            optionValue =>
                !input || input === '' || removeAccents(optionValue.showName.toLowerCase()).indexOf(filterValue) > -1,
        );
    }

    private filterSession(input: string): ShowSessionModel[] {
        const filterValue = removeAccents(input.toLowerCase());
        return this.sessionList.filter(
            optionValue =>
                !input ||
                input === '' ||
                removeAccents(optionValue.sessionName?.toLowerCase() || '').indexOf(filterValue) > -1 ||
                removeAccents(optionValue.code?.toLowerCase() || '').indexOf(filterValue) > -1,
        );
    }

    settings: Settings = {
        hideSubHeader: false,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
        },
        columns: {
            button: {
                filter: false,
                isSortable: false,
                title: 'Actions',
                type: IColumnType.Custom,
                renderComponent: ButtonViewComponent,
                onComponentInitFunction(instance) {
                    instance.action.subscribe(event => {
                        console.log(event);
                        event.data.comp.onCustomButton(event);
                    });
                },
            },
            index: {
                title: 'STT',
                type: IColumnType.Text,
                filter: false,
                valuePrepareFunction: (value, row, cell) => {
                    const pager = this.source.getPaging();
                    const page = pager.page; // Trang hiện tại
                    const perPage = pager.perPage; // Số dòng mỗi trang

                    // Công thức: (Trang - 1) * Số dòng + Chỉ mục hàng hiện tại + 1
                    return (page - 1) * perPage + cell.getRow().index + 1;
                },
            },
            reportName: {
                title: 'Tên Report',
                type: IColumnType.Text,
            },
            username: {
                title: 'Người Tạo',
                type: IColumnType.Text,
            },
            status: {
                title: 'Trạng Thái',
                type: IColumnType.Text,
            },
            createdAt: {
                title: 'Ngày Tạo',
                type: IColumnType.Text,
                valuePrepareFunction: date => {
                    if (date) {
                        // Định dạng dd/MM/yyyy cho ngày tháng Việt Nam
                        return this.datePipe.transform(date, 'dd/MM/yyyy HH:mm');
                    }
                    return '';
                },
            },
            filter: {
                title: 'Filter',
                type: IColumnType.Text,
            },
        },
    };

    onCustomButton(event: { action: 'disable' | 'enable' | 'remove' | 'link' | 'open'; data: ReportFilterResponse }) {
        switch (event.action) {
            case 'open': {
                const link = `${location.protocol}//${location.hostname}/public/report/${event.data.reportKey}`;
                window.open(link, '_blank');
                break;
            }
            case 'link': {
                const link = `${location.protocol}//${location.hostname}/public/report/${event.data.reportKey}`;
                this.clipboard.copy(link);
                break;
            }
            case 'disable': {
                this.loading += 1;
                this.adminService
                    .disableReportFilter$(event.data.id)
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
                        rp => {
                            const tmp = this.data$.value.map(e => {
                                return e.id === rp.id ? rp : e;
                            });
                            this.data$.next(tmp);
                            this.toastService.show(
                                `Disable report filter ${event.data.reportName} thành công`,
                                'Thành công',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                            );
                        },
                        err => {
                            console.error('Cannot disable report filter', err);
                            this.toastService.show(
                                `Disable report filter ${event.data.reportName} thất bại`,
                                'Thất bại',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                            );
                        },
                    );
                break;
            }
            case 'enable': {
                this.loading += 1;
                this.adminService
                    .enableReportFilter$(event.data.id)
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
                        rp => {
                            const tmp = this.data$.value.map(e => {
                                return e.id === rp.id ? rp : e;
                            });
                            this.source.getElements().then(e => {
                                if (e.id === rp.id) {
                                    this.source.update(e, rp);
                                }
                            });
                            this.data$.next(tmp);
                            this.toastService.show(
                                `Enable report filter ${event.data.reportName} thành công`,
                                'Thành công',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                            );
                        },
                        err => {
                            console.error('Cannot enable report filter', err);
                            this.toastService.show(
                                `Enable report filter ${event.data.reportName} thất bại`,
                                'Thất bại',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                            );
                        },
                    );
                break;
            }
            case 'remove': {
                const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
                this.dialogService.open(DialogConfirmComponent, {
                    closeOnBackdropClick: false,
                    context: {
                        title: 'Xác nhận xóa report filter',
                        content: `Bạn có chắc chắn muốn xóa report filter <strong>${event.data.reportName}</strong>?`,
                        onAction$: onAction$,
                    },
                });
                onAction$.subscribe(act => {
                    if (act === 'confirm') {
                        this.loading += 1;
                        this.adminService
                            .deleteReportFilter$(event.data.id)
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
                                rp => {
                                    this.data$.next(this.data$.value.filter(e => e.id !== rp));
                                    this.toastService.show(
                                        `Xóa report filter ${event.data.reportName} thành công`,
                                        'Thành công',
                                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                                    );
                                },
                                err => {
                                    console.error('Cannot remove report filter', err);
                                    this.toastService.show(
                                        `Xóa report filter ${event.data.reportName} thất bại`,
                                        'Thất bại',
                                        {
                                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                            status: 'danger',
                                            duration: 30000,
                                        },
                                    );
                                },
                            );
                    }
                });
                break;
            }
            default: {
                break;
            }
        }
    }
}

@Component({
    selector: 'button-view',
    template: `
        <div class="d-flex justify-content-center" style="font-size: 20px">
            <i
                *ngIf="rowData.status === 'Inactive'"
                (click)="onAction('enable')"
                class="eva eva-eye-outline mr-2"
                title="Enable"
                [style.cursor]="'pointer'"></i>
            <i
                *ngIf="rowData.status === 'Active'"
                (click)="onAction('disable')"
                class="eva eva-eye-off-outline mr-2"
                title="Disable"
                [style.cursor]="'pointer'"></i>
            <i
                *ngIf="rowData.status === 'Active'"
                (click)="onAction('link')"
                class="eva eva-link-outline mr-2"
                title="Get Link"
                [style.cursor]="'pointer'"></i>
            <i
                *ngIf="rowData.status === 'Active'"
                (click)="onAction('open')"
                class="eva eva-external-link-outline mr-2"
                title="Open"
                [style.cursor]="'pointer'"></i>
            <i
                (click)="onAction('remove')"
                class="eva eva-trash-2-outline"
                title="Remove"
                [style.cursor]="'pointer'"></i>
        </div>
    `,
    standalone: true,
    imports: [NgIf],
})
class ButtonViewComponent implements ViewCell {
    @Input() value: string | number;
    @Input() rowData: ReportFilterResponse;

    @Output() action: EventEmitter<{ action: 'disable' | 'enable' | 'remove' | 'link' | 'open'; data: any }> =
        new EventEmitter();

    onAction(action: 'disable' | 'enable' | 'remove' | 'link' | 'open') {
        this.action.emit({ action: action, data: this.rowData });
    }
}
