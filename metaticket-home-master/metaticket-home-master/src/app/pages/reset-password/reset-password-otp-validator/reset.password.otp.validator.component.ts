import { Component, ViewChild } from '@angular/core';
import { LayoutComponent, OtpComponent, PopupErrorComponent } from 'src/app/lib/components';
import { UserService } from 'src/app/lib/services';
import { Router } from '@angular/router';
import { routeName } from 'src/app/lib/constants';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { serverErrorText } from 'src/app/lib/utils';

@Component({
  selector: 'app-reset-password-otp-validator',
  templateUrl: './reset.password.otp.validator.component.html',
  standalone: true,
  imports: [OtpComponent, LayoutComponent, PopupErrorComponent],
})
export class ResetPasswordOtpValidatorComponent {
  protected otpError: BehaviorSubject<string> = new BehaviorSubject<string>('');
  protected loading = false;

  @ViewChild('popupError') popupError!: PopupErrorComponent;

  private readonly phone: string = '';

  constructor(
    private router: Router,
    private userService: UserService,
  ) {
    const navigation = this.router.getCurrentNavigation();
    if (navigation?.extras?.state) {
      this.phone = navigation.extras.state['phone'] as string;
    }
    if (!this.phone) {
      this.router.navigate([routeName.resetPassword]);
    }
  }

  async onResendOtp(): Promise<void> {
    this.loading = true;
    try {
      await firstValueFrom(this.userService.resendOtpResetPassword());
    } catch (error) {
      const message = error instanceof HttpErrorResponse ? serverErrorText(error) : '';
      await this.popupError.open(message || 'Gửi lại OTP thất bại');
    } finally {
      this.loading = false;
    }
  }

  async onInputOtp(otp: string): Promise<void> {
    if (!otp) return;

    try {
      this.loading = true;
      await firstValueFrom(this.userService.verifyOtpResetPassword(this.phone, otp));
      await this.router.navigate([routeName.resetPasswordNewPass], {
        state: { phone: this.phone, otp: otp },
      });
    } catch (error: any) {
      if (error instanceof HttpErrorResponse) {
        const message: string = (error.error as string) ?? 'Xác thực OTP thất bại';
        this.otpError.next(message ?? 'Xác thực OTP thất bại');
        if (error.status != 410) {
          // OTP Gone | không còn hiệu lực
          await this.popupError.open(message);
          await this.router.navigate([routeName.resetPassword]);
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
