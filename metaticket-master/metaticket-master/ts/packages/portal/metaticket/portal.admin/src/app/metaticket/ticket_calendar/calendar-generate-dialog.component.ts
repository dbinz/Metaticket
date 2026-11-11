import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NbCalendarRange, NbDialogRef, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { GenerateCalendarResponse } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { CalendarByDayOfWeek, GenerateCalendarRequest } from '../gateway/request.ticket';
import {
    addDays,
    DAYS_OF_WEEK,
    DayOfWeekOption,
    describeSkip,
    displayYmd,
    listDates,
    toYmd,
    todayVnYmd,
    validateDateRange,
    ymdToLocalDate,
} from './calendar.utils';
import { Observable } from 'rxjs';

export interface CalendarCategoryOption {
    category: string;
    displayName: string;
    price: number;
}

interface MatrixRow {
    category: string;
    displayName: string;
    include: boolean;
    defaultPrice: number | null;
    defaultQuota: number | null;
    /** Giá/quota riêng theo thứ; null = dùng mặc định của hàng. */
    price: Record<number, number | null>;
    quota: Record<number, number | null>;
}

const DEFAULT_RANGE_DAYS = 30;
const MAX_SKIPPED_SHOWN = 200;

/**
 * Dialog "Sinh lịch / Gia hạn": khoảng ngày + thứ + ngày loại trừ, bảng giá/quota hạng vé × thứ.
 * Không inject service của MetaticketModule (NbDialog chạy dưới injector của AppModule), nên trang gọi API qua [submit].
 */
@Component({
    selector: 'calendar-generate-dialog',
    templateUrl: './calendar-generate-dialog.component.html',
    styleUrls: ['./calendar-dialog.scss'],
})
export class CalendarGenerateDialogComponent implements OnInit {
    @Input() showId!: string;
    @Input() sessionId!: string;
    @Input() sessionFrom!: string;
    @Input() sessionTo!: string;
    @Input() categories: CalendarCategoryOption[] = [];
    @Input() submit!: (req: GenerateCalendarRequest) => Observable<GenerateCalendarResponse>;

    readonly weekdays = DAYS_OF_WEEK;
    readonly displayYmd = displayYmd;

    range: NbCalendarRange<Date> | null = null;
    selectedDays = new Set<number>(DAYS_OF_WEEK.map(d => d.value));
    excludeDates: string[] = [];
    excludeInput: Date | null = null;
    rows: MatrixRow[] = [];

    useDailyCapacity = false;
    capacityDefault: number | null = null;
    capacityByDay: Record<number, number | null> = {};

    loading = false;
    result: GenerateCalendarResponse | null = null;
    skippedLines: string[] = [];

    constructor(
        private toastr: NbToastrService,
        public ref: NbDialogRef<CalendarGenerateDialogComponent>,
    ) {}

    ngOnInit(): void {
        const today = todayVnYmd();
        const from = today > this.sessionFrom ? today : this.sessionFrom;
        const plannedTo = addDays(from, DEFAULT_RANGE_DAYS - 1);
        const to = plannedTo < this.sessionTo ? plannedTo : this.sessionTo;
        if (from <= to) this.range = { start: ymdToLocalDate(from), end: ymdToLocalDate(to) };

        this.rows = this.categories.map(c => ({
            category: c.category,
            displayName: c.displayName || c.category,
            include: true,
            defaultPrice: c.price ?? null,
            defaultQuota: null,
            price: {},
            quota: {},
        }));
    }

    get fromDate(): string {
        return this.range?.start ? toYmd(this.range.start) : '';
    }

    get toDate(): string {
        return this.range?.end ? toYmd(this.range.end) : '';
    }

    get selectedWeekdays(): DayOfWeekOption[] {
        return this.weekdays.filter(d => this.selectedDays.has(d.value));
    }

    get includedRows(): MatrixRow[] {
        return this.rows.filter(r => r.include);
    }

    /** Các ngày sẽ được sinh (chưa trừ ngày đã tồn tại — backend báo lại sau khi sinh). */
    get previewDates(): string[] {
        return listDates(this.fromDate, this.toDate, [...this.selectedDays], new Set(this.excludeDates));
    }

    toggleDay(day: number, checked: boolean): void {
        if (checked) this.selectedDays.add(day);
        else this.selectedDays.delete(day);
    }

    addExcludeDate(date: Date | null): void {
        if (!date) return;
        const ymd = toYmd(date);
        if (!this.excludeDates.includes(ymd)) this.excludeDates = [...this.excludeDates, ymd].sort();
        this.excludeInput = null;
    }

    removeExcludeDate(ymd: string): void {
        this.excludeDates = this.excludeDates.filter(d => d !== ymd);
    }

    resolvedPrice(row: MatrixRow, day: number): number | null {
        return row.price[day] ?? row.defaultPrice;
    }

    resolvedQuota(row: MatrixRow, day: number): number | null {
        return row.quota[day] ?? row.defaultQuota;
    }

