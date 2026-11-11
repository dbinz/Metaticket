import { Component, OnInit, ViewChild } from '@angular/core';
import {
  ButtonComponent,
  FormCheckboxComponent,
  FormInputComponent,
  LayoutComponent,
  PopupErrorComponent,
} from 'src/app/lib/components';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { NzFormModule } from 'ng-zorro-antd/form';
import { Router, RouterLink } from '@angular/router';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { routeName } from 'src/app/lib/constants';
import { RecaptchaComponent, RecaptchaModule } from 'ng-recaptcha';
import { UserService } from 'src/app/lib/services';
import { firstValueFrom } from 'rxjs';
import { validationRegex, validators } from 'src/app/lib/utils';
import { NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { CaptchaSettingResponse, EmailVerificationSettingResponse } from 'src/app/lib/entities';
import { NgHcaptchaComponent, NgHcaptchaModule } from 'ng-hcaptcha';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    ButtonComponent,
    LayoutComponent,
    NzTypographyComponent,
    FormInputComponent,
    FormsModule,
    NzFormModule,
    ReactiveFormsModule,
    RouterLink,
    NzCheckboxModule,
    RecaptchaModule,
    FormCheckboxComponent,
    PopupErrorComponent,
    NgIf,
    NgHcaptchaModule,
  ],
  templateUrl: './register.component.html',
})
export class RegisterComponent implements OnInit {
  protected readonly routeName = routeName;
  public formGroup!: FormGroup<{
    phoneNumber: FormControl<string | null>;
    password: FormControl<string | null>;
    confirmationPassword: FormControl<string | null>;
    captchaToken: FormControl<string | null>;
    agreePolicy: FormControl<boolean | null>;
  }>;

  @ViewChild('recaptchaRef') recaptchaRef: RecaptchaComponent | undefined;
  @ViewChild('hcaptchaRef') hcaptchaRef: NgHcaptchaComponent | undefined;
  @ViewChild('popupError') popupError!: PopupErrorComponent;

  protected captchaSetting!: CaptchaSettingResponse;
  // Fail-safe: mặc định bắt buộc xác thực email cho tới khi tải xong cấu hình từ server
  protected emailVerificationEnabled = true;
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
      password: [
        '',
        Validators.compose([
          validators.required('Vui lòng nhập mật khẩu'),
          validators.regExp('valid1', /^.{8,16}$/i, 'Mật khẩu của bạn phải dài từ 8 đến 16 ký tự'),
          validators.regExp('valid2', /^(?=.*[A-Z]).*$/, 'Mật khẩu của bạn phải chứa ít nhất một ký tự viết hoa'),
          validators.regExp('valid3', /^(?=.*[a-z]).*$/, 'Mật khẩu của bạn phải chứa ít nhất một ký tự viết thường'),
          validators.regExp('valid4', /^(?=.*[0-9]).*$/, 'Mật khẩu của bạn phải chứa ít nhất một ký tự số'),
          validators.regExp('valid5', /^(?=.*[\W_]).*$/, 'Mật khẩu của bạn phải chứa ít nhất một ký tự đặc biệt'),
        ]),
      ],
      confirmationPassword: [
        '',
        Validators.compose([
          validators.required('Vui lòng nhập lại mật khẩu'),
          validators.match('math1', 'password', 'Mật khẩu không khớp'),
        ]),
      ],
      captchaToken: [
        '',
        Validators.compose(this.captchaSetting.enable ? [validators.required('Chưa xác nhận bạn là con người')] : []),
      ],
      agreePolicy: [false],
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

    try {
      const setting: EmailVerificationSettingResponse = await firstValueFrom(
        this.userService.getEmailVerificationSetting(),
      );
      this.emailVerificationEnabled = setting.enable;
    } catch (error) {
      // Lỗi tải cấu hình không nên chặn luồng đăng ký - giữ hành vi bắt buộc xác thực email như mặc định.
      console.error('Failed to load email verification setting', error);
    }
  }

  protected async submit(): Promise<void> {
    if (this.formGroup.invalid || !this.formGroup.controls.agreePolicy.value) {
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

      // Xác minh Captcha ngay tại bước khởi tạo phiên để giảm nguy cơ spam tạo session
      const captchaToken = this.captchaSetting.enable ? this.formGroup.controls.captchaToken.value! : undefined;
      await firstValueFrom(this.userService.initSessionRegisterUser(captchaToken));
      const phone = this.formGroup.controls.phoneNumber.value!;
      await firstValueFrom(this.userService.sendOtpRegisterUser(phone));

      const password = this.formGroup.controls.password.value!;
      await this.router.navigate([routeName.registerVerifyOtp], {
        state: { phone: phone, password: password, emailVerificationEnabled: this.emailVerificationEnabled },
      });
    } catch (error: any) {
      if (error instanceof HttpErrorResponse) {
        const message: string = (error.error as string) ?? 'Đăng ký tài khoản thất bại';
        await this.popupError.open(message);
      } else if (error instanceof Error) {
        await this.popupError.open(error.message ?? 'Đăng ký tài khoản thất bại');
      } else {
        await this.popupError.open('Đăng ký tài khoản thất bại');
      }
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
