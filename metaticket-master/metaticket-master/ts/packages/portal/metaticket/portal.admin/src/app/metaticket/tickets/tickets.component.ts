import { AsyncPipe, NgForOf, NgIf } from '@angular/common';
import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
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
import { ShowService } from '../../@core/metaticket/data.service';
import { ShowModel, ShowSessionModel, TicketModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { DialogInputStringComponent } from '../dialog/dialog.input.string/dialog.input.string.component';
import { UpdateTicketSlotRequest } from '../gateway/request.ticket';
import { TicketGateway } from '../gateway/ticket.gateway';
import { displayYmd, isCalendarSession } from '../ticket_calendar/calendar.utils';
import { Angular2SmartTableModule, IColumnType, LocalDataSource, Settings, ViewCell } from 'angular2-smart-table';
import removeAccents from 'remove-accents';
import { ReplaySubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './tickets.component.html',
    styleUrls: ['./tickets.component.scss'],
    selector: 'tickets',
    standalone: true,
    imports: [
        NbCardModule,
        NbSpinnerModule,
        NbAutocompleteModule,
        NgForOf,
        Angular2SmartTableModule,
        AsyncPipe,
        NbInputModule,
        NbButtonModule,
    ],
})
export class TicketsComponent {
    source: LocalDataSource = new LocalDataSource();

    showList: ShowModel[] = [];
    sessionList: ShowSessionModel[] = [];
    filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>();
    filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>();

    @ViewChild('autoInputShow') inputShow!: ElementRef<HTMLInputElement>;
    @ViewChild('autoInputSession') inputSession!: ElementRef<HTMLInputElement>;

    loading: number = 0;
    selectedRows: any[] = [];

