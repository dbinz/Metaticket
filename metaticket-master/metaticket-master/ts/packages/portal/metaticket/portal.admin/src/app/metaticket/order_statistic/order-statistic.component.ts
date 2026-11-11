import { Component, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, FormControl } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
    NbDialogService,
    NbGlobalPhysicalPosition,
    NbRangepickerComponent,
    NbToastrService,
    NbTreeGridDataSource,
    NbTreeGridDataSourceBuilder,
} from '@nebular/theme';
import {
    AdminService,
    OrderService,
    PaymentService,
    ShowService,
    UserService,
} from '../../@core/metaticket/data.service';
import {
    CreateReportFilterRequest,
    InvoiceStatisticsByShow,
    OrderResponse,
    OrderStatus,
    PaymentResponse,
    ShowModel,
    ShowSessionModel,
} from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { DialogInputStringComponent } from '../dialog/dialog.input.string/dialog.input.string.component';
import { toYmd } from '../ticket_calendar/calendar.utils';
import * as ExcelJS from 'exceljs';
import { saveAs } from 'file-saver';
import removeAccents from 'remove-accents';
import { ReplaySubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './order-statistic.component.html',
    selector: 'order-statistic',
})
export class OrderStatisticComponent {
    headers = [
        'Suất diễn',
        'Loại vé',
        'Giá',
        'Vé đã bán',
        'Doanh số',
        'Khuyến mãi',
        'Hoa hồng',
        'Doanh thu',
        'Tải xuống',
    ];
    defaultColumns = ['cat', 'basePrice', 'totalTicket', 'totalPrice', 'discount', 'commission', 'revenue', 'download'];
    shortColumn = 'show';
    allColumns = [this.shortColumn, ...this.defaultColumns];
    dataSource: NbTreeGridDataSource<FSEntry>;

    rangepickerCtrl = this.formBuilder.group({
        rangepicker: new FormControl(undefined),
        visitDateRange: new FormControl(undefined),
    });

    summary = {
        totalOpenShow: 0,
        totalTicket: 0,
        totalPrice: 0,
        totalRevenue: 0,
    };

    shows: Map<string, { showName: string; sessions: Map<string, string> }> = new Map();

    showList: ShowModel[];
    sessionList: ShowSessionModel[] = [];
    filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>();
    filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>();

    loading: number = 0;
    startDate: any;
    endDate: any;
    /** Lọc đơn vé theo lịch theo ngày tham quan ('YYYY-MM-DD'), độc lập với lọc theo ngày đặt đơn. */
    visitDateFrom?: string;
    visitDateTo?: string;

    private selectedShow: ShowModel;
    private selectedSession: ShowSessionModel;

    private queryOrderStatus?: OrderStatus[];

    @ViewChild('autoInputShow') inputShow: ElementRef<HTMLInputElement>;
    @ViewChild('autoInputSession') inputSession: ElementRef<HTMLInputElement>;

    constructor(
        private route: ActivatedRoute,
        private formBuilder: FormBuilder,
        private dataSourceBuilder: NbTreeGridDataSourceBuilder<FSEntry>,
        private orderService: OrderService,
        private paymentService: PaymentService,
        private showService: ShowService,
        private adminService: AdminService,
        private toastService: NbToastrService,
        private userService: UserService,
        private dialogService: NbDialogService,
    ) {
        this.queryOrderStatus = this.route.snapshot.queryParams['orderStatus']?.split(',')?.map(s => OrderStatus[s]);
        console.log(this.queryOrderStatus);
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
    }

    onDateSelected(picker: NbRangepickerComponent<any>) {
        this.startDate = picker?.value?.start ? Date.parse(picker.value.start) : undefined;
        this.endDate = picker?.value?.end ? Date.parse(picker.value.end) + 24 * 60 * 60000 : undefined;
    }

    /** Chọn mới ngày đầu (chưa có ngày cuối) thì tạm hiểu là lọc đúng một ngày. */
    onVisitDateSelected(picker: NbRangepickerComponent<Date>) {
        const start = picker?.value?.start;
        const end = picker?.value?.end;
        this.visitDateFrom = start ? toYmd(start) : undefined;
        this.visitDateTo = end ? toYmd(end) : this.visitDateFrom;
    }

    private resetVisitDate() {
        this.visitDateRangeFormCtr.reset();
        this.visitDateFrom = undefined;
        this.visitDateTo = undefined;
    }

