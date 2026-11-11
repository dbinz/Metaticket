import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { ShowService } from '../../@core/metaticket/data.service';
import {
    BulkUpdateCalendarResponse,
    CalendarAdminDay,
    CalendarAdminTicket,
    CalendarDayStatus,
    ShowModel,
    ShowSessionModel,
} from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { BulkUpdateCalendarRequest } from '../gateway/request.ticket';
import { TicketGateway } from '../gateway/ticket.gateway';
import { CalendarBulkDialogComponent, CalendarBulkMode } from './calendar-bulk-dialog.component';
import { CalendarCategoryOption, CalendarGenerateDialogComponent } from './calendar-generate-dialog.component';
import {
    DAYS_OF_WEEK,
    dayOfWeekLabel,
    dayOfWeekOf,
    displayYmd,
    isCalendarSession,
    sessionDateRange,
    summarizeSkips,
    todayVnYmd,
} from './calendar.utils';
import removeAccents from 'remove-accents';
import { ReplaySubject, Subject } from 'rxjs';
import { finalize } from 'rxjs/operators';

interface CalendarCell {
    date: string;
    dayNumber: number;
    day?: CalendarAdminDay;
    minPrice: number | null;
    reserved: number;
    quota: number;
    locked: boolean;
    past: boolean;
    today: boolean;
}

interface TicketEdit {
    price: number | null;
    quota: number | null;
}

const pad = (value: number) => String(value).padStart(2, '0');

/** 'YYYY-MM' cộng [delta] tháng. */
function shiftMonth(month: string, delta: number): string {
    const [year, mon] = month.split('-').map(Number);
    const total = year * 12 + (mon - 1) + delta;
    return `${Math.floor(total / 12)}-${pad((total % 12) + 1)}`;
}

/** Màn "Lịch bán vé": xem/sửa giá, số lượng, trạng thái bán theo từng ngày của session vé theo lịch. */
@Component({
    selector: 'ticket-calendar',
    templateUrl: './ticket-calendar.component.html',
    styleUrls: ['./ticket-calendar.component.scss'],
})
export class TicketCalendarComponent implements OnInit {
    readonly weekdays = DAYS_OF_WEEK;
    readonly displayYmd = displayYmd;
    readonly dayOfWeekLabel = dayOfWeekLabel;
    readonly today = todayVnYmd();

    showList: ShowModel[] = [];
    sessionList: ShowSessionModel[] = [];
    filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>(1);
    filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>(1);
    selectedShow?: ShowModel;
    selectedSession?: ShowSessionModel;

    categories: CalendarCategoryOption[] = [];
    sessionFrom = '';
    sessionTo = '';
    month = '';
    cells: (CalendarCell | null)[] = [];
    private days = new Map<string, CalendarAdminDay>();

    selectedDate?: string;
    ticketEdits: Record<string, TicketEdit> = {};
    capacityEdit: number | null = null;

    loading = 0;

