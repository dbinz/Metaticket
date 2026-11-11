import { Component, OnDestroy, OnInit } from '@angular/core';
import { NgClass, NgForOf, NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { Observable, Subject, catchError, map, of, switchMap, takeUntil, tap } from 'rxjs';
import { CalendarDayResponse } from 'src/app/lib/entities';
import { TicketService } from 'src/app/lib/services';
import { BookingQuery, BookingService } from 'src/app/lib/stores';
import {
  addMonthsToMonthKey,
  buildMonthGrid,
  formatDateKey,
  formatMonthKey,
  openingHoursLabel,
  shortPriceLabel,
  toVnMonthKey,
} from 'src/app/lib/utils';
import { BookingPolicyDescComponent } from '../booking-policy-desc/booking-policy-desc.component';

/** `loading` = month data not loaded yet; `disabled` = closed, unavailable or not a calendar date. */
type CalendarCellKind = 'loading' | 'available' | 'soldOut' | 'disabled';

interface CalendarCell {
  /** null pads the first/last week of the grid. */
  dateKey: string | null;
  day: number;
  kind: CalendarCellKind;
  subLabel: string;
}

type MonthLoadResult = { days: CalendarDayResponse[] } | { error: unknown };

/**
 * Bước chọn ngày tham quan của suất theo lịch (sessionType = CALENDAR): lưới tháng hiện ngay, trạng thái + giá từng ngày
 * nạp sau từ `ticket/calendar/month`. Chọn ngày xong mới hiện danh sách vé của ngày đó (dùng lại booking-policy-desc).
 */
@Component({
  selector: 'app-booking-calendar',
  standalone: true,
  imports: [NgClass, NgForOf, NgIf, NzTypographyComponent, BookingPolicyDescComponent],
  templateUrl: './booking-calendar.component.html',
})
export class BookingCalendarComponent implements OnInit, OnDestroy {
  readonly weekdayLabels = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
  readonly openingHours: string;
  month = '';
  cells: CalendarCell[] = [];
  selectedDate?: string;
  isMonthLoading = false;
  loadError = '';

  private readonly _showId: string;
  private readonly _sessionId: string;
  private readonly _minMonth: string;
  private readonly _maxMonth: string;
  private readonly _month$ = new Subject<string>();
  private readonly _destroy$ = new Subject<void>();
  private _days = new Map<string, CalendarDayResponse>();

  constructor(
    private _ticketService: TicketService,
    private _bookingQuery: BookingQuery,
    private _bookingService: BookingService,
  ) {
    const state = this._bookingQuery.getValue();
    const session = state.showDetail?.sessions?.find((item) => item.id === state.sessionId);
    this._showId = state.showId;
    this._sessionId = state.sessionId;
    this._minMonth = toVnMonthKey(session?.startTime ?? Date.now());
    this._maxMonth = session ? toVnMonthKey(session.endTime) : this._minMonth;
    this.openingHours = openingHoursLabel(session?.calendar);
    // Restored booking (back to this step / page reload) keeps its date.
    this.selectedDate = state.visitDate;
  }

  get monthLabel(): string {
    return this.month ? formatMonthKey(this.month) : '';
  }

  get canGoPrev(): boolean {
    return this.month > this._minMonth;
  }

  get canGoNext(): boolean {
    return this.month < this._maxMonth;
  }

  get selectedDateLabel(): string {
    return this.selectedDate ? formatDateKey(this.selectedDate) : '';
  }

  ngOnInit(): void {
    this._month$
      .pipe(
        tap((month) => this._startMonth(month)),
        // switchMap: quick prev/next clicks must not let an older month's response paint the current grid.
        switchMap((month) => this._loadMonth(month)),
        takeUntil(this._destroy$),
      )
      .subscribe((result) => this._applyMonth(result));

    this._month$.next(this._initialMonth());
  }

  ngOnDestroy(): void {
    this._destroy$.next();
    this._destroy$.complete();
  }

  onPrevMonth(): void {
    if (this.canGoPrev) {
      this._month$.next(addMonthsToMonthKey(this.month, -1));
    }
  }

  onNextMonth(): void {
    if (this.canGoNext) {
      this._month$.next(addMonthsToMonthKey(this.month, 1));
    }
  }

  onRetry(): void {
    this._month$.next(this.month);
  }

  onSelectDate(cell: CalendarCell): void {
    if (!cell.dateKey || cell.kind !== 'available' || cell.dateKey === this.selectedDate) {
      return;
    }
    this.selectedDate = cell.dateKey;
    this._bookingService.selectVisitDate(cell.dateKey);
  }

  cellAriaLabel(cell: CalendarCell): string {
    const date = cell.dateKey ? formatDateKey(cell.dateKey) : '';
    return cell.subLabel ? `${date}, ${cell.subLabel}` : date;
  }

  trackByIndex(index: number): number {
    return index;
  }

  /** Start on the chosen date's month, otherwise the current month, kept inside the session's months. */
  private _initialMonth(): string {
    const preferred = this.selectedDate ? this.selectedDate.slice(0, 7) : toVnMonthKey(Date.now());
    if (preferred < this._minMonth) {
      return this._minMonth;
    }
    return preferred > this._maxMonth ? this._maxMonth : preferred;
  }

  /** Paint the bare grid right away so the customer sees the calendar before prices arrive. */
  private _startMonth(month: string): void {
    this.month = month;
    this._days = new Map();
    this.loadError = '';
    this.isMonthLoading = true;
    this._buildCells();
  }

  private _loadMonth(month: string): Observable<MonthLoadResult> {
    return this._ticketService.getCalendarMonth(this._showId, this._sessionId, month).pipe(
      map((days) => ({ days })),
      catchError((error: unknown) => of({ error })),
    );
  }

  private _applyMonth(result: MonthLoadResult): void {
    this.isMonthLoading = false;
    if ('error' in result) {
      this.loadError =
        result.error instanceof HttpErrorResponse && result.error.status === 403
          ? 'Suất này hiện chưa mở bán vé theo ngày.'
          : 'Không tải được lịch, vui lòng thử lại.';
      this._buildCells();
      return;
    }
    this._days = new Map(result.days.map((day) => [day.date, day]));
    this._buildCells();
  }

  private _buildCells(): void {
    this.cells = buildMonthGrid(this.month).map((dateKey) => {
      if (!dateKey) {
        return { dateKey, day: 0, kind: 'disabled', subLabel: '' };
      }
      const day = Number(dateKey.slice(8, 10));
      if (this.isMonthLoading || this.loadError) {
        return { dateKey, day, kind: 'loading', subLabel: '' };
      }
      return { dateKey, day, ...this._describeDay(this._days.get(dateKey)) };
    });
  }

  /** Dates missing from the response are not part of the calendar and render like closed ones. */
  private _describeDay(info?: CalendarDayResponse): Pick<CalendarCell, 'kind' | 'subLabel'> {
    switch (info?.status) {
      case 'AVAILABLE':
        return { kind: 'available', subLabel: info.minPrice != null ? shortPriceLabel(info.minPrice) : '' };
      case 'SOLD_OUT':
        return { kind: 'soldOut', subLabel: 'Hết vé' };
      default:
        return { kind: 'disabled', subLabel: '' };
    }
  }
}
