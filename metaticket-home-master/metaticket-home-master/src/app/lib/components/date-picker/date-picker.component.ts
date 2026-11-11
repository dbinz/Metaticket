import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { ImageComponent } from '../image/image.component';
import { appIcons } from '../../theme';
import { NgClass, NgFor } from '@angular/common';
import { NzPopoverModule } from 'ng-zorro-antd/popover';
import { NzIconModule } from 'ng-zorro-antd/icon';
import {
  addDays,
  addMonths,
  endOfMonth,
  endOfWeek,
  getMonth,
  getYear,
  isAfter,
  isBefore,
  isSameDay,
  startOfMonth,
  startOfWeek,
  subMonths,
} from 'date-fns';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { ButtonComponent } from '../button/button.component';
import { DropDownDatePicker } from '../../dto';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
@Component({
  selector: 'app-date-picker',
  standalone: true,
  imports: [
    NzDatePickerModule,
    ImageComponent,
    NgClass,
    NzPopoverModule,
    NzIconModule,
    NgFor,
    NzDividerModule,
    ButtonComponent,
    NzTypographyComponent,
  ],
  templateUrl: './date-picker.component.html',
  styleUrl: './date-picker.component.css',
})
export class DatePickerComponent implements OnInit {
  @Output() handlerOnPressed = new EventEmitter<DropDownDatePicker>();
  @Input() iconLeft: string = '';
  @Input() iconRight: string = '';
  date = null;
  active = false;
  labelDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  labelMonth = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  daysOfCalendar: Date[] = [];
  currentDay = new Date();
  currentMonth = getMonth(this.currentDay);
  selectedButton = 'today';
  selectedDay: DropDownDatePicker = {
    fromDate: this.currentDay,
    toDate: this.currentDay,
  };
  protected displayTitle: string = '';
  getDaysofMonth(current: Date): void {
    this.daysOfCalendar = [];
    const dayStartMonth = startOfMonth(current);
    const dayEndMonth = endOfMonth(current);
    const dayStartWeek = startOfWeek(dayStartMonth, { weekStartsOn: 1 });
    const dayEndWeek = endOfWeek(dayEndMonth, { weekStartsOn: 1 });
    for (let i = dayStartWeek; i <= dayEndWeek; i = addDays(i, 1)) {
      this.daysOfCalendar.push(i);
    }
  }
  ngOnInit(): void {
    this.getDaysofMonth(this.currentDay);
  }
  protected appIcons = appIcons;
  getMonth(item: Date): number {
    return getMonth(item);
  }
  getYear(item: Date): number {
    return getYear(item);
  }
  visible: boolean = false;
  prevMonth(): void {
    this.currentDay = subMonths(this.currentDay, 1);
    this.currentMonth = getMonth(this.currentDay);
    this.getDaysofMonth(this.currentDay);
  }
  nextMonth(): void {
    this.currentDay = addMonths(this.currentDay, 1);
    this.currentMonth = getMonth(this.currentDay);
    this.getDaysofMonth(this.currentDay);
  }
  isSameDay(day1: Date, day2: Date): boolean {
    return isSameDay(day1, day2);
  }
  isAfterDay(day1: Date, day2: Date): boolean {
    return isAfter(day1, day2);
  }
  isBeforeDay(day1: Date, day2: Date): boolean {
    return isBefore(day1, day2);
  }
  pickDateToday(): void {
    this.currentDay = new Date();
    this.getDaysofMonth(this.currentDay);
    this.currentMonth = getMonth(this.currentDay);
    this.selectedDay = {
      fromDate: new Date(),
      toDate: new Date(),
    };
    this.selectedButton = 'today';
    this.handlerOnPressed.emit(this.selectedDay);
    this.displayTitle = this.formatDate(this.selectedDay.fromDate?.toString() || '');
  }
  pickThisWeek(): void {
    this.currentDay = new Date();
    this.getDaysofMonth(this.currentDay);
    this.currentMonth = getMonth(this.currentDay);
    const dayStartWeek = startOfWeek(new Date(), { weekStartsOn: 1 });
    const dayEndWeek = endOfWeek(new Date(), { weekStartsOn: 1 });
    this.selectedDay = {
      fromDate: dayStartWeek,
      toDate: dayEndWeek,
    };
    this.selectedButton = 'this-week';
    this.handlerOnPressed.emit(this.selectedDay);
    this.displayTitle = `${this.formatDate(this.selectedDay.fromDate?.toString() || '')} - ${this.formatDate(
      this.selectedDay.toDate?.toString() || '',
    )}`;
  }
  pickOption(): void {
    this.selectedButton = 'option';
  }
  choseDate(item: Date): void {
    if (this.selectedButton !== 'option') {
      return;
    }
    /// co ca fromDate va toDate
    if (this.selectedDay.fromDate !== null && this.selectedDay.toDate !== null) {
      if (isSameDay(this.selectedDay.fromDate, this.selectedDay.toDate) && isSameDay(item, this.selectedDay.toDate)) {
        this.selectedDay.fromDate = null;
        this.selectedDay.toDate = null;
        this.displayTitle = this.formatDate('');
      } else if (isSameDay(item, this.selectedDay.fromDate)) {
        this.selectedDay.fromDate = this.selectedDay.toDate;
        this.displayTitle = this.formatDate(this.selectedDay.fromDate?.toString() || '');
      } else if (isSameDay(item, this.selectedDay.toDate)) {
        this.selectedDay.toDate = this.selectedDay.fromDate;
        this.displayTitle = this.formatDate(this.selectedDay.fromDate?.toString() || '');
      } else {
        this.selectedDay.fromDate = item;
        this.selectedDay.toDate = null;
        this.displayTitle = this.formatDate(this.selectedDay.fromDate?.toString() || '');
      }
    } else if (this.selectedDay.fromDate !== null) {
      if (isAfter(item, this.selectedDay.fromDate)) {
        this.selectedDay.toDate = item;
        this.displayTitle = `${this.formatDate(this.selectedDay.fromDate?.toString() || '')} - ${this.formatDate(
          this.selectedDay.toDate?.toString() || '',
        )}`;
      }
    } else {
      this.selectedDay.fromDate = item;
      this.displayTitle = this.formatDate(this.selectedDay.fromDate?.toString() || '');
    }
    this.handlerOnPressed.emit(this.selectedDay);
  }

  formatDate(date?: string): string {
    if (!date) return '';

    const dateFormat = new Date(date);
    return dateFormat.toLocaleDateString('vi-VN', {
      day: 'numeric',
      month: 'numeric',
      year: 'numeric',
    });
  }
}
