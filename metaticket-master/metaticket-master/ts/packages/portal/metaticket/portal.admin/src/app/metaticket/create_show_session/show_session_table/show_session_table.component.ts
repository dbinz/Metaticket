import { DatePipe, NgIf } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NbActionsModule, NbButtonModule } from '@nebular/theme';
import { SessionActionType } from '../model';
import { TicketGateway } from '../../gateway/ticket.gateway';
import { LocalDataSource, ViewCell, IColumnType, Settings } from 'angular2-smart-table';
import { BehaviorSubject } from 'rxjs';
import { ShowSessionModel } from '../../../@core/metaticket/model';

/** Ngày của session lịch là ngày theo giờ VN (00:00 ngày đầu – 23:59 ngày cuối). */
const VN_TIMEZONE = '+0700';

@Component({
    selector: 'show_session-table',
    templateUrl: './show_session_table.component.html',
    styleUrls: ['./show_session_table.component.css'],
})
export class ShowSessionTableComponent implements OnInit {
    @Input() showId!: string;
    @Input() sessions!: BehaviorSubject<ShowSessionModel[]>;

    @Output() actionEmitter: EventEmitter<{ action: SessionActionType; data: ShowSessionModel }> = new EventEmitter();

    source: LocalDataSource = new LocalDataSource();
    loading: number = 0;

    constructor(
        private ticketGateway: TicketGateway,
        private datePipe: DatePipe,
    ) {}

    async ngOnInit() {
        this.loading += 1;
        const countsTicket = await this.ticketGateway.countTicketByShow(this.showId).toPromise();
        this.loading -= 1;

        this.sessions.subscribe(sessions => {
            this.source.load(
                sessions.map((session, index) => {
                    const sourceModel: SessionSourceModel = structuredClone(session) as SessionSourceModel;
                    sourceModel.comp = this;
                    sourceModel.noTicket = countsTicket?.find(s => s.sessionId === session.id)?.noTicket || 0;
                    return sourceModel;
                }),
            );
        });
    }

    settings: Settings = {
        hideSubHeader: true,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
        },
        columns: {
            code: {
                title: 'Mã',
                type: IColumnType.Text,
            },
            sessionName: {
                title: 'Tên',
                type: IColumnType.Text,
            },
            status: {
                title: 'Trạng Thái',
                type: IColumnType.Text,
            },
            venue: {
                title: 'Địa Điểm',
                type: IColumnType.Text,
            },
            startTime: {
                title: 'Thời Gian',
                type: IColumnType.Html,
                valuePrepareFunction: (date, row: SessionSourceModel) => {
                    // Nội dung chỉ gồm ngày do DatePipe sinh ra (không có dữ liệu người dùng) nên render HTML an toàn.
                    if (row?.sessionType === 'CALENDAR') {
                        const from = this.datePipe.transform(row.startTime, 'dd/MM/yyyy', VN_TIMEZONE);
                        const to = this.datePipe.transform(row.endTime, 'dd/MM/yyyy', VN_TIMEZONE);
                        return `<span class="calendar-badge">Vé theo lịch</span><br />${from} – ${to}`;
                    }
                    if (date) {
                        // Định dạng dd/MM/yyyy cho ngày tháng Việt Nam
                        return this.datePipe.transform(date, 'dd/MM/yyyy HH:mm');
                    }
                    return '';
                },
            },
            noTicket: {
                title: 'Số lượng vé',
                type: IColumnType.Text,
            },
            button: {
                filter: false,
                isSortable: false,
                title: 'Actions',
                type: IColumnType.Custom,
                renderComponent: ButtonViewComponent,
                onComponentInitFunction(instance: {
                    action: {
                        subscribe: (
                            arg0: (event: { action: SessionActionType; data: SessionSourceModel }) => void,
                        ) => void;
                    };
                }) {
                    instance.action.subscribe((event: { action: SessionActionType; data: SessionSourceModel }) => {
                        console.log(event);
                        event.data.comp.actionEmitter.emit(event);
                    });
                },
            },
        },
    };
}

interface SessionSourceModel extends ShowSessionModel {
    comp: ShowSessionTableComponent;
    noTicket: number;
    startTimeString: string;
}