    @ViewChild('autoInputShow') inputShow: ElementRef<HTMLInputElement>;
    @ViewChild('autoInputSession') inputSession: ElementRef<HTMLInputElement>;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private showService: ShowService,
        private ticketGateway: TicketGateway,
        private toastrService: NbToastrService,
        private dialogService: NbDialogService,
    ) {}

    ngOnInit(): void {
        const { showId, sessionId } = this.route.snapshot.queryParams;
        this.loading += 1;
        this.showService
            .getShows$()
            .pipe(finalize(() => (this.loading -= 1)))
            .subscribe(
                shows => {
                    this.showList = (shows ?? []).sort((s1, s2) => (s1.createdAt > s2.createdAt ? -1 : 1));
                    this.filteredShowList$.next(this.showList);
                    if (showId) this.onSelectShow(showId, sessionId);
                },
                error => this.toastError(error, 'Load danh sách show thất bại'),
            );
    }

    get isCalendar(): boolean {
        return isCalendarSession(this.selectedSession);
    }

    get hasCalendarSessions(): boolean {
        return this.sessionList.some(isCalendarSession);
    }

    get monthLabel(): string {
        const [year, mon] = this.month.split('-');
        return `Tháng ${Number(mon)}/${year}`;
    }

    get canPrevMonth(): boolean {
        return this.month > this.sessionFrom.slice(0, 7);
    }

    get canNextMonth(): boolean {
        return this.month < this.sessionTo.slice(0, 7);
    }

    get selectedDay(): CalendarAdminDay | undefined {
        return this.selectedDate ? this.days.get(this.selectedDate) : undefined;
    }

    get selectedDayIsPast(): boolean {
        return !!this.selectedDate && this.selectedDate < this.today;
    }

    get monthDayCount(): number {
        return this.days.size;
    }

    get monthClosedCount(): number {
        return [...this.days.values()].filter(d => d.status === 'CLOSED').length;
    }

    onInputShowChange(value: string): void {
        const keyword = removeAccents((value ?? '').toLowerCase());
        this.filteredShowList$.next(
            this.showList.filter(s => !keyword || removeAccents((s.showName ?? '').toLowerCase()).includes(keyword)),
        );
    }

    onInputSessionChange(value: string): void {
        const keyword = removeAccents((value ?? '').toLowerCase());
        this.filteredSessionList$.next(
            this.sessionList.filter(
                s =>
                    !keyword ||
                    removeAccents((s.sessionName ?? '').toLowerCase()).includes(keyword) ||
                    removeAccents((s.code ?? '').toLowerCase()).includes(keyword),
            ),
        );
    }

    /** [preferredSessionId] đến từ query param; không có thì tự chọn khi show chỉ có một suất vé theo lịch. */
    onSelectShow(showId: string, preferredSessionId?: string): void {
        const show = this.showList.find(s => s.id === showId);
        if (!show) return;
        this.selectedShow = show;
        if (this.inputShow) this.inputShow.nativeElement.value = show.showName;
        this.sessionList = show.sessions ?? [];
        this.filteredSessionList$.next(this.sessionList);
        this.resetSession();
        if (this.inputSession) this.inputSession.nativeElement.value = '';

        const calendarSessions = this.sessionList.filter(isCalendarSession);
        const sessionId = preferredSessionId ?? (calendarSessions.length === 1 ? calendarSessions[0].id : undefined);
        if (sessionId) this.onSelectSession(sessionId);
        else this.syncQueryParams();
    }

    onSelectSession(sessionId: string): void {
        const session = this.sessionList.find(s => s.id === sessionId);
        if (!session) return;
        this.resetSession();
        this.selectedSession = session;
        if (this.inputSession) this.inputSession.nativeElement.value = this.getSessionName(session);
        this.syncQueryParams();
        if (!this.isCalendar) return;

        const range = sessionDateRange(session);
        this.sessionFrom = range.from;
        this.sessionTo = range.to;
        const todayMonth = this.today.slice(0, 7);
        const firstMonth = range.from.slice(0, 7);
        const lastMonth = range.to.slice(0, 7);
        this.month = todayMonth < firstMonth ? firstMonth : todayMonth > lastMonth ? lastMonth : todayMonth;
        this.loadCategories();
        this.loadMonth();
    }

    getSessionName(session: ShowSessionModel): string {
        const name = [session.code?.trim(), session.sessionName].filter(Boolean).join(' - ');
        return isCalendarSession(session) ? name : `${name} (suất thường)`;
    }

    categoryName(category: string): string {
        return this.categories.find(c => c.category === category)?.displayName ?? category;
    }

    ticketName(ticket: CalendarAdminTicket): string {
        return ticket.dname || ticket.priceCat || this.categoryName(ticket.category);
    }

    changeMonth(delta: number): void {
        this.month = shiftMonth(this.month, delta);
        this.selectedDate = undefined;
        this.loadMonth();
    }

    selectDate(date: string): void {
        this.selectedDate = date;
        this.resetDayEdits();
    }

    openGenerateDialog(): void {
        if (!this.categories.length) {
            this.toastrService.show('Tạo hạng vé cho suất ở màn Hạng vé trước khi sinh lịch', 'Chưa có hạng vé', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        this.dialogService
            .open(CalendarGenerateDialogComponent, {
                closeOnBackdropClick: false,
                context: {
                    showId: this.selectedShow.id,
                    sessionId: this.selectedSession.id,
                    sessionFrom: this.sessionFrom,
                    sessionTo: this.sessionTo,
                    categories: this.categories,
                    submit: req => this.ticketGateway.generateCalendar$(req),
                },
            })
            .onClose.subscribe(changed => {
                if (changed) this.loadMonth();
            });
    }

    /** [date] có giá trị = mở sẵn cho đúng một ngày (từ panel chi tiết ngày). */
    openBulkDialog(mode: CalendarBulkMode, date?: string): void {
        this.dialogService
            .open(CalendarBulkDialogComponent, {
                closeOnBackdropClick: false,
                context: {
                    mode,
                    showId: this.selectedShow.id,
                    sessionId: this.selectedSession.id,
                    sessionFrom: this.sessionFrom,
                    sessionTo: this.sessionTo,
                    categories: this.categories,
                    initialFrom: date,
                    initialTo: date,
                    submitUpdate: req => this.ticketGateway.bulkUpdateCalendar$(req),
                    submitDelete: req => this.ticketGateway.deleteCalendar$(req),
                },
            })
            .onClose.subscribe(changed => {
                if (changed) this.loadMonth();
            });
    }

    setDayStatus(status: CalendarDayStatus): void {
        this.updateSelectedDay({ status }, status === 'OPEN' ? 'Đã mở bán lại ngày' : 'Đã ngừng bán ngày');
    }

    saveTicket(ticket: CalendarAdminTicket): void {
        const edit = this.ticketEdits[ticket.ticketId];
        if (!edit) return;
        const priceChanged = edit.price !== ticket.price;
        const quotaChanged = edit.quota !== ticket.quota;
        if (!priceChanged && !quotaChanged) {
            this.warn('Chưa có thay đổi');
            return;
        }
        if (priceChanged && (!Number.isInteger(edit.price) || edit.price < 0)) {
            this.warn('Giá vé phải là số nguyên ≥ 0');
            return;
        }
        if (quotaChanged && (!Number.isInteger(edit.quota) || edit.quota <= 0)) {
            this.warn('Số lượng vé phải là số nguyên > 0');
            return;
        }
        // Backend cũng chặn, nhưng báo sớm để admin hiểu vì sao ô bị bỏ qua.
        if (ticket.locked && (priceChanged || edit.quota < ticket.quota)) {
            this.warn('Vé đã có người mua/giữ: chỉ được tăng số lượng hoặc ngừng bán ngày');
            return;
        }
        this.updateSelectedDay(
            {
                categories: [ticket.category],
                price: priceChanged ? edit.price : undefined,
                quota: quotaChanged ? edit.quota : undefined,
            },
            `Đã cập nhật ${this.ticketName(ticket)}`,
        );
    }

    saveCapacity(): void {
        const capacity = this.capacityEdit;
        if (!Number.isInteger(capacity) || capacity <= 0) {
            this.warn('Sức chứa cả ngày phải là số nguyên > 0');
            return;
        }
        if (capacity === this.selectedDay?.capacity?.total) {
            this.warn('Chưa có thay đổi');
            return;
        }
        this.updateSelectedDay({ dailyCapacity: capacity }, 'Đã cập nhật sức chứa ngày');
    }

    /** Sửa đúng ngày đang chọn qua bulk-update (fromDate = toDate), rồi tải lại tháng để thấy số liệu mới. */
    private updateSelectedDay(
        change: Omit<BulkUpdateCalendarRequest, 'showId' | 'sessionId' | 'fromDate' | 'toDate'>,
        successTitle: string,
    ): void {
        const date = this.selectedDate;
        if (!date) return;
        this.loading += 1;
        this.ticketGateway
            .bulkUpdateCalendar$({
                showId: this.selectedShow.id,
                sessionId: this.selectedSession.id,
                fromDate: date,
                toDate: date,
                ...change,
            })
            .pipe(finalize(() => (this.loading -= 1)))
            .subscribe(
                res => {
                    this.notifyUpdate(res, successTitle);
                    this.loadMonth();
                },
                error => this.toastError(error, 'Cập nhật thất bại'),
            );
    }

    private notifyUpdate(res: BulkUpdateCalendarResponse, successTitle: string): void {
        if (res.skipped.length) {
            this.toastrService.show(`Bỏ qua: ${summarizeSkips(res.skipped)}`, `Cập nhật ${res.updated} mục`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
                duration: 15000,
            });
            return;
        }
        this.toastrService.show(`Cập nhật ${res.updated} mục`, successTitle, {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'success',
        });
    }

    private loadCategories(): void {
        const sessionId = this.selectedSession.id;
        this.loading += 1;
        this.ticketGateway
            .loadCategoryMappingByShowAndSession$(this.selectedShow.id, sessionId)
            .pipe(finalize(() => (this.loading -= 1)))
            .subscribe(
                mapping => {
                    if (sessionId !== this.selectedSession?.id) return;
                    // API trả catMapping dạng object JSON (key = mã hạng vé), không phải Map.
                    const entries = Object.entries(mapping?.catMapping ?? {});
                    this.categories = entries.map(([category, item]) => ({
                        category,
                        displayName: item.displayName || category,
                        price: item.price,
                    }));
                },
                error => this.toastError(error, 'Load hạng vé thất bại'),
            );
    }

    private loadMonth(): void {
        const month = this.month;
        const sessionId = this.selectedSession.id;
        this.loading += 1;
        this.ticketGateway
            .loadCalendarMonth$(this.selectedShow.id, sessionId, month)
            .pipe(finalize(() => (this.loading -= 1)))
            .subscribe(
                days => {
                    // Bỏ kết quả của tháng/suất cũ nếu admin đã chuyển trong lúc chờ.
                    if (month !== this.month || sessionId !== this.selectedSession?.id) return;
                    this.days = new Map(days.map(d => [d.date, d]));
                    this.buildCells();
                    this.resetDayEdits();
                },
                error => this.toastError(error, 'Load lịch bán vé thất bại'),
            );
    }

    /** Lưới tháng 7 cột (T2..CN): ô null là phần đệm trước ngày 1 và sau ngày cuối tháng. */
    private buildCells(): void {
        const [year, mon] = this.month.split('-').map(Number);
        const daysInMonth = new Date(Date.UTC(year, mon, 0)).getUTCDate();
        const cells: (CalendarCell | null)[] = Array(dayOfWeekOf(`${this.month}-01`) - 1).fill(null);
        for (let dayNumber = 1; dayNumber <= daysInMonth; dayNumber++) {
            const date = `${this.month}-${pad(dayNumber)}`;
            const day = this.days.get(date);
            const tickets = day?.tickets ?? [];
            cells.push({
                date,
                dayNumber,
                day,
                minPrice: tickets.length ? Math.min(...tickets.map(t => t.price)) : null,
                reserved: tickets.reduce((sum, t) => sum + t.reserved, 0),
                quota: tickets.reduce((sum, t) => sum + t.quota, 0),
                locked: tickets.some(t => t.locked),
                past: date < this.today,
                today: date === this.today,
            });
        }
        while (cells.length % 7 !== 0) cells.push(null);
        this.cells = cells;
    }

    private resetDayEdits(): void {
        const day = this.selectedDay;
        this.ticketEdits = {};
        (day?.tickets ?? []).forEach(t => (this.ticketEdits[t.ticketId] = { price: t.price, quota: t.quota }));
        this.capacityEdit = day?.capacity?.total ?? null;
    }

    private resetSession(): void {
        this.selectedSession = undefined;
        this.categories = [];
        this.sessionFrom = '';
        this.sessionTo = '';
        this.month = '';
        this.days = new Map();
        this.cells = [];
        this.selectedDate = undefined;
        this.ticketEdits = {};
        this.capacityEdit = null;
    }

    /** Giữ show/suất trên URL để mở lại hoặc gửi link đúng màn. */
    private syncQueryParams(): void {
        this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { showId: this.selectedShow?.id, sessionId: this.selectedSession?.id },
            replaceUrl: true,
        });
    }

    private warn(message: string): void {
        this.toastrService.show(message, 'Không thể cập nhật', {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'warning',
        });
    }

    private toastError(error: HttpErrorResponse, title: string): void {
        this.toastrService.show(getErrorMessageFromResponse(error), title, {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'danger',
            duration: 30000,
        });
    }
}
