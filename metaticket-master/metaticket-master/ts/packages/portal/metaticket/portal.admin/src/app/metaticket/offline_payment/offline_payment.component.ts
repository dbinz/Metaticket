import { Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { ShowService } from '../../@core/metaticket/data.service';
import { ShowModel, ShowSessionModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { OrderGateway } from '../gateway/order.gateway';
import { LocalDataSource, Settings, IColumnType } from 'angular2-smart-table';
import removeAccents from 'remove-accents';
import { ReplaySubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { DialogConfirmComponent } from '../dialog/dialog.confirm/dialog.confirm.component';

@Component({
    templateUrl: './offline_payment.component.html',
    styleUrls: ['./offline_payment.component.scss'],
    selector: 'offline-payment',
})
export class OfflinePaymentComponent {
    source: LocalDataSource = new LocalDataSource();

    showList: ShowModel[] = [];
    sessionList: ShowSessionModel[] = [];
    filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>();
    filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>();

    loading: number = 0;

    @ViewChild('autoInputShow') inputShow: ElementRef<HTMLInputElement>;
    @ViewChild('autoInputSession') inputSession: ElementRef<HTMLInputElement>;

    settings: Settings = {
        rowClassFunction: row => 'ng2-custom-actions-inline',
        hideSubHeader: true,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
            custom: [
                {
                    name: 'detail',
                    title: '<i class="eva eva-eye-outline" title="Xem chi tiết"></i>',
                },
                {
                    name: 'confirm',
                    title: '<i class="eva eva-done-all-outline" title="Xác nhận đã thanh toán"></i>',
                },
                {
                    name: 'cancel',
                    title: '<i class="eva eva-undo-outline" title="Hủy đơn hàng"></i>',
                },
            ],
            position: 'left',
        },
        columns: {
            code: {
                title: 'Mã đơn hàng',
                type: IColumnType.Text,
            },
            orderStatus: {
                title: 'Trạng thái Order',
                type: IColumnType.Text,
            },
            payStatus: {
                title: 'Trạng thái Thanh toán',
                type: IColumnType.Text,
            },
            price: {
                title: 'Thành tiền',
                type: IColumnType.Text,
                filter: false,
            },
            time: {
                title: 'Thời gian',
                type: IColumnType.Text,
            },
            customerName: {
                title: 'Tên khách hàng',
                type: IColumnType.Text,
            },
            customerPhone: {
                title: 'Số điện thoại',
                type: IColumnType.Text,
            },
            customerEmail: {
                title: 'Email',
                type: IColumnType.Text,
            },
        },
    };

    constructor(
        private router: Router,
        private showService: ShowService,
        private orderGateway: OrderGateway,
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
                    this.showList = shows
                        .filter(s => s.sessions.find(ss => ss.paymentOffline))
                        .sort((s1, s2) => (s1.createdAt > s2.createdAt ? -1 : 1));
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

    onCustomButton(event: { action: 'detail' | 'confirm' | 'cancel'; data: OrderSourceModel }) {
        if (event.action === 'detail') {
            this.router.navigateByUrl('/portal/reservation-order?resId=' + event.data.reservationId);
            return;
        }

        const title = event.action === 'confirm' ? 'Xác nhận đơn hàng đã thanh toán' : 'Xác nhận hủy đơn hàng';
        const content =
            event.action === 'confirm'
                ? `Bạn có chắc chắn muốn xác nhận đơn hàng ${event.data.code} đã được thanh toán`
                : `Bạn có chắc chắn muốn hủy đơn hàng ${event.data.code}`;

        const loading$ = new ReplaySubject<boolean>();
        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: title,
                content: content,
                loading$: loading$,
                onAction$: onAction$,
            },
        });

        onAction$.subscribe(act => {
            if (act === 'confirm') {
                loading$.next(true);
                switch (event.action) {
                    case 'confirm': {
                        this.orderGateway.confirmOfflinePaymentOrder(event.data.id).subscribe(
                            res => {
                                loading$.next(false);
                                if (res.code !== 0) {
                                    this.toastrService.show(
                                        res.message,
                                        `Không thể xác nhận đơn hàng ${event.data.code}`,
                                        {
                                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                            status: 'danger',
                                            duration: 30000,
                                        },
                                    );
                                } else {
                                    this.source.remove(event.data);
                                    this.toastrService.show(
                                        res.message,
                                        `Xác nhận đơn hàng thành công ${event.data.code}`,
                                        {
                                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                            status: 'success',
                                            duration: 3000,
                                        },
                                    );
                                }
                            },
                            error => {
                                this.toastrService.show(
                                    getErrorMessageFromResponse(error),
                                    `Xác nhận đơn hàng thất bại ${event.data.code}`,
                                    { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                                );
                            },
                        );
                        break;
                    }
                    case 'cancel': {
                        this.orderGateway.cancelOfflinePaymentOrder(event.data.id).subscribe(
                            res => {
                                loading$.next(false);
                                if (res.code !== 0) {
                                    this.toastrService.show(res.message, `Không thể hủy đơn hàng ${event.data.code}`, {
                                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                        status: 'danger',
                                        duration: 30000,
                                    });
                                } else {
                                    this.source.remove(event.data);
                                    this.toastrService.show(res.message, `Đã hủy đơn hàng ${event.data.code}`, {
                                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                        status: 'info',
                                        duration: 3000,
                                    });
                                }
                            },
                            error => {
                                this.toastrService.show(
                                    getErrorMessageFromResponse(error),
                                    `Hủy đơn hàng thất bại ${event.data.code}`,
                                    { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                                );
                            },
                        );
                        break;
                    }
                    default: {
                        loading$.next(false);
                        break;
                    }
                }
            }
        });
    }

    private selectedShow: ShowModel;

    onInputShowChange(value: string) {
        this.filteredShowList$.next(this.filterShow(value));
    }

    async onInputSessionChange(value: string) {
        this.filteredSessionList$.next(this.filterSession(value));
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
        this.filteredSessionList$.next(this.sessionList.filter(s => s.id === sessionId));
        const selectedSession = this.sessionList.filter(s => s.id === sessionId)[0];
        this.inputSession.nativeElement.value = this.getSessionName(selectedSession);
        if (!selectedSession) return;
        this.loading += 1;
        this.orderGateway
            .fetchOfflinePaymentOrder(this.selectedShow.id, sessionId)
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
                orders => {
                    this.source.load(
                        orders.map(
                            o =>
                                <OrderSourceModel>{
                                    id: o.orderId,
                                    code: o.orderCode,
                                    orderStatus: o.orderStatus,
                                    reservationId: o.reservationId,
                                    payStatus: o.paymentInfo?.payStatus,
                                    customerName: o.buyerInfo.name,
                                    customerPhone: o.buyerInfo.phone,
                                    customerEmail: o.buyerInfo.email,
                                    eventName: o.orderDetail.eventName,
                                    price: o.orderDetail.total.toLocaleString('it-IT', {
                                        style: 'currency',
                                        currency: 'VND',
                                    }),
                                    time: new Date(o.createdAt).toLocaleString(),
                                },
                        ),
                    );
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Load offline payment thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
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
}

interface OrderSourceModel {
    id: string;
    code: string;
    reservationId: string;
    orderStatus: string;
    payStatus: string;
    customerName: string;
    customerPhone: string;
    customerEmail: string;
    eventName: string;
    time: string;
    price: string;
}
