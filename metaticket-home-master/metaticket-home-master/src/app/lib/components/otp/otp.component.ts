import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  QueryList,
  ViewChildren,
} from '@angular/core';
import { ButtonComponent } from '../button/button.component';
import { CountdownTimerComponent } from '../countdown-timer/countdown-timer.component';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { BehaviorSubject, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-otp',
  templateUrl: './otp.component.html',
  standalone: true,
  imports: [ButtonComponent, CommonModule, CountdownTimerComponent, NzTypographyComponent],
})
export class OtpComponent implements OnInit, OnDestroy {
  @Input() title: string = 'Xác thực OTP';
  @Input() description?: string = 'Vui lòng nhập mã OTP đã được META Ticket gửi đến số điện thoại của bạn';
  @Input() error?: BehaviorSubject<string>;
  @Input() loading: boolean = false;

  @Output() callback = new EventEmitter<string>();
  @Output() resenOtp = new EventEmitter<void>();

  @ViewChildren('otpInput') private otpInputs!: QueryList<ElementRef<HTMLInputElement>>;

  protected isOtpComplete = false; // Trạng thái kiểm tra OTP đầy đủ

  protected otp: string[] = new Array<string>(6).fill('');
  protected readonly list = [1, 2, 3, 4, 5, 6];
  protected minutes: number = 0;
  protected seconds: number = 5;
  protected clickAble: boolean = false;

  // Thêm một Subject để quản lý vòng đời của các subscription
  private readonly destroy$ = new Subject<void>();

  constructor() {}

  ngOnInit(): void {
    this.error?.pipe(takeUntil(this.destroy$)).subscribe((msg: string) => {
      if (msg) {
        this.clear();
      }
    });

    setTimeout(
      () => {
        this.clickAble = true;
      },
      this.minutes * 60 * 1000 + this.seconds * 1000,
    );
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  protected onBeforeInput(event: InputEvent): void {
    const input = event.target as HTMLInputElement;
    input.value = ''; // Xóa nội dung cũ trước khi nhập mới
  }

  protected resendOtp(): void {
    this.resenOtp.emit();
  }

  protected onInput(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const value: string = input.value;

    if (!/^\d$/.test(value)) {
      input.value = ''; // Xóa ký tự không hợp lệ
      return;
    }

    this.otp[index] = value;
    this.checkOtpComplete();

    // Tự động focus vào ô tiếp theo nếu còn
    if (value && index < this.list.length - 1) {
      const nextInput = this.otpInputs.get(index + 1)?.nativeElement;
      nextInput?.focus();
    }
  }

  protected onKeyDown(event: KeyboardEvent, index: number): void {
    const input = event.target as HTMLInputElement;

    // Cho phép xóa và quay lại ô trước khi nhấn Backspace
    if (event.key === 'Backspace' && index > 0 && !input.value) {
      const prevInput = this.otpInputs.get(index - 1)?.nativeElement;
      prevInput?.focus();
    }

    if (event.key === 'Enter') {
      this.checkOtpComplete();
      if (this.isOtpComplete) {
        this.onSubmit();
      }
    }
  }

  private checkOtpComplete(): void {
    this.isOtpComplete = this.otp.every((val) => /^\d$/.test(val));
  }

  protected onSubmit(): void {
    // Cải tiến: Lấy giá trị otp từ mảng state thay vì đọc lại từ DOM
    const otp = this.otp.join('');
    this.callback.emit(otp);
  }

  private clear(): void {
    this.otp.fill('');
    this.otpInputs.forEach((input) => (input.nativeElement.value = ''));
    // Thêm optional chaining để an toàn hơn
    this.otpInputs.first?.nativeElement.focus();
  }
}