    settings: Settings = {
        hideSubHeader: false,
        selectMode: 'multi',
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
            position: 'left',
        },
        columns: {
            button: {
                filter: false,
                isSortable: false,
                title: 'Actions',
                type: IColumnType.Custom,
                renderComponent: ButtonViewComponent,
                onComponentInitFunction(instance: { action: { subscribe: (arg0: (event: any) => void) => void } }) {
                    instance.action.subscribe(event => {
                        event.data.comp.onCustomButton(event);
                    });
                },
            },
            priceCat: {
                title: 'Hạng vé',
                type: IColumnType.Text,
            },
            sid: {
                title: 'sid',
                type: IColumnType.Text,
            },
            dname: {
                title: 'Tên',
                type: IColumnType.Text,
            },
            ticketSlots: {
                title: 'Slot/Vé',
                type: IColumnType.Text,
                filter: false,
            },
            resCap: {
                title: 'Số vé đã giữ',
                type: IColumnType.Text,
                filter: false,
            },
            remCap: {
                title: 'Số vé còn lại',
                type: IColumnType.Text,
                filter: false,
            },
            totCap: {
                title: 'Tổng số vé',
                type: IColumnType.Text,
                filter: false,
            },
        },
    };

    private readonly baseSettings: Settings = this.settings;

    private selectedShow?: ShowModel;
    private selectedSession?: ShowSessionModel;

    constructor(
        private router: Router,
        private showService: ShowService,
        private ticketGateway: TicketGateway,
        private dialogService: NbDialogService,
        private toastrService: NbToastrService,
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
        if (event.selected) {
            this.selectedRows = event.selected;
        } else if (event.data) {
            const rowData = event.data;
            const existingIndex = this.selectedRows.findIndex(row => row.id === rowData.id);
            if (event.isSelected) {
                if (existingIndex === -1) this.selectedRows.push(rowData);
            } else {
                if (existingIndex > -1) this.selectedRows.splice(existingIndex, 1);
            }
        }
    }

    onSelectionShowChange($event: string) {
        if (!this.inputShow) return;

        this.filteredShowList$.next(this.showList.filter(s => s.id === $event));
        this.selectedShow = this.showList.filter(s => s.id === $event)[0];
        this.inputShow.nativeElement.value = this.selectedShow?.showName;

        this.inputSession.nativeElement.value = '';
        this.source.load([]);
        this.onSelectionSessionChange(undefined);

        this.sessionList = this.selectedShow.sessions;
        this.filteredSessionList$.next(this.sessionList);
        if (this.sessionList.length === 1) {
            this.inputSession.nativeElement.value = this.sessionList[0].id;
            this.onSelectionSessionChange(this.sessionList[0].id);
        }
    }

    getSessionName(session?: ShowSessionModel): string {
        let name: string = '';
        if (session) {
            if (session.code.trim().length > 0 && session.sessionName)
                name = `${session.code} - ${session.sessionName}`;
            else if (session.code?.trim()?.length > 0 && !session.sessionName) name = session.code;
            else if (session.sessionName && session.code?.trim()?.length <= 0) name = session.sessionName;
        }
        return name;
    }

    onSelectionSessionChange(sessionId ?: string) {
        this.selectedSession = sessionId ? this.sessionList.find(s => s.id === sessionId) : undefined;

        this.filteredSessionList$.next(this.selectedSession ? [this.selectedSession] : this.sessionList);
        if (this.inputSession?.nativeElement) {
            this.inputSession.nativeElement.value = this.getSessionName(this.selectedSession);
        }

        if (!this.selectedSession || !this.selectedShow) {
            this.source.load([]);
            this.selectedRows = [];
            return;
        }

        this.selectedRows = [];
        const calendar = isCalendarSession(this.selectedSession);
        this.settings = calendar ? this.withVisitDateColumn() : this.baseSettings;

        this.loading += 1;
        this.ticketGateway
            .fetchAllTicket(this.selectedShow.id, this.selectedSession.id)
            .pipe(
                tap(
                    e => (this.loading -= 1),
                    e => (this.loading -= 1),
                ),
            )
            .subscribe(
                tickets => {
                    tickets.forEach(t => {
                        (t as any).comp = this;
                        // dd/MM/yyyy text so the column filter matches what the admin types ("20/10").
                        (t as any).visitDate = displayYmd(t.calendarDate);
                    });
                    if (calendar) {
                        // One row per (date, category): group by date so the same sid of different dates sit apart.
                        tickets.sort(
                            (a, b) =>
                                (a.calendarDate || '').localeCompare(b.calendarDate || '') ||
                                a.priceCat.localeCompare(b.priceCat),
                        );
                    }
                    this.source.load(tickets);
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Load tickets thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    updateTicketSlot() {
        if (!this.selectedShow || !this.selectedSession) {
            this.toastrService.show('Vui lòng chọn Sự kiện và Session', 'Thiếu thông tin', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
                duration: 5000,
            });
            return;
        }

        if (this.selectedRows.length === 0) {
            this.toastrService.show('Vui lòng chọn vé cần cập nhật', 'Chưa chọn vé', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
                duration: 5000,
            });
            return;
        }

        const dialogRef = this.dialogService.open(DialogInputStringComponent, {
            context: {
                title: `Cập nhật slot cho ${this.selectedRows.length} vé đã chọn`,
                dataType: 'number',
                inputData: '1',
                validateFn: (value: string | number) => {
                    const num = Number(value);
                    return !isNaN(num) && Number.isInteger(num) && num > 0;
                },
            },
        });

        dialogRef.onClose.subscribe((slotInput: string) => {
            if (slotInput) {
                const slot = Number(slotInput);
                const request: UpdateTicketSlotRequest = {
                    showId: this.selectedShow!.id,
                    sessionId: this.selectedSession!.id,
                    ticketIds: this.selectedRows.map(r => r.id),
                    slot: slot,
                };

                this.loading++;
                this.ticketGateway.updateTicketSlot(request).subscribe(
                    () => {
                        this.loading--;
                        this.toastrService.show('Cập nhật slot thành công', 'Thành công', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'success',
                            duration: 5000,
                        });
                        // Update local data to reflect changes
                        this.selectedRows.forEach(row => {
                            row.ticketSlots = slot;
                        });
                        this.source.refresh();
                        this.selectedRows = []; // Clear selection after update
                    },
                    error => {
                        this.loading--;
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Cập nhật thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 5000,
                        });
                    },
                );
            }
        });
    }

    /** Base columns plus "Ngày tham quan" right after the actions; a new object so the table re-renders. */
    private withVisitDateColumn(): Settings {
        const { button, ...rest } = this.baseSettings.columns;
        return {
            ...this.baseSettings,
            columns: {
                button,
                visitDate: { title: 'Ngày tham quan', type: IColumnType.Text },
                ...rest,
            },
        };
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

    onCustomButton(event: { action: 'lock' | 'unlock'; data: TicketModel | any }) {
        switch (event.action) {
            case 'unlock': {
                this.router.navigateByUrl('/portal/ticket/reservations?tkId=' + event.data.id);
                break;
            }
            case 'lock': {
                const name = event.data.dname || event.data.sid;
                // Calendar tickets share the sid across dates; the date tells the admin which one is being locked.
                const ticketName = event.data.visitDate ? `${name} ngày ${event.data.visitDate}` : name;
                const title = `Bạn có chắc chắn muốn khóa vé ${ticketName}`;

                const onAction$ = this.dialogService.open(DialogInputStringComponent, {
                    context: {
                        title: title,
                        dataType: 'number',
                        inputData: '1',
                        validateFn: (input: string | number) => {
                            const num = Number(input);
                            return Number.isInteger(num) && num > 0 && num <= event.data.remCap;
                        },
                    },
                });

                onAction$.onClose.subscribe(quantityStr => {
                    if (quantityStr === undefined) return;
                    const quantity = Number(quantityStr);
                    if (isNaN(quantity) || quantity <= 0) return;
                    if (event.data.remCap === 0 || event.data.resCap === event.data.totCap) {
                        this.toastrService.show(
                            `Không thể khóa vé ${ticketName}, số lượng vé không đủ`,
                            `Không thể khóa vé ${ticketName}`,
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success', duration: 3000 },
                        );
                        return;
                    }

                    if (quantity > event.data.remCap) {
                        this.toastrService.show(
                            `Không thể khóa vé ${ticketName}, số lượng vé còn lại không đủ (${event.data.remCap})`,
                            `Không thể khóa vé ${ticketName}`,
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 3000 },
                        );
                        return;
                    }

                    event.data.loading = true;
                    this.ticketGateway
                        .forceReserveTicket(event.data.showId, event.data.sessionId, [
                            {
                                ticketId: event.data.id,
                                sid: event.data.sid,
                                priceCat: event.data.priceCat,
                                quantity: quantity,
                            },
                        ])
                        .subscribe(
                            res => {
                                event.data.remCap -= quantity;
                                event.data.resCap += quantity;
                                this.source.refresh();
                                event.data.loading = false;
                                this.toastrService.show(
                                    `Đã khóa ${quantity} vé ${ticketName}`,
                                    `Khóa vé thành công ${ticketName}`,
                                    { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success', duration: 3000 },
                                );
                            },
                            error => {
                                event.data.loading = false;
                                this.toastrService.show(
                                    getErrorMessageFromResponse(error),
                                    `Không thể khóa vé ${ticketName}`,
                                    { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                                );
                            },
                        );
                });
            }
        }
    }
}

@Component({
    selector: 'button-view',
    template: `
        <div class="d-flex justify-content-center" style="font-size: 20px">
            <i
                *ngIf="rowData.remCap > 0"
                [nbSpinner]="rowData.loading"
                (click)="onAction('lock')"
                class="eva eva-lock-outline mr-2"
                title="Khóa vé"
                [style.cursor]="'pointer'"></i>
            <i
                *ngIf="rowData.resCap > 0"
                [nbSpinner]="rowData.loading"
                (click)="onAction('unlock')"
                class="eva eva-undo-outline"
                title="Mở khóa vé"
                [style.cursor]="'pointer'"></i>
        </div>
    `,
    standalone: true,
    imports: [NgIf, NbSpinnerModule],
})
class ButtonViewComponent implements ViewCell {
    @Input() value!: string | number;
    @Input() rowData: any;

    @Output() action: EventEmitter<{ action: 'lock' | 'unlock'; data: any }> = new EventEmitter();

    onAction(action: 'lock' | 'unlock') {
        this.action.emit({ action: action, data: this.rowData });
    }
}
