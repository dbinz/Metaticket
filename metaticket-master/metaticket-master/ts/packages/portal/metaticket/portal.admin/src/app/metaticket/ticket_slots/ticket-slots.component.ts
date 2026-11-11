import { AsyncPipe, NgForOf } from '@angular/common';
import { Component, ElementRef, ViewChild } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
    NbAutocompleteModule,
    NbButtonModule,
    NbCardModule,
    NbDialogService,
    NbGlobalPhysicalPosition,
    NbInputModule,
    NbOptionModule,
    NbSpinnerModule,
    NbToastrService,
} from '@nebular/theme';
import { ShowService } from '../../@core/metaticket/data.service';
import { ShowModel, ShowSessionModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { DialogConfirmComponent } from '../dialog/dialog.confirm/dialog.confirm.component';
import { DialogModule } from '../dialog/dialog.module';
import { UpdateTicketSlotRequest } from '../gateway/request.ticket.slots';
import { TicketSlotGateway } from '../gateway/ticket.slot.gateway';
import { Angular2SmartTableModule, LocalDataSource, Settings, IColumnType } from 'angular2-smart-table';
import removeAccents from 'remove-accents';
import { ReplaySubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';

function equalFilter(cell?: string, search?: string): boolean {
    return !search || cell === search;
}

@Component({
    templateUrl: './ticket-slots.component.html',
    styleUrls: ['./ticket-slots.component.scss'],
    selector: 'ticket-slots',
    standalone: true,
    imports: [
        AsyncPipe,
        DialogModule,
        FormsModule,
        NbAutocompleteModule,
        NbCardModule,
        NbInputModule,
        NbOptionModule,
        Angular2SmartTableModule,
        NgForOf,
        ReactiveFormsModule,
        NbSpinnerModule,
        NbButtonModule,
    ],
})
export class TicketSlotsComponent {
    protected source: LocalDataSource = new LocalDataSource();

    private selectedShow: ShowModel;
    private selectedSession: ShowSessionModel;
    private showList: ShowModel[];
    protected sessionList: ShowSessionModel[] = [];
    protected filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>();
    protected filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>();

    @ViewChild('autoInputShow') private inputShow: ElementRef<HTMLInputElement>;
    @ViewChild('autoInputSession') private inputSession: ElementRef<HTMLInputElement>;

    protected loading: number = 0;
    protected selectedRows: any[] = []; // Store selected ticket slots

    protected settings: Settings = {
        hideSubHeader: false,
        selectMode: 'multi', // Enable multi-row selection with checkboxes
        actions: {
            columnTitle: 'Chọn',
            add: false,
            edit: false,
            delete: false,
            position: 'left',
        },
        columns: {
            orderCode: {
                title: 'Mã đơn hàng',
                type: IColumnType.Text,
                filter: true, // Enable text input filter
            },
            priceCat: {
                title: 'Hạng vé',
                type: IColumnType.Text,
                filter: {
                    type: 'list',
                    config: {
                        selectText: 'All',
                        list: [], // Initialize as empty, will be populated dynamically
                    },
                },
                filterFunction: equalFilter,
            },
            sid: {
                title: 'sid',
                type: IColumnType.Text,
                filter: true, // Enable text input filter
                width: '100px',
            },
            ticketIdx: {
                title: '#Vé',
                type: IColumnType.Text,
                width: '20px',
            },
            slotIdx: {
                title: '#Slot',
                type: IColumnType.Text,
                width: '20px',
            },
            currentStatus: {
                title: 'Trạng thái QR',
                type: IColumnType.Text,
                filter: {
                    type: 'list',
                    config: {
                        selectText: 'All',
                        list: [], // Initialize as empty, will be populated dynamically
                    },
                },
                filterFunction: equalFilter,
            },
            optStatus: {
                title: 'Operator',
                type: IColumnType.Text,
                filter: {
                    type: 'list',
                    config: {
                        selectText: 'All',
                        list: [], // Initialize as empty, will be populated dynamically
                    },
                },
                filterFunction: equalFilter,
            },
        },
    };

    constructor(
        private router: Router,
        private showService: ShowService,
        private ticketSlotService: TicketSlotGateway,
        private toastrService: NbToastrService,
        private dialogService: NbDialogService,
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
                    this.showList = shows.sort((s1, s2) => (s1.createdAt > s2.createdAt ? -1 : 1));
                    this.filteredShowList$.next(this.showList);
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Load show thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    onInputShowChange(value: string) {
        this.filteredShowList$.next(this.filterShow(value));
    }

    async onInputSessionChange(value: string) {
        this.filteredSessionList$.next(this.filterSession(value));
    }

    onRowSelect(event: any): void {
        // Handle both rowSelect and userRowSelect events
        if (event.selected) {
            // This is from rowSelect event (when rows are selected/deselected)
            this.selectedRows = event.selected;
        } else if (event.data) {
            // This is from userRowSelect event (when user clicks checkbox)
            // Toggle selection for the specific row
            const rowData = event.data;
            const existingIndex = this.selectedRows.findIndex(
                row =>
                    row.orderCode === rowData.orderCode &&
                    row.sid === rowData.sid &&
                    row.ticketIdx === rowData.ticketIdx &&
                    row.slotIdx === rowData.slotIdx,
            );

            if (event.isSelected) {
                // Add to selection if not already selected
                if (existingIndex === -1) {
                    this.selectedRows.push(rowData);
                }
            } else {
                // Remove from selection if currently selected
                if (existingIndex > -1) {
                    this.selectedRows.splice(existingIndex, 1);
                }
            }
        }
        console.log('Selected rows:', this.selectedRows);
    }

    onSelectionShowChange($event: string) {
        if (!this.inputShow) return;

        this.filteredShowList$.next(this.showList.filter(s => s.id === $event));
        this.selectedShow = this.showList.filter(s => s.id === $event)[0];
        this.inputShow.nativeElement.value = this.selectedShow?.showName;

        this.inputSession.nativeElement.value = undefined;
        this.source.load([]);
        this.onSelectionSessionChange(undefined);

        this.sessionList = this.selectedShow.sessions;
        this.filteredSessionList$.next(this.sessionList);
        if (this.sessionList.length === 1) {
            this.inputSession.nativeElement.value = this.sessionList[0].id;
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
        this.selectedSession = sessionId ? this.sessionList.find(s => s.id === sessionId) : undefined;

        // Update filtered list for autocomplete dropdown
        this.filteredSessionList$.next(this.selectedSession ? [this.selectedSession] : this.sessionList);
        if (this.inputSession?.nativeElement) {
            // Check if inputSession is available
            this.inputSession.nativeElement.value = this.getSessionName(this.selectedSession);
        }

        if (!this.selectedSession || !this.selectedShow) {
            // Also check selectedShow
            this.source.load([]);
            this.selectedRows = []; // Clear selections when no session selected
            this.updateFilterList([], ['orderCode', 'sid', 'priceCat', 'currentStatus', 'optStatus']);
            return;
        }

        // Clear previous selections when loading new data
        this.selectedRows = [];

        this.loading += 1;
        this.ticketSlotService
            .fetchTicketSlotsByShow(this.selectedShow.id, this.selectedSession.id)
            .pipe(
                tap(
                    () => (this.loading -= 1),
                    () => (this.loading -= 1),
                ),
            ) // Use arrow functions
            .subscribe(
                slots => {
                    this.source.load(slots || []);

                    if (slots && slots.length > 0) {
                        this.updateFilterList(slots, ['priceCat', 'currentStatus', 'optStatus']);
                    } else {
                        this.updateFilterList([], ['priceCat', 'currentStatus', 'optStatus']);
                    }
                },
                error => {
                    this.toastrService.show(
                        error.error?.message || error.message || 'Unknown error',
                        'Load ticket slots thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                    this.source.load([]); // Clear table on error
                    this.updateFilterList([], ['priceCat', 'currentStatus', 'optStatus']);
                },
            );
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

    private updateFilterList(source: any[], columnsToUpdate: string[]) {
        let newColumnsConfig = { ...this.settings.columns }; // Start with a copy of current column settings

        columnsToUpdate.forEach(columnKey => {
            // Iterate over each column key to update
            if (!newColumnsConfig[columnKey] || !newColumnsConfig[columnKey].filter) {
                // If the column doesn't exist or doesn't have a filter config, skip it
                console.warn(`Column ${columnKey} not found or has no filter configuration.`);
                return;
            }

            let list = [];
            if (source && source.length > 0) {
                const uniqueData = [
                    ...new Set(source.map(s => s[columnKey]).filter(value => value != null && value !== '')), // Use 'value' instead of 'pc' for generic term
                ].sort();
                list = uniqueData.map(value => ({ value: value, title: value }));
            }

            // Update the specific column's filter list
            newColumnsConfig = {
                ...newColumnsConfig,
                [columnKey]: {
                    // Use computed property name to target the dynamic column
                    ...newColumnsConfig[columnKey],
                    filter: {
                        ...(newColumnsConfig[columnKey].filter as any),
                        config: {
                            ...(newColumnsConfig[columnKey].filter as any).config,
                            list: list,
                        },
                    },
                },
            };
        });

        // Create a new settings object to trigger change detection in angular2-smart-table
        this.settings = {
            ...this.settings,
            columns: newColumnsConfig,
        };
    }

    public openTicketSlots(): void {
        if (!this.selectedShow || !this.selectedSession) {
            this.toastrService.show('Vui lòng chọn Sự kiện và Session trước khi cập nhật.', 'Thiếu thông tin', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
                duration: 5000,
            });
            return;
        }

        if (!this.selectedRows || this.selectedRows.length === 0) {
            this.toastrService.show('Vui lòng chọn ít nhất một ticket slot để mở.', 'Chưa chọn ticket slot', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
                duration: 5000,
            });
            return;
        }

        let content = '';

        // Chỉ hiển thị message xác nhận nếu có slot hợp lệ
        if (this.selectedRows.length > 0) {
            content = `Bạn có chắc chắn muốn mở ${this.selectedRows.length} ticket slot(s) đã chọn không?`;
        }

        // Hiển thị popup xác nhận
        const loading$ = new ReplaySubject<boolean>();
        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();

        const dialogRef = this.dialogService.open(DialogConfirmComponent, {
            context: {
                title: 'Xác nhận mở slot vé',
                content: content,
                loading$: loading$,
                onAction$: onAction$,
            },
        });

        onAction$.subscribe(action => {
            if (action === 'confirm') {
                this.performOpenTicketSlots(loading$);
            }
        });
    }

    private performOpenTicketSlots(loading$: ReplaySubject<boolean>): void {
        const ticketSlotIds: string[] = this.selectedRows.map(row => row.id);

        if (ticketSlotIds.length === 0) {
            this.toastrService.show(
                'Không có ticket slot nào có trạng thái QR_GENERATED để mở.',
                'Không thể mở slot vé',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'warning', duration: 5000 },
            );
            loading$.next(false);
            return;
        }

        const request: UpdateTicketSlotRequest = {
            ticketSlotIds: ticketSlotIds,
        };

        loading$.next(true);
        this.loading += 1;
        this.ticketSlotService
            .openSelectedTicketSlots(request)
            .pipe(
                tap(
                    () => {
                        this.loading -= 1;
                        loading$.next(false);
                    },
                    () => {
                        this.loading -= 1;
                        loading$.next(false);
                    },
                ),
            )
            .subscribe({
                next: () => {
                    this.toastrService.success('Mở slot vé thành công', 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                    // Clear selections and refresh data
                    this.selectedRows = [];
                    this.onSelectionSessionChange(this.selectedSession.id);
                },
                error: error => {
                    this.toastrService.show(
                        error.error?.message || error.message || 'Unknown error',
                        'Mở slot vé thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
            });
    }

    public closeTicketSlots(): void {
        if (!this.selectedShow || !this.selectedSession) {
            this.toastrService.show('Vui lòng chọn Sự kiện và Session trước khi cập nhật.', 'Thiếu thông tin', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
                duration: 5000,
            });
            return;
        }

        if (!this.selectedRows || this.selectedRows.length === 0) {
            this.toastrService.show('Vui lòng chọn ít nhất một ticket slot để đóng.', 'Chưa chọn ticket slot', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
                duration: 5000,
            });
            return;
        }

        // Hiển thị popup xác nhận
        const loading$ = new ReplaySubject<boolean>();
        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();

        const dialogRef = this.dialogService.open(DialogConfirmComponent, {
            context: {
                title: 'Xác nhận đóng slot vé',
                content: `Bạn có chắc chắn muốn đóng ${this.selectedRows.length} ticket slot(s) đã chọn không?`,
                loading$: loading$,
                onAction$: onAction$,
            },
        });

        onAction$.subscribe(action => {
            if (action === 'confirm') {
                this.performCloseTicketSlots(loading$);
            }
        });
    }

    private performCloseTicketSlots(loading$: ReplaySubject<boolean>): void {
        const ticketSlotIds: string[] = this.selectedRows.map(row => row.id);

        const request: UpdateTicketSlotRequest = {
            ticketSlotIds: ticketSlotIds,
        };

        loading$.next(true);
        this.loading += 1;
        this.ticketSlotService
            .closeSelectedTicketSlots(request)
            .pipe(
                tap(
                    () => {
                        this.loading -= 1;
                        loading$.next(false);
                    },
                    () => {
                        this.loading -= 1;
                        loading$.next(false);
                    },
                ),
            )
            .subscribe({
                next: () => {
                    this.toastrService.success('Đóng slot vé thành công', 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                    // Clear selections and refresh data
                    this.selectedRows = [];
                    this.onSelectionSessionChange(this.selectedSession.id);
                },
                error: error => {
                    this.toastrService.show(
                        error.error?.message || error.message || 'Unknown error',
                        'Đóng slot vé thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
            });
    }
}