    /** Ghi giá mặc định của hàng vào mọi ô thứ đang chọn để sửa tiếp từng ô. */
    applyPriceToRow(row: MatrixRow): void {
        this.selectedWeekdays.forEach(d => (row.price[d.value] = row.defaultPrice));
    }

    /** Lấy quota của hàng đầu tiên có giá trị trong cột rồi áp cho mọi hàng đang chọn. */
    applyQuotaToColumn(day: number | 'default'): void {
        const quotaOf = (row: MatrixRow) => (day === 'default' ? row.defaultQuota : this.resolvedQuota(row, day));
        const source = this.includedRows.map(quotaOf).find(q => q !== null && q !== undefined);
        if (source === undefined) {
            this.warn('Chưa có số lượng nào trong cột để áp dụng');
            return;
        }
        this.includedRows.forEach(row => {
            if (day === 'default') row.defaultQuota = source;
            else row.quota[day] = source;
        });
    }

    cancel(): void {
        this.ref.close(!!this.result);
    }

    onSubmit(): void {
        const req = this.buildRequest();
        if (!req) return;
        this.loading = true;
        this.submit(req).subscribe(
            res => {
                this.loading = false;
                this.result = res;
                const nameOf = (cat: string) => this.rows.find(r => r.category === cat)?.displayName ?? cat;
                this.skippedLines = res.skipped.slice(0, MAX_SKIPPED_SHOWN).map(s => describeSkip(s, nameOf));
            },
            (error: HttpErrorResponse) => {
                this.loading = false;
                this.toastr.show(getErrorMessageFromResponse(error), 'Sinh lịch thất bại', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'danger',
                    duration: 30000,
                });
            },
        );
    }

    /** Kiểm tra giống backend rồi dựng request; trả null (kèm toast) khi dữ liệu chưa hợp lệ. */
    private buildRequest(): GenerateCalendarRequest | null {
        const rangeError = validateDateRange(this.fromDate, this.toDate, this.sessionFrom, this.sessionTo);
        if (rangeError) return this.warn(rangeError);
        const days = this.selectedWeekdays.map(d => d.value);
        if (days.length === 0) return this.warn('Chọn ít nhất một thứ trong tuần');
        if (this.previewDates.length === 0) return this.warn('Không có ngày nào khớp lịch đã chọn');
        const rows = this.includedRows;
        if (rows.length === 0) return this.warn('Chọn ít nhất một loại vé');

        const types: GenerateCalendarRequest['types'] = [];
        for (const row of rows) {
            const price = this.toByDayOfWeek(row.defaultPrice, row.price, days, isValidPrice);
            if (!price) return this.warn(`Giá của ${row.displayName} phải là số nguyên ≥ 0`);
            const quota = this.toByDayOfWeek(row.defaultQuota, row.quota, days, isValidQuota);
            if (!quota) return this.warn(`Số lượng của ${row.displayName} phải là số nguyên > 0`);
            types.push({ category: row.category, price, quota });
        }

        let dailyCapacity: CalendarByDayOfWeek<number> | undefined;
        if (this.useDailyCapacity) {
            dailyCapacity = this.toByDayOfWeek(this.capacityDefault, this.capacityByDay, days, isValidQuota);
            if (!dailyCapacity) return this.warn('Sức chứa cả ngày phải là số nguyên > 0');
        }

        return {
            showId: this.showId,
            sessionId: this.sessionId,
            fromDate: this.fromDate,
            toDate: this.toDate,
            daysOfWeek: days,
            excludeDates: this.excludeDates.filter(d => d >= this.fromDate && d <= this.toDate),
            types,
            dailyCapacity,
        };
    }

    /** Chỉ gửi giá trị riêng của thứ đang chọn (backend từ chối khóa thứ nằm ngoài daysOfWeek). */
    private toByDayOfWeek(
        defaultValue: number | null,
        byDay: Record<number, number | null>,
        days: number[],
        isValid: (value: number) => boolean,
    ): CalendarByDayOfWeek<number> | null {
        if (!isPresent(defaultValue) || !isValid(defaultValue)) return null;
        const overrides: Record<string, number> = {};
        for (const day of days) {
            const value = byDay[day];
            if (!isPresent(value)) continue;
            if (!isValid(value)) return null;
            overrides[String(day)] = value;
        }
        return Object.keys(overrides).length > 0
            ? { default: defaultValue, byDayOfWeek: overrides }
            : { default: defaultValue };
    }

    private warn(message: string): null {
        this.toastr.show(message, 'Dữ liệu chưa hợp lệ', {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'warning',
        });
        return null;
    }
}

function isPresent(value: number | null | undefined): value is number {
    return value !== null && value !== undefined && `${value}` !== '';
}

function isValidPrice(value: number): boolean {
    return Number.isInteger(value) && value >= 0;
}

function isValidQuota(value: number): boolean {
    return Number.isInteger(value) && value > 0;
}
