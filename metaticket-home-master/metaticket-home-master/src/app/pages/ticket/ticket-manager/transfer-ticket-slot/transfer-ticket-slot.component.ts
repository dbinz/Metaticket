import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgIf, NgSwitch, NgSwitchCase } from '@angular/common';
import {
  CaptchaSettingResponse,
  TicketSlotResponse,
  TicketSlotStateResponse,
  TransferTicketSlotRequest,
} from 'src/app/lib/entities';
import { HttpErrorResponse } from '@angular/common/http';
import { TicketSlotsService } from 'src/app/lib/services';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { RecaptchaComponent, RecaptchaModule } from 'ng-recaptcha';
import { NgHcaptchaComponent, NgHcaptchaModule } from 'ng-hcaptcha';
import { ButtonComponent, FormInputComponent, OtpComponent, PopupErrorComponent } from 'src/app/lib/components';
import { serverErrorText, validationRegex, validators } from 'src/app/lib/utils';
import { NzFormDirective } from 'ng-zorro-antd/form';

export type TransferTicketSlotPopupStep = 'confirm' | 'success' | 'error' | 'form' | 'otp';

@Component({
  selector: 'app-transfer-ticket-slot',
  templateUrl: './transfer-ticket-slot.component.html',
  styleUrls: ['./transfer-ticket-slot.component.scss'],
  standalone: true,
  imports: [
    NgSwitch,
    NgSwitchCase,
    NgIf,
    ButtonComponent,
    FormInputComponent,
    NgHcaptchaModule,
    NzFormDirective,
    ReactiveFormsModule,
    RecaptchaModule,
    OtpComponent,
    PopupErrorComponent,
  ],
})
export class TransferTicketSlotComponent {
  @Input() visible: boolean = false;
  @Input() ticketSlot!: TicketSlotResponse;
  @Input() state!: TicketSlotStateResponse;

  @Output() closed = new EventEmitter<void>();
  @Output() transferSuccess = new EventEmitter<void>();

  protected step: TransferTicketSlotPopupStep = 'confirm';

  @ViewChild('recaptchaRef') recaptchaRef: RecaptchaComponent | undefined;
  @ViewChild('hcaptchaRef') hcaptchaRef: NgHcaptchaComponent | undefined;
  @ViewChild('popupError') popupError!: PopupErrorComponent;

  protected captchaSetting!: CaptchaSettingResponse;
  protected loading: boolean = false;

  public formGroup!: FormGroup<{
    newOwnerName: FormControl<string | null>;
    newOwnerPhone: FormControl<string | null>;
    confirmationNewOwnerPhone: FormControl<string | null>;
    captchaToken: FormControl<string | null>;
  }>;

  protected otpError: BehaviorSubject<string> = new BehaviorSubject<string>('');

  protected errorMessage: string = '';
  protected countdown = 0;
  private interval?: number | null = null;

  constructor(
    private _fb: FormBuilder,
    private ticketSlotService: TicketSlotsService,
  ) {}

  private _initialForm(): void {
    this.formGroup = this._fb.group({
      newOwnerName: ['', Validators.compose([validators.required('Vui lòng nhập tên người nhận')])],
      newOwnerPhone: [
        '',
        Validators.compose([
          validators.required('Vui lòng nhập số điện thoại người nhận'),
          validators.regExp('valid', validationRegex.phoneNumber, 'Số điện thoại không hợp lệ'),
        ]),
      ],
      confirmationNewOwnerPhone: [
        '',
        Validators.compose([
          validators.required('Vui lòng nhập lại số điện thoại người nhận'),
          validators.match('math1', 'newOwnerPhone', 'Số điện thoại không khớp'),
        ]),
      ],
      captchaToken: [
        '',
        Validators.compose(this.captchaSetting.enable ? [validators.required('Chưa xác nhận bạn là con người')] : []),
      ],
    });
  }

  closePopup(): void {
    this.step = 'confirm';
    this.closed.emit();
    this.clearCountdown();
  }

  async confirmAction(): Promise<void> {
    this.step = 'form';
    this.loading = true;
    try {
      this.captchaSetting = await firstValueFrom(this.ticketSlotService.getCaptchaSetting());
      this._initialForm();
    } catch (error) {
      console.error('Failed to load captcha settings', error);
      this._initialForm();
      await this.popupError.open('Không thể tải được cài đặt captcha. Vui lòng thử lại sau.');
    } finally {
      this.loading = false;
    }
  }

