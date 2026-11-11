import { HttpErrorResponse } from '@angular/common/http';
import { Component, Input, OnInit } from '@angular/core';
import { NbCalendarRange, NbDialogRef, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { BulkUpdateCalendarResponse, CalendarSkip, DeleteCalendarResponse } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { BulkUpdateCalendarRequest, CalendarSelectionRequest, DeleteCalendarRequest } from '../gateway/request.ticket';
import { CalendarCategoryOption } from './calendar-generate-dialog.component';
import {
    addDays,
    DAYS_OF_WEEK,
    describeSkip,
    displayYmd,
    listDates,
    toYmd,
    todayVnYmd,
    validateDateRange,
    ymdToLocalDate,
} from './calendar.utils';
import { Observable } from 'rxjs';

export type CalendarBulkMode = 'update' | 'delete';

const DEFAULT_RANGE_DAYS = 30;
const MAX_SKIPPED_SHOWN = 200;

/**
 * Dialog "Sửa hàng loạt" / "Xóa ngày" trên một vùng ngày × thứ × loại vé.
 * Không inject service của MetaticketModule (NbDialog chạy dưới injector của AppModule), nên trang gọi API qua callback.
 */
@Component({
    selector: 'calendar-bulk-dialog',
    templateUrl: './calendar-bulk-dialog.component.html',
    styleUrls: ['./calendar-dialog.scss'],
})
export class CalendarBulkDialogComponent implements OnInit {
    @Input() mode: CalendarBulkMode = 'update';
    @Input() showId!: string;
    @Input() sessionId!: string;
    @Input() sessionFrom!: string;
    @Input() sessionTo!: string;
    @Input() categories: CalendarCategoryOption[] = [];
    /** Mở sẵn cho một ngày/khoảng ngày (vd. từ panel chi tiết ngày). */
    @Input() initialFrom?: string;
    @Input() initialTo?: string;
    @Input() submitUpdate!: (req: BulkUpdateCalendarRequest) => Observable<BulkUpdateCalendarResponse>;
    @Input() submitDelete!: (req: DeleteCalendarRequest) => Observable<DeleteCalendarResponse>;

    readonly weekdays = DAYS_OF_WEEK;
    readonly displayYmd = displayYmd;

    range: NbCalendarRange<Date> | null = null;
    selectedDays = new Set<number>();
    selectedCategories: string[] = [];

    changePrice = false;
    price: number | null = null;
    changeQuota = false;
    quota: number | null = null;
    status: 'KEEP' | 'OPEN' | 'CLOSED' = 'KEEP';
    changeCapacity = false;
    capacity: number | null = null;

    confirmingDelete = false;
    loading = false;
    resultMessage: string | null = null;
    skippedLines: string[] = [];
    skippedTotal = 0;

    constructor(
        private toastr: NbToastrService,
        public ref: NbDialogRef<CalendarBulkDialogComponent>,
    ) {}

    ngOnInit(): void {
        const today = todayVnYmd();
        const from = this.initialFrom ?? (today > this.sessionFrom ? today : this.sessionFrom);
        const plannedTo = this.initialTo ?? addDays(from, DEFAULT_RANGE_DAYS - 1);
        const to = plannedTo < this.sessionTo ? plannedTo : this.sessionTo;
        if (from <= to) this.range = { start: ymdToLocalDate(from), end: ymdToLocalDate(to) };
    }

    get isDelete(): boolean {
        return this.mode === 'delete';
    }

    get fromDate(): string {
        return this.range?.start ? toYmd(this.range.start) : '';
    }

    get toDate(): string {
        return this.range?.end ? toYmd(this.range.end) : '';
    }

    get matchedDateCount(): number {
        return listDates(this.fromDate, this.toDate, [...this.selectedDays]).length;
    }

    toggleDay(day: number, checked: boolean): void {
        if (checked) this.selectedDays.add(day);
        else this.selectedDays.delete(day);
        this.confirmingDelete = false;
    }

    cancel(): void {
        this.ref.close(this.resultMessage !== null);
    }

    onSubmit(): void {
        const selection = this.buildSelection();
        if (!selection) return;
        if (this.isDelete) {
            // Xóa không hoàn tác được nên bắt bấm xác nhận lần hai sau khi đã thấy số ngày bị ảnh hưởng.
            if (!this.confirmingDelete) {
                this.confirmingDelete = true;
                return;
            }
            this.run(this.submitDelete(selection), res => `Đã xóa ${res.deletedTickets} vé, ${res.deletedDays} ngày.`);
            return;
        }
        const req = this.buildUpdate(selection);
        if (!req) return;
        this.run(this.submitUpdate(req), res => `Đã cập nhật ${res.updated} mục.`);
    }

    private run<T extends { skipped: CalendarSkip[] }>(request$: Observable<T>, describe: (res: T) => string): void {
        this.loading = true;
        request$.subscribe(
            res => {
                this.loading = false;
                this.confirmingDelete = false;
                this.resultMessage = describe(res);
                const nameOf = (cat: string) => this.categories.find(c => c.category === cat)?.displayName ?? cat;
                this.skippedTotal = res.skipped.length;
                this.skippedLines = res.skipped.slice(0, MAX_SKIPPED_SHOWN).map(s => describeSkip(s, nameOf));
            },
            (error: HttpErrorResponse) => {
                this.loading = false;
                this.confirmingDelete = false;
                this.toastr.show(
                    getErrorMessageFromResponse(error),
                    this.isDelete ? 'Xóa thất bại' : 'Cập nhật thất bại',
                    {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    },
                );
            },
        );
    }

    private buildSelection(): CalendarSelectionRequest | null {
        const rangeError = validateDateRange(this.fromDate, this.toDate, this.sessionFrom, this.sessionTo);
        if (rangeError) return this.warn(rangeError);
        if (this.matchedDateCount === 0) return this.warn('Không có ngày nào khớp lựa chọn');
        return {
            showId: this.showId,
            sessionId: this.sessionId,
            fromDate: this.fromDate,
            toDate: this.toDate,
            daysOfWeek: this.selectedDays.size > 0 ? [...this.selectedDays].sort() : undefined,
            categories: this.selectedCategories.length > 0 ? this.selectedCategories : undefined,
        };
    }

    private buildUpdate(selection: CalendarSelectionRequest): BulkUpdateCalendarRequest | null {
        const req: BulkUpdateCalendarRequest = { ...selection };
        if (this.changePrice) {
            if (!Number.isInteger(this.price) || this.price < 0) return this.warn('Giá vé phải là số nguyên ≥ 0');
            req.price = this.price;
        }
        if (this.changeQuota) {
            if (!Number.isInteger(this.quota) || this.quota <= 0) return this.warn('Số lượng vé phải là số nguyên > 0');
            req.quota = this.quota;
        }
        if (this.status !== 'KEEP') req.status = this.status;
        if (this.changeCapacity) {
            if (!Number.isInteger(this.capacity) || this.capacity <= 0) {
                return this.warn('Sức chứa cả ngày phải là số nguyên > 0');
            }
            req.dailyCapacity = this.capacity;
        }
        const hasChange = [req.price, req.quota, req.status, req.dailyCapacity].some(v => v !== undefined);
        return hasChange ? req : this.warn('Chưa chọn thay đổi nào');
    }

    private warn(message: string): null {
        this.toastr.show(message, 'Dữ liệu chưa hợp lệ', {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'warning',
        });
        return null;
    }
}