@Component({
    selector: 'button-view',
    template: `
        <button
            nbButton
            size="small"
            outline
            status="primary"
            (click)="onAction('market-link')"
            class="p-1 mr-1"
            title="Lấy link trang bán vé"
            [style.cursor]="'pointer'">
            Link Bán Vé
        </button>
        <button
            nbButton
            size="small"
            outline
            status="primary"
            (click)="onAction('scanner-link')"
            class="p-1 mr-1"
            title="Lấy link scanner"
            [style.cursor]="'pointer'">
            Link Scanner
        </button>
        <button
            nbButton
            size="small"
            outline
            status="info"
            (click)="onAction('edit')"
            class="p-1 mr-1"
            title="Chỉnh sửa"
            [style.cursor]="'pointer'">
            Chỉnh sửa
        </button>
        <a
            nbButton
            size="small"
            outline
            status="info"
            *ngIf="rowData.sessionType === 'CALENDAR'"
            [routerLink]="['/portal/ticket/calendar']"
            [queryParams]="{ showId: rowData.comp.showId, sessionId: rowData.id }"
            class="p-1 mr-1"
            title="Giá, số lượng và trạng thái bán theo từng ngày">
            Lịch Bán Vé
        </a>
        <button
            nbButton
            size="small"
            outline
            status="success"
            *ngIf="['Created'].includes(rowData.status)"
            (click)="onAction('request-approve')"
            class="p-1 mr-1"
            title="Yêu cầu duyệt"
            [style.cursor]="'pointer'">
            Yêu Cầu Duyệt
        </button>
        <button
            nbButton
            size="small"
            outline
            status="danger"
            *ngIf="['ApprovalWait'].includes(rowData.status)"
            (click)="onAction('reject')"
            class="p-1 mr-1"
            title="Từ chối"
            [style.cursor]="'pointer'">
            Từ Chối
        </button>
        <button
            nbButton
            size="small"
            outline
            status="danger"
            *ngIf="['ApprovalWait'].includes(rowData.status)"
            (click)="onAction('approve')"
            class="p-1 mr-1"
            title="Chấp Nhận"
            [style.cursor]="'pointer'">
            Chấp Nhận
        </button>
        <button
            nbButton
            size="small"
            outline
            status="success"
            *ngIf="['OpeningWait'].includes(rowData.status)"
            (click)="onAction('open')"
            class="p-1 mr-1"
            title="Mở Bán"
            [style.cursor]="'pointer'">
            Mở Bán
        </button>
        <button
            nbButton
            size="small"
            outline
            status="success"
            *ngIf="['Paused'].includes(rowData.status)"
            (click)="onAction('open')"
            class="p-1 mr-1"
            title="Mở Bán"
            [style.cursor]="'pointer'">
            Mở Bán Lại
        </button>
        <button
            nbButton
            size="small"
            outline
            status="warning"
            *ngIf="['Open'].includes(rowData.status)"
            (click)="onAction('pause')"
            class="p-1 mr-1"
            title="Tạm dừng"
            [style.cursor]="'pointer'">
            Tạm Dừng
        </button>
        <button
            nbButton
            size="small"
            outline
            status="danger"
            *ngIf="['Open'].includes(rowData.status)"
            (click)="onAction('stop')"
            class="p-1 mr-1"
            title="Dừng"
            [style.cursor]="'pointer'">
            Dừng
        </button>
        <button
            nbButton
            size="small"
            outline
            status="danger"
            *ngIf="['Rejected', 'Stopped'].includes(rowData.status)"
            (click)="onAction('terminate')"
            class="p-1 mr-1"
            title="Chấm dứt"
            [style.cursor]="'pointer'">
            Kết Thúc
        </button>
    `,
    standalone: true,
    imports: [NgIf, NbActionsModule, NbButtonModule, RouterLink],
})
class ButtonViewComponent implements ViewCell {
    @Input() value!: string | number;
    @Input() rowData!: SessionSourceModel;

    @Output() action: EventEmitter<{ action: SessionActionType; data: any }> = new EventEmitter();

    onAction(action: SessionActionType) {
        this.action.emit({ action: action, data: this.rowData });
    }
}
