import { Component, ViewChild } from '@angular/core';
import {
  ButtonComponent,
  FormInputComponent,
  LayoutComponent,
  OtpComponent,
  PopupErrorComponent,
} from 'src/app/lib/components';
import { UserService } from 'src/app/lib/services';
import { Router } from '@angular/router';
import { routeName } from 'src/app/lib/constants';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { NgIf } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { serverErrorText, validationRegex, validators } from 'src/app/lib/utils';

@Component({
  selector: 'app-register-otp-validator',
  templateUrl: './register.otp.validator.component.html',
  standalone: true,
  imports: [
    OtpComponent,
    LayoutComponent,
    PopupErrorComponent,
    NgIf,
    FormInputComponent,
    ButtonComponent,
    ReactiveFormsModule,
  ],
})
export class RegisterOtpValidatorComponent {
  protected otpError: BehaviorSubject<string> = new BehaviorSubject<string>('');
  protected loading = false;
  protected step: 'PHONE_OTP' | 'EMAIL_INPUT' | 'EMAIL_OTP' = 'PHONE_OTP';
  protected emailControl = new FormControl('', [
    validators.required('Vui lòng nhập email'),
    validators.regExp('valid', validationRegex.email, 'Vui lòng nhập đúng định dạng email'),
  ]);

  @ViewChild('popupError') popupError!: PopupErrorComponent;

  private readonly phone: string = '';
  private readonly password: string = '';
  // Fail-safe: mặc định bắt buộc xác thực email nếu thiếu state (ví dụ điều hướng trực tiếp)
  private readonly emailVerificationEnabled: boolean = true;

  constructor(
    private router: Router,
    private userService: UserService,
  ) {
    const navigation = this.router.getCurrentNavigation();
    if (navigation?.extras?.state) {
      this.phone = navigation.extras.state['phone'] as string;
      this.password = navigation.extras.state['password'] as string;
      this.emailVerificationEnabled = (navigation.extras.state['emailVerificationEnabled'] as boolean) ?? true;
    }
    if (!this.phone || !this.password) {
      this.router.navigate([routeName.register]);
    }
  }

  async onResendOtp(): Promise<void> {
    this.loading = true;
    try {
      await firstValueFrom(this.userService.resendOtpRegisterUser());
    } catch (error) {
      const message = error instanceof HttpErrorResponse ? serverErrorText(error) : '';
      await this.popupError.open(message || 'Gửi lại OTP thất bại');
    } finally {
      this.loading = false;
    }
  }

  async onSendEmail(): Promise<void> {
    if (this.emailControl.invalid) {
      this.emailControl.markAsDirty();
      this.emailControl.updateValueAndValidity();
      return;
    }
    this.loading = true;
    try {
      await firstValueFrom(this.userService.sendOtpRegisterUser(this.phone, this.emailControl.value!));
      this.step = 'EMAIL_OTP';
      this.otpError.next('');
    } catch (error: any) {
      if (error instanceof HttpErrorResponse) {
        const message: string = (error.error as string) ?? 'Gửi OTP thất bại';
        await this.popupError.open(message);
      } else if (error instanceof Error) {
        await this.popupError.open(error.message ?? 'Gửi OTP thất bại');
      } else {
        await this.popupError.open('Gửi OTP thất bại');
      }
    } finally {
      this.loading = false;
    }
  }

  async onInputOtp(otp: string): Promise<void> {
    if (!otp) return;
    this.loading = true;
    try {
      if (this.step === 'PHONE_OTP') {
        await firstValueFrom(this.userService.verifyOtpRegisterUser(this.phone, otp));
        if (!this.emailVerificationEnabled) {
          await this.router.navigate([routeName.registerUserInfo], {
            state: { phone: this.phone, password: this.password, otp: otp },
          });
          return;
        }
        this.step = 'EMAIL_INPUT';
        this.otpError.next('');
      } else {
        // EMAIL_OTP
        await firstValueFrom(this.userService.verifyOtpRegisterUser(this.phone, otp, this.emailControl.value!));
        await this.router.navigate([routeName.registerUserInfo], {
          state: { phone: this.phone, password: this.password, otp: otp, email: this.emailControl.value! },
        });
      }
    } catch (error: any) {
      if (error instanceof HttpErrorResponse) {
        const message: string = (error.error as string) ?? 'Xác thực OTP thất bại';
        this.otpError.next(message ?? 'Xác thực OTP thất bại');
        if (error.status != 410) {
          // OTP Gone | không còn hiệu lực
          if (this.step === 'PHONE_OTP') {
            // Only redirect to register if Phone OTP session is completely gone/invalid
            // But here we might just show error.
            // If error 400 (Bad Request), maybe session expired.
            // Let's keep original logic for Phone OTP, but be careful for Email OTP
          }
          if (error.status === 400 && this.step === 'PHONE_OTP') {
            await this.popupError.open(message);
            await this.router.navigate([routeName.register]);
          } else {
            // Just show error for other cases
          }
        }
      } else if (error instanceof Error) {
        await this.popupError.open(error.message ?? 'Xác thực OTP thất bại');
      } else {
        await this.popupError.open('Xác thực OTP thất bại');
      }
    } finally {
      this.loading = false;
    }
  }
}
