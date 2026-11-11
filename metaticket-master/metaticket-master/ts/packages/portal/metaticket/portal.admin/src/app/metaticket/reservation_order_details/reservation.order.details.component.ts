import { NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
    NbButtonModule,
    NbCardModule,
    NbCheckboxModule,
    NbDialogService,
    NbGlobalPhysicalPosition,
    NbIconModule,
    NbInputModule,
    NbSpinnerModule,
    NbToastrService,
} from '@nebular/theme';
import { OrderSummaryModel, TicketReservationDetailModel, TicketReservationModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { DialogConfirmComponent } from '../dialog/dialog.confirm/dialog.confirm.component';
import { OrderGateway } from '../gateway/order.gateway';
import { TicketGateway } from '../gateway/ticket.gateway';
import { LocalDataSource, Angular2SmartTableModule, Settings, IColumnType } from 'angular2-smart-table';
import { ReplaySubject } from 'rxjs';
import { mergeMap, tap } from 'rxjs/operators';

@Component({
    templateUrl: './reservation.order.details.component.html',
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
export class ReservationOrderDetailsComponent {
    source: LocalDataSource = new LocalDataSource([]);

    settings: Settings = {
        rowClassFunction: row => 'ng2-custom-actions-inline',
        hideSubHeader: false,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
        },
        columns: {
            index: {
                title: 'STT',
                type: IColumnType.Text,
                filter: false,
                valuePrepareFunction: (value, row, cell) => {
                    const pager = this.source.getPaging();
                    const page = pager.page; // Trang hiện tại
                    const perPage = pager.perPage; // Số dòng mỗi trang

                    // Công thức: (Trang - 1) * Số dòng + Chỉ mục hàng hiện tại + 1
                    return (page - 1) * perPage + (cell as any).row.index + 1;
                },
            },
            sid: {
                title: 'sid',
                type: IColumnType.Text,
            },
            priceCat: {
                title: 'Hạng vé',
                type: IColumnType.Text,
            },
            quantity: {
                title: 'Số lượng',
                type: IColumnType.Text,
                filter: false,
            },
        },
    };

    loading: number = 0;
    reservation: TicketReservationModel;
    order: OrderSummaryModel;
    private reservationId: string;

    constructor(
        activatedRoute: ActivatedRoute,
        private ticketGateway: TicketGateway,
        private orderGateway: OrderGateway,
        private toastrService: NbToastrService,
        private dialogService: NbDialogService,
    ) {
        this.reservationId = activatedRoute.snapshot.queryParams['resId'];
        this.refresh();
    }

    private refresh() {
        this.loading += 1;
        this.ticketGateway
            .fetchReservationById(this.reservationId)
            .pipe(
                mergeMap((reservation: TicketReservationModel) => {
                    this.reservation = reservation;
                    this.loadData(reservation.details);
                    return this.orderGateway.fetchOrderByReservation(
                        this.reservation.showId,
                        this.reservation.sessionId,
                        [this.reservation.id],
                    );
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
                    this.order = orders[0];
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

    private loadData(tickets: TicketReservationDetailModel[]) {
        this.source.load(
            tickets.map((ticket, index) => {
                return <Object>{
                    comp: this,
                    sid: ticket.sid,
                    priceCat: ticket.priceCat,
                    quantity: ticket.quantity,
                };
            }),
        );
    }

    get currentDate(): Date {
        return new Date();
    }

    dateTimeLocal(value: number): string {
        return new Date(value).toLocaleString();
    }

    async unlockReservation() {
        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: 'Xác nhận hủy khóa vé',
                content: 'Bạn có chắc chắn muốn hủy khóa vé hay không?',
                onAction$: onAction$,
            },
        });

        onAction$.subscribe(action => {
            switch (action) {
                case 'confirm': {
                    this.loading += 1;
                    this.ticketGateway
                        .unlockReservation(this.reservation.id)
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
                            _ => {
                                this.toastrService.show('Hủy khóa vé thành công!', `Thành công`, {
                                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                    status: 'success',
                                    duration: 3000,
                                });
                                this.refresh();
                            },
                            error => {
                                this.toastrService.show(getErrorMessageFromResponse(error), `Thất bại`, {
                                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                    status: 'danger',
                                    duration: 30000,
                                });
                            },
                        );
                    break;
                }
                default:
                    break;
            }
        });
    }

    async cancelOrder() {
        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: 'Xác nhận hủy đơn hàng',
                content: 'Bạn có chắc chắn muốn hủy đơn hàng hay không?',
                onAction$: onAction$,
            },
        });
        onAction$.subscribe(action => {
            switch (action) {
                case 'confirm': {
                    this.loading += 1;
                    const service = this.order.paymentOffline
                        ? this.orderGateway.cancelOfflinePaymentOrder(this.order.orderId)
                        : this.orderGateway.cancelOrder(this.order.orderId);

                    service
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
                            _ => {
                                this.toastrService.show('Hủy đơn hàng thành công!', `Thành công`, {
                                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                    status: 'success',
                                    duration: 3000,
                                });
                                this.refresh();
                            },
                            error => {
                                this.toastrService.show(getErrorMessageFromResponse(error), `Thất bại`, {
                                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                    status: 'danger',
                                    duration: 30000,
                                });
                            },
                        );
                    break;
                }
                default:
                    break;
            }
        });
    }
}
