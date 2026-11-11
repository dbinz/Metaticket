import { Component, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from 'src/app/lib/services';
import { RecaptchaComponent, RecaptchaModule } from 'ng-recaptcha';
import { routeName } from 'src/app/lib/constants';
import { validationRegex, validators } from 'src/app/lib/utils';
import { firstValueFrom } from 'rxjs';
import { ButtonComponent, FormInputComponent, LayoutComponent, PopupErrorComponent } from 'src/app/lib/components';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { NzFormDirective } from 'ng-zorro-antd/form';
import { NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { CaptchaSettingResponse } from 'src/app/lib/entities';
import { NgHcaptchaComponent, NgHcaptchaModule } from 'ng-hcaptcha';

@Component({
  selector: 'app-reset-password',
  templateUrl: './reset.password.component.html',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    LayoutComponent,
    FormInputComponent,
    RecaptchaModule,
    ButtonComponent,
    NzTypographyComponent,
    NzFormDirective,
    NgIf,
    PopupErrorComponent,
    NgHcaptchaModule,
  ],
})
export class ResetPasswordComponent implements OnInit {
  protected readonly routeName = routeName;
  public formGroup!: FormGroup<{
    phoneNumber: FormControl<string | null>;
    captchaToken: FormControl<string | null>;
  }>;

  @ViewChild('recaptchaRef') recaptchaRef: RecaptchaComponent | undefined;
  @ViewChild('hcaptchaRef') hcaptchaRef: NgHcaptchaComponent | undefined;
  @ViewChild('popupError') popupError!: PopupErrorComponent;

  protected captchaSetting!: CaptchaSettingResponse;
  protected loading: boolean = false;

  constructor(
    private router: Router,
    private _fb: FormBuilder,
    private userService: UserService,
  ) {}

  private _initialForm(): void {
    this.formGroup = this._fb.group({
      phoneNumber: [
        '',
        Validators.compose([
          validators.required('Vui lòng nhập số điện thoại'),
          validators.regExp('valid', validationRegex.phoneNumber, 'Số điện thoại không hợp lệ'),
        ]),
      ],
      captchaToken: [
        '',
        Validators.compose(this.captchaSetting.enable ? [validators.required('Chưa xác nhận bạn là con người')] : []),
      ],
    });
  }

  async ngOnInit(): Promise<void> {
    this.loading = true;
    try {
      this.captchaSetting = await firstValueFrom(this.userService.getCaptchaSetting());
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

    try {
      this.loading = true;

      const captchaToken = this.captchaSetting.enable ? this.formGroup.controls.captchaToken.value! : undefined;
      const phone = this.formGroup.controls.phoneNumber.value!;
      await firstValueFrom(this.userService.initSessionResetPassword(captchaToken));
      await firstValueFrom(this.userService.sendOtpResetPassword(phone));

      await this.router.navigate([routeName.resetPasswordVerifyOtp], {
        state: { phone: phone },
      });
    } catch (error: any) {
      if (error instanceof HttpErrorResponse) {
        const message: string = (error.error as string) ?? 'Thay đổi mật khẩu thất bại';
        await this.popupError.open(message);
      } else if (error instanceof Error) {
        await this.popupError.open(error.message ?? 'Thay đổi mật khẩu thất bại');
      } else {
        await this.popupError.open('Thay đổi mật khẩu thất bại');
      }
    } finally {
      this.resetCaptcha();
      this.loading = false;
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
}