  protected async submit(): Promise<void> {
    if (this.formGroup.invalid) {
      Object.values(this.formGroup.controls).forEach((control) => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });
      return;
    }

    if (this.formGroup.controls.newOwnerPhone.value != this.formGroup.controls.confirmationNewOwnerPhone.value) {
      await this.popupError.open('Số điện thoại không khớp');
      return;
    }

    try {
      this.loading = true;
      const captchaToken = this.captchaSetting.enable ? this.formGroup.controls.captchaToken.value! : undefined;
      await firstValueFrom(this.ticketSlotService.initSessionTransferTicketSlot(captchaToken));
      await firstValueFrom(this.ticketSlotService.sendOtpTransferTicketSlot(this.ticketSlot.id));
      this.step = 'otp';
    } catch (error: any) {
      if (error instanceof HttpErrorResponse) {
        await this.popupError.open((error.error as string) ?? 'Tặng vé thất bại');
      } else if (error instanceof Error) {
        await this.popupError.open(error.message ?? 'Tặng vé thất bại');
      } else {
        await this.popupError.open('Tặng vé thất bại');
      }
    } finally {
      this.resetCaptcha();
      this.loading = false;
    }
  }

  private startCountdown(): void {
    this.clearCountdown();
    this.countdown = 30;
    this.interval = window.setInterval(() => {
      this.countdown--;
      if (this.countdown <= 0) {
        this.closePopup();
      }
    }, 1000);
  }

  private clearCountdown(): void {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
  }

  protected resolvedCaptcha(captchaResponse: string | null): void {
    this.formGroup.controls.captchaToken.setValue(captchaResponse);
  }

  private resetCaptcha(): void {
    if (this.captchaSetting.enable) {
      this.recaptchaRef?.reset();
      this.hcaptchaRef?.reset();
      this.formGroup.controls.captchaToken.setValue('');
    }
  }

  protected onHcaptchaVerify(token: string): void {
    // The verification process was successful.
    // You can verify the token on your server now.
    console.log(token);
  }

  protected onHcaptchaExpired(response: any): void {
    // The verification expired.
    console.log(response);
  }

  protected onHcaptchaError(error: any): void {
    // An error occured during the verification process.
    console.log(error);
  }

  async onResendOtp(): Promise<void> {
    this.loading = true;
    try {
      await firstValueFrom(this.ticketSlotService.resendOtpTransferTicketSlot());
    } catch (error) {
      const message = error instanceof HttpErrorResponse ? serverErrorText(error) : '';
      this.otpError.next(message || 'Gửi lại OTP thất bại');
    } finally {
      this.loading = false;
    }
  }

  async onInputOtp(otp: string): Promise<void> {
    if (!otp) return;
    this.loading = true;
    try {
      await firstValueFrom(this.ticketSlotService.verifyOtpTransferTicketSlot(this.ticketSlot.id, otp));
      const request: TransferTicketSlotRequest = {
        slotId: this.ticketSlot.id,
        currentStateIdx: this.state.stateIdx,
        version: this.ticketSlot.version,
        newOwnerPhone: this.formGroup.controls.newOwnerPhone.value!,
        newOwnerName: this.formGroup.controls.newOwnerName.value!,
      };
      this.ticketSlotService.transferTicketSlot(request).subscribe({
        next: () => {
          this.step = 'success';
          this.transferSuccess.emit();
        },
        error: (resp: HttpErrorResponse) => {
          this.errorMessage = (resp.error as string) ?? 'Không tặng được vé';
          console.error(resp);
          this.step = 'error';
          this.startCountdown();
        },
      });
    } catch (error: any) {
      if (error instanceof HttpErrorResponse) {
        const message: string = (error.error as string) ?? 'Xác thực OTP thất bại';
        this.otpError.next(message);
      } else if (error instanceof Error) {
        this.otpError.next(error.message ?? 'Xác thực OTP thất bại');
      } else {
        this.otpError.next('Xác thực OTP thất bại');
      }
    } finally {
      this.loading = false;
    }
  }
}
