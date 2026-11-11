import { NgIf } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
    NbButtonModule,
    NbCardModule,
    NbCheckboxModule,
    NbGlobalPhysicalPosition,
    NbIconModule,
    NbInputModule,
    NbSpinnerModule,
    NbToastrService,
} from '@nebular/theme';
import { OrderSummaryModel, TicketModel, TicketReservationModel } from '../../@core/metaticket/model';
import { formatDateTimeVN } from '../../@core/metaticket/utils/datetime.utils';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { OrderGateway } from '../gateway/order.gateway';
import { TicketGateway } from '../gateway/ticket.gateway';
import { Angular2SmartTableModule, IColumnType, LocalDataSource, Settings, ViewCell } from 'angular2-smart-table';
import { of } from 'rxjs';
import { mergeMap, tap } from 'rxjs/operators';

@Component({
    templateUrl: './reservation.of.ticket.component.html',
    standalone: true,
    imports: [
        NbCardModule,
        ReactiveFormsModule,
        NbCheckboxModule,
        NbInputModule,
        NbButtonModule,
        NbSpinnerModule,
        NgIf,
        NbIconModule,
        Angular2SmartTableModule,
    ],
})
export class ReservationOfTicketComponent {
    source: LocalDataSource = new LocalDataSource();
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
                onComponentInitFunction(instance: { action: { subscribe: (arg0: (event: any) => void) => void } }) {
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
            reservationStatus: {
                title: 'Trạng thái Reservation',
                type: IColumnType.Text,
            },
            orderCode: {
                title: 'Order',
                type: IColumnType.Text,
            },
            orderStatus: {
                title: 'Trạng thái Order',
                type: IColumnType.Text,
            },
            paymentStatus: {
                title: 'Trạng thái Thanh toán',
                type: IColumnType.Text,
            },
            date: {
                title: 'Ngày tạo',
                type: IColumnType.Text,
                isSortable: true,
            },
        },
    };

    loading: number = 0;
    ticket: TicketModel | undefined;
    private reservations: TicketReservationModel[] = [];

    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private orderGateway: OrderGateway,
        private ticketGateway: TicketGateway,
        private toastrService: NbToastrService,
    ) {
        const tkId = activatedRoute.snapshot.queryParams['tkId'];
        this.loading += 1;
        this.ticketGateway
            .fetchTicketById(tkId)
            .pipe(
                mergeMap((tk: TicketModel, idx) => {
                    this.ticket = tk;
                    return ticketGateway.fetchReservationByTicket(tkId, [
                        'Created',
                        'Reserving',
                        'Reserved',
                        'Locked',
                        'Unlocked',
                        'Removing',
                        'OrderPaying',
                        'OrderSuccessful',
                        'OrderCancelled',
                        'OrderFailed',
                    ]);
                }),
            )
            .pipe(
                mergeMap((reservations: TicketReservationModel[], idx) => {
                    this.reservations = reservations;
                    const showIds = [...new Set<string>(reservations.map(r => r.showId))];
                    const sessionIds = [...new Set<string>(reservations.map(r => r.sessionId))];
                    if (showIds.length !== 1 || sessionIds.length !== 1) {
                        console.log('there more then one show-session');
                        return of([]);
                    }
                    const reservationIds = reservations.map(r => r.id);
                    return orderGateway.fetchOrderByReservation(showIds[0], sessionIds[0], reservationIds);
                }),
            )
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
                (orders: OrderSummaryModel[]) => {
                    this.loadData(this.reservations, orders);
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), `Không thể load data`, {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    private loadData(reservations: TicketReservationModel[], orders: OrderSummaryModel[]) {
        this.source.load(
            reservations.map((reservation, index) => {
                const order = orders.find(o => o.reservationId === reservation.id);
                return <Object>{
                    comp: this,
                    reservationId: reservation.id,
                    reservationStatus: reservation.status,
                    orderCode: order?.orderCode,
                    orderStatus: order?.orderStatus,
                    paymentStatus: order?.paymentInfo?.payStatus,
                    date: formatDateTimeVN(order?.createdAt || reservation.createdAt),
                };
            }),
        );
    }

    onCustomButton(event: { action: 'detail'; data: any }) {
        switch (event.action) {
            case 'detail': {
                this.router.navigateByUrl('/portal/reservation-order?resId=' + event.data.reservationId);
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
                (click)="onAction('detail')"
                class="eva eva-eye-outline mr-2"
                title="Detail"
                [style.cursor]="'pointer'"></i>
        </div>
    `,
    standalone: true,
    imports: [],
})
export class ButtonViewComponent implements ViewCell {
    @Input() value!: string | number;
    @Input() rowData: any;

    @Output() action: EventEmitter<{
        action: 'detail';
        data: any;
    }> = new EventEmitter();

    onAction(action: 'detail') {
        this.action.emit({ action: action, data: this.rowData });
    }
}
