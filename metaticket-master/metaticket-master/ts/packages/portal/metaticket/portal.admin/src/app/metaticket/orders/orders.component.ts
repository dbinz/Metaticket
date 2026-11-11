import { LocationStrategy, NgIf } from '@angular/common';
import { Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { ShowService } from '../../@core/metaticket/data.service';
import { OrderSummaryModel, ShowModel, ShowSessionModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { OrderGateway } from '../gateway/order.gateway';
import { QRCodeGateway } from '../gateway/qrcode.gateway';
import { LocalDataSource, ViewCell, Settings, IColumnType, Row } from 'angular2-smart-table';
import removeAccents from 'remove-accents';
import { combineLatest, Observable, ReplaySubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { DisableEnableQRCodeResponse, FetchOrderQRCodeResponse } from '../../qrcode/model';

@Component({
    templateUrl: './orders.component.html',
    styleUrls: ['./orders.component.scss'],
    selector: 'orders',
})
export class OrdersComponent {
    sourceData: OrderSourceModel[] = [];
    source: LocalDataSource = new LocalDataSource();

    showList: ShowModel[] = [];
    sessionList: ShowSessionModel[] = [];
    filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>();
    filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>();

    loading: number = 0;

    private selectedShow?: ShowModel;

    @ViewChild('autoInputShow') inputShow!: ElementRef<HTMLInputElement>;
    @ViewChild('autoInputSession') inputSession!: ElementRef<HTMLInputElement>;

    settings: Settings = {
        rowClassFunction: (row: Row) => 'ng2-custom-actions-inline',
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
        private showService: ShowService,
        private orderGateway: OrderGateway,
        private qrcodeGateway: QRCodeGateway,
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
                    this.toastrService.show(getErrorMessageFromResponse(error), `Không thể load show`, {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    onCustomButton(event: { action: 'notify' | 'disable-qr' | 'enable-qr'; data: OrderSourceModel }) {
        switch (event.action) {
            case 'notify': {
                this.loading += 1;
                this.orderGateway
                    .resendNotification([event.data.id])
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
                        (res: Map<string, boolean>) => {
                            if (!res.get(event.data.id)) {
                                this.toastrService.show('', `Không thể thông báo lại ${event.data.code}`, {
                                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                    status: 'danger',
                                    duration: 300000,
                                });
                            } else {
                                this.toastrService.show('', `Thông báo lại thành công ${event.data.code}`, {
                                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                    status: 'success',
                                    duration: 3000,
                                });

                                this.loading += 1;
                                this.qrcodeGateway
                                    .fetchOrderQRCode(event.data.showId, event.data.sessionId, [event.data.id])
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
                                    .subscribe((qrcodesResp: FetchOrderQRCodeResponse) => {
                                        const order = this.sourceData.filter(data => data.id === event.data.id)[0];
                                        if (qrcodesResp.enabled.includes(order.id)) {
                                            order.qrcodeDisabled = false;
                                            this.source.load(this.sourceData);
                                        } else if (qrcodesResp.disabled.includes(order.id)) {
                                            order.qrcodeDisabled = true;
                                            this.source.load(this.sourceData);
                                        }
                                    });
                            }
                        },
                        error => {
                            this.toastrService.show(
                                getErrorMessageFromResponse(error),
                                `Không thể thông báo lại ${event.data.code}`,
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 300000 },
                            );
                        },
                    );
                break;
            }
            case 'disable-qr': {
                this.loading += 1;
                this.qrcodeGateway
                    .disableOrderQRCode(event.data.id)
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
                        (res: DisableEnableQRCodeResponse) => {
                            const order = this.sourceData.filter(data => data.id === event.data.id)[0];
                            order.qrcodeDisabled = true;
                            this.source.load(this.sourceData);
                            this.toastrService.show(
                                `Khóa ${res.countQRCode} QR đơn hàng thành công ${event.data.code}`,
                                `Khóa QR đơn hàng thành công ${event.data.code}`,
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success', duration: 300000 },
                            );
                        },
                        error => {
                            this.toastrService.show(
                                getErrorMessageFromResponse(error),
                                `Không thể khóa QR đơn hàng ${event.data.code}`,
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'warning', duration: 300000 },
                            );
                        },
                    );
                break;
            }
            case 'enable-qr': {
                this.loading += 1;
                this.qrcodeGateway
                    .enableOrderQRCode(event.data.id)
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
                        (res: DisableEnableQRCodeResponse) => {
                            const order = this.sourceData.filter(data => data.id === event.data.id)[0];
                            order.qrcodeDisabled = false;
                            this.source.load(this.sourceData);
                            this.toastrService.show(
                                `Mở khóa ${res.countQRCode} QR đơn hàng thành công ${event.data.code}`,
                                `Mở khóa QR đơn hàng thành công ${event.data.code}`,
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success', duration: 300000 },
                            );
                        },
                        error => {
                            this.toastrService.show(
                                getErrorMessageFromResponse(error),
                                `Không thể mở khóa QR đơn hàng ${event.data.code}`,
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 300000 },
                            );
                        },
                    );
                break;
            }
            default: {
                break;
            }
        }
    }

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

        this.inputSession.nativeElement.value = '';
        this.sourceData = [];
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

    async onSelectionSessionChange(sessionId?: string) {
        this.filteredSessionList$.next(this.sessionList.filter(s => s.id === sessionId));
        const selectedSession = this.sessionList.filter(s => s.id === sessionId)[0];
        this.inputSession.nativeElement.value = this.getSessionName(selectedSession);
        if (!selectedSession) return;

        this.loading += 1;
        const qrcodesResp: Observable<FetchOrderQRCodeResponse> = this.qrcodeGateway
            .fetchOrderQRCode(this.selectedShow!.id, sessionId)
            .pipe(
                tap(
                    e => {
                        this.loading -= 1;
                    },
                    e => {
                        this.loading -= 1;
                    },
                ),
            );

        this.loading += 1;
        const ordersResp: Observable<OrderSummaryModel[]> = this.orderGateway
            .fetchOrders(this.selectedShow!.id, sessionId)
            .pipe(
                tap(
                    e => {
                        this.loading -= 1;
                    },
                    e => {
                        this.loading -= 1;
                    },
                ),
            );

        combineLatest([qrcodesResp, ordersResp]).subscribe(
            resp => {
                this.sourceData = resp[1].map(o => {
                    let disabledQRCode: boolean = false;
                    if (resp[0].disabled.includes(o.orderId)) disabledQRCode = true;
                    if (resp[0].enabled.includes(o.orderId)) disabledQRCode = false;
                    return <OrderSourceModel>{
                        comp: this,
                        showId: this.selectedShow!.id,
                        sessionId: sessionId,
                        id: o.orderId,
                        code: o.orderCode,
                        qrcodeDisabled: disabledQRCode,
                        orderStatus: o.orderStatus,
                        payStatus: o.paymentInfo?.payStatus,
                        customerName: o.buyerInfo?.name,
                        customerPhone: o.buyerInfo?.phone,
                        customerEmail: o.buyerInfo?.email,
                        eventName: o.orderDetail.eventName,
                        price: o.orderDetail.total.toLocaleString('it-IT', { style: 'currency', currency: 'VND' }),
                        time: new Date(o.createdAt).toLocaleString(),
                    };
                });
                this.source.load(this.sourceData);
            },
            error => {
                this.toastrService.show(getErrorMessageFromResponse(error), 'Load order thất bại', {
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
    orderStatus: string;
    payStatus: string;
    customerName: string;
    customerPhone: string;
    customerEmail: string;
    eventName: string;
    time: string;
    price: string;
    qrcodeDisabled?: boolean;
    showId: string;
    sessionId: string;
}

@Component({
    selector: 'button-view',
    template: `
        <div class="d-flex justify-content-center align-items-center" style="font-size: 20px">
            <i
                (click)="onAction('notify')"
                class="eva eva-email-outline mr-2"
                title="Gửi lại mail"
                [style.cursor]="'pointer'"></i>
            <i
                *ngIf="rowData.qrcodeDisabled === true"
                (click)="onAction('enable-qr')"
                class="icon"
                title="Enable QR Code"
                [style.cursor]="'pointer'"
                ><img src="/assets/images/qr-code_disabled.png" alt="disable qr icon"
            /></i>
            <i
                *ngIf="rowData.qrcodeDisabled === false"
                (click)="onAction('disable-qr')"
                class="icon"
                title="Disable QR Code"
                [style.cursor]="'pointer'"
                ><img src="/assets/images/qr-code-scan.svg" alt="enable qr icon"
            /></i>
        </div>
    `,
    styleUrls: ['./orders.component.scss'],
    standalone: true,
    imports: [NgIf],
})
class ButtonViewComponent implements ViewCell {
    constructor(public locationStrategy: LocationStrategy) {}

    @Input() value!: string | number;
    @Input() rowData: any;

    @Output() action: EventEmitter<{ action: 'notify' | 'disable-qr' | 'enable-qr'; data: any }> = new EventEmitter();

    onAction(action: 'notify' | 'disable-qr' | 'enable-qr') {
        this.action.emit({ action: action, data: this.rowData });
    }
}