    async saveFilter() {
        const dialogRef = this.dialogService.open(DialogInputStringComponent, {
            context: {
                title: 'Nhập tên filter muốn lưu',
            },
        });

        const reportName = await dialogRef.onClose.toPromise();

        if (reportName === undefined) return;

        if (reportName?.length === 0) {
            this.toastService.show('Tên Report Filter không để trống', `Không thể lưu Report Filter`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }

        this.userService.getUser$().subscribe(user => {
            const req: CreateReportFilterRequest = {
                username: user.username,
                reportName: reportName,
                filters: {
                    showId: this.selectedShow?.id || undefined,
                    sessionId: this.selectedSession?.id || undefined,
                    startDate: this.startDate || undefined,
                    endDate: this.endDate || undefined,
                    status: this.queryOrderStatus || [OrderStatus.PaymentSucceed],
                    visitDateFrom: this.visitDateFrom,
                    visitDateTo: this.visitDateTo,
                },
            };
            this.loading += 1;
            this.adminService
                .createReportFilter$(req)
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
                    d => {
                        this.toastService.show(d, `Report Filter đã được lưu`, {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'success',
                            duration: 30000,
                        });
                    },
                    error => {
                        console.log('cannot save report filter', error);
                        this.toastService.show(getErrorMessageFromResponse(error), `Không thể lưu Report Filter`, {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        });
    }

    loadStatistic() {
        this.loading += 1;
        this.orderService
            .loadStatistic({
                showId: this.selectedShow?.id || undefined,
                sessionId: this.selectedSession?.id || undefined,
                startDate: this.startDate,
                endDate: this.endDate,
                status: this.queryOrderStatus || [OrderStatus.PaymentSucceed],
                visitDateFrom: this.visitDateFrom,
                visitDateTo: this.visitDateTo,
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
                d => {
                    this.fillForm(d);
                },
                error => {
                    this.toastService.show(getErrorMessageFromResponse(error), `Không thể tải bảng thống kê`, {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                    this.clear();
                },
            );
    }

    clear() {
        this.inputShow.nativeElement.value = '';
        this.inputSession.nativeElement.value = '';
        this.selectedShow = undefined;
        this.selectedSession = undefined;
        this.sessionList = [];
        this.rangepickerFormCtr.reset();
        this.startDate = undefined;
        this.endDate = undefined;
        this.resetVisitDate();
        this.dataSource = undefined;
        this.summary.totalOpenShow = 0;
        this.summary.totalTicket = 0;
        this.summary.totalPrice = 0;
        this.summary.totalRevenue = 0;
    }

    clearDateTime() {
        this.rangepickerFormCtr.reset();
        this.startDate = undefined;
        this.endDate = undefined;
        this.resetVisitDate();
        this.dataSource = undefined;
        this.summary.totalOpenShow = 0;
        this.summary.totalTicket = 0;
        this.summary.totalPrice = 0;
        this.summary.totalRevenue = 0;
    }

    clearSession() {
        this.selectedSession = undefined;
        this.rangepickerFormCtr.reset();
        this.startDate = undefined;
        this.endDate = undefined;
        this.resetVisitDate();
        this.dataSource = undefined;
        this.summary.totalOpenShow = 0;
        this.summary.totalTicket = 0;
        this.summary.totalPrice = 0;
        this.summary.totalRevenue = 0;
    }

    get rangepickerFormCtr() {
        return this.rangepickerCtrl.controls.rangepicker;
    }

    get visitDateRangeFormCtr() {
        return this.rangepickerCtrl.controls.visitDateRange;
    }

    private fillForm(source: InvoiceStatisticsByShow[]) {
        this.summary.totalOpenShow = 0;
        this.summary.totalTicket = 0;
        this.summary.totalPrice = 0;
        this.summary.totalRevenue = 0;
        this.dataSource = undefined;

        const data: TreeNode<FSEntry>[] = source.map(show => {
            const currShow = this.showList.filter(s => s.id === show.showId)[0];
            if (currShow.status === 'Open') this.summary.totalOpenShow += 1;
            this.summary.totalTicket += show.totalTicket;
            this.summary.totalPrice += show.totalPrice;
            this.summary.totalRevenue += show.revenue;

            return <TreeNode<FSEntry>>{
                data: {
                    level: 1,
                    showId: show.showId,
                    show: this.shows.get(show.showId).showName,
                    cat: '-',
                    basePrice: '-',
                    totalTicket: show.totalTicket,
                    totalPrice: show.totalPrice.toLocaleString('vn-VN'),
                    discount: show.discount.toLocaleString('vn-VN'),
                    commission: '-',
                    revenue: show.revenue.toLocaleString('vn-VN'),
                },
                children: show.bySession.map(session => {
                    const currSession = currShow.sessions.filter(s => s.id === session.sessionId)[0];
                    return <TreeNode<FSEntry>>{
                        data: {
                            level: 2,
                            showId: show.showId,
                            sessionId: session.sessionId,
                            show: this.shows.get(show.showId).sessions.get(session.sessionId),
                            cat: '-',
                            basePrice: '-',
                            totalTicket: session.totalTicket,
                            totalPrice: session.totalPrice.toLocaleString('vn-VN'),
                            discount: session.discount.toLocaleString('vn-VN'),
                            commission: session.commission ? session.commission + '%' : '-',
                            revenue: session.revenue.toLocaleString('vn-VN'),
                            download: currShow.code + currSession.code,
                        },
                        children: session.byCategory.map(
                            cat =>
                                <TreeNode<FSEntry>>{
                                    data: {
                                        level: 3,
                                        cat: cat.cat,
                                        basePrice: cat.basePrice.toLocaleString('vn-VN'),
                                        totalTicket: cat.totalTicket,
                                        totalPrice: cat.totalPrice.toLocaleString('vn-VN'),
                                        discount: '-',
                                        commission: '-',
                                        revenue: '-',
                                    },
                                },
                        ),
                    };
                }),
            };
        });
        if (data.length > 0) this.dataSource = this.dataSourceBuilder.create(data);
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

        this.inputSession.nativeElement.value = undefined;
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
        if (!this.inputSession) return;

        this.clearDateTime();

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

    loadAndExportReport(data: FSEntry) {
        this.loading += 1;
        this.orderService
            .loadReport({
                showId: data.showId,
                sessionId: data.sessionId,
                startDate: this.startDate,
                endDate: this.endDate,
                status: this.queryOrderStatus || [OrderStatus.PaymentSucceed],
                visitDateFrom: this.visitDateFrom,
                visitDateTo: this.visitDateTo,
            })
            .subscribe(
                res => {
                    this.exportOrderReport(res, data.download).finally(() => (this.loading -= 1));
                },
                _ => {
                    this.loading -= 1;
                    this.toastService.show('Vui lòng tải lại bảng thống kê', `Không thể tải bảng thống kê`, {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                    this.clear();
                },
            );
    }

    private async exportOrderReport(orders: OrderResponse[], fileName: string) {
        const workbook = new ExcelJS.Workbook();
        const worksheet = workbook.addWorksheet(fileName);

        const hasDelivery = orders.findIndex(o => o.delivery) > -1;
        const hasPromotion = orders.findIndex(o => o.promotionDetails?.length > 0) > -1;
        const hasCustomFields = orders.findIndex(o => o.customFields?.length > 0) > -1;

        const headers = [
            'STT',
            'Mã',
            'Trạng thái đơn hàng',
            'Mã Giao Dịch',
            'Gateway',
            'Tài Khoản Thanh Toán',
            'Trạng thái thanh toán',
            'Tên Khách Hàng',
            'Số Điện Thoại',
            'Mail',
            'Thời Gian',
        ];
        if (hasDelivery)
            headers.push(...['Tên Người Nhận', 'SDT Người Nhận', 'Thành Phố', 'Quận/Huyện', 'Đường/Thôn', 'Địa Chỉ']);
        if (hasCustomFields) headers.push(...['Thông Tin Bổ Sung', 'Giá Trị Thông Tin Bổ Sung']);
        headers.push(...['Hạng Vé', 'Vé', 'Số Lượng Vé', 'Giá Vé']);
        if (hasPromotion) headers.push(...['Loại Khuyến Mãi', 'Khuyến Mãi']);
        headers.push(...['Thành Tiền']);

        const table: any[][] = [];
        table.push(headers);

        worksheet.addRow(headers);

        const paymentIds = orders.filter(o => o.paymentId).map(o => o.paymentId);
        const payments = await this.paymentService.getPayments(paymentIds).toPromise();

        for (let i = 0; i < orders.length; i++) {
            const order = orders[i];
            const payment = payments.filter(p => p.id === order.paymentId)[0];
            // this.createOrderRow(table, i + 1, order, payment,, hasDelivery, hasPromotion, hasCustomFields);
            this.createOrderRowObj(worksheet, i + 1, order, payment, hasDelivery, hasPromotion, hasCustomFields);
        }

        // Generate Excel file
        workbook.xlsx.writeBuffer().then((buffer: any) => {
            const blob = new Blob([buffer], {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            });
            saveAs(blob, `${fileName}.xlsx`);
        });
    }

    private createOrderRowObj(
        worksheet: ExcelJS.Worksheet,
        orderIdx: number,
        order: OrderResponse,
        payment: PaymentResponse,
        hasDelivery: boolean,
        hasPromotion: boolean,
        hasCustomFields: boolean,
    ): any[] {
        const wrapCellIdx = [];

        const row = [];

        let cellIdx = 0;
        row[cellIdx++] = orderIdx; // STT
        row[cellIdx++] = order.code; // Code
        row[cellIdx++] = order.status; // order status
        row[cellIdx++] = payment?.paymentNo || ''; // payment no
        row[cellIdx++] = payment?.gateway || ''; // payment gateway
        row[cellIdx++] = payment?.pgAccountCode || ''; // payment account code
        row[cellIdx++] = payment?.status || ''; // payment status
        row[cellIdx++] = order.customerName; // customerName
        row[cellIdx++] = order.customerPhone; // customerPhone
        row[cellIdx++] = order.customerEmail; // customerMail
        row[cellIdx++] = new Date(order.createdAt).toLocaleString('vi-vn'); // customerPhone

        if (hasDelivery) {
            row[cellIdx++] = order?.delivery?.deliveryName; // deliveryName
            row[cellIdx++] = order?.delivery?.deliveryPhone; // deliveryPhone
            row[cellIdx++] = order?.delivery?.provinceName; // provinceName
            row[cellIdx++] = order?.delivery?.districtName; // districtName
            row[cellIdx++] = order?.delivery?.wardName; // wardName
            row[cellIdx++] = order?.delivery?.address; // address
        }

        if (hasCustomFields) {
            const nameIdx = cellIdx++;
            const valueIdx = cellIdx++;
            row[nameIdx] = '';
            row[valueIdx] = '';
            if (order.customFields?.length > 0) {
                for (let i = 0; i < order.customFields.length; i++) {
                    if (i === 0) {
                        row[nameIdx] += order.customFields[i].name;
                        row[valueIdx] += order.customFields[i].value;
                    }
                    if (i > 0 && i <= order.customFields.length - 1) {
                        row[nameIdx] += '\r\n' + order.customFields[i].name;
                        row[valueIdx] += '\r\n' + order.customFields[i].value;
                        wrapCellIdx.push(nameIdx, valueIdx);
                    }
                }
            }
        }

        const categoryIdx = cellIdx++;
        const ticketIdx = cellIdx++;
        const quantityIdx = cellIdx++;
        const priceIdx = cellIdx++;
        row[categoryIdx] = '';
        row[ticketIdx] = '';
        row[quantityIdx] = '';
        row[priceIdx] = '';
        for (let i = 0; i < order.tickets.length; i++) {
            if (i === 0) {
                row[categoryIdx] += order.tickets[i].priceCat;
                row[ticketIdx] += order.tickets[i].dname || order.tickets[i].sid;
                row[quantityIdx] += order.tickets[i].quantity;
                row[priceIdx] += order.tickets[i].basePrice;
            }
            if (i > 0 && i <= order.tickets.length - 1) {
                row[categoryIdx] += '\r\n' + order.tickets[i].priceCat;
                row[ticketIdx] += '\r\n' + (order.tickets[i].dname || order.tickets[i].sid);
                row[quantityIdx] += '\r\n' + order.tickets[i].quantity;
                row[priceIdx] += '\r\n' + order.tickets[i].basePrice;
                wrapCellIdx.push(categoryIdx, ticketIdx, quantityIdx, priceIdx);
            }
        }

        if (hasPromotion) {
            const nameIdx = cellIdx++;
            const valueIdx = cellIdx++;
            row[nameIdx] = '';
            row[valueIdx] = '';
            if (order.promotionDetails?.length > 0) {
                for (let i = 0; i < order.promotionDetails.length; i++) {
                    if (i === 0) {
                        row[nameIdx] += order.promotionDetails[i].name;
                        row[valueIdx] += order.promotionDetails[i].priceDiscount;
                    }
                    if (i > 0 && i <= order.promotionDetails.length - 1) {
                        row[nameIdx] += '\r\n' + order.promotionDetails[i].name;
                        row[valueIdx] += '\r\n' + order.promotionDetails[i].priceDiscount;
                        wrapCellIdx.push(nameIdx, valueIdx);
                    }
                }
            }
        }

        row[cellIdx] = order.total;
        const r = worksheet.addRow(row);
        wrapCellIdx.forEach(idx => {
            r.getCell(idx).alignment = { wrapText: true, shrinkToFit: true };
            r.getCell(idx).value += '';
        });

        return [];
    }
}

interface TreeNode<T> {
    data: T;
    children?: TreeNode<T>[];
    expanded?: boolean;
}

interface FSEntry {
    level: number;
    showId?: string;
    sessionId?: string;
    show?: string;
    cat?: string;
    basePrice?: number | string;
    totalTicket?: number;
    totalPrice?: number | string;
    discount?: number | string;
    commission?: number | string;
    revenue?: number | string;
    download?: string;
}
