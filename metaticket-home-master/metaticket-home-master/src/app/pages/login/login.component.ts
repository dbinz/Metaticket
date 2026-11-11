import { Component, OnInit, ViewChild } from '@angular/core';
import { ButtonComponent, FormInputComponent, LayoutComponent, PopupErrorComponent } from 'src/app/lib/components';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NzFormModule } from 'ng-zorro-antd/form';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { routeName } from 'src/app/lib/constants';
import { AuthService } from 'src/app/lib/services';
import { RecaptchaComponent, RecaptchaModule } from 'ng-recaptcha';
import { validationRegex, validators } from 'src/app/lib/utils';
import { firstValueFrom } from 'rxjs';
import { NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { CaptchaSettingResponse } from 'src/app/lib/entities';
import { NgHcaptchaComponent, NgHcaptchaModule } from 'ng-hcaptcha';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    LayoutComponent,
    NzTypographyModule,
    ButtonComponent,
    RouterLink,
    NzFormModule,
    ReactiveFormsModule,
    FormInputComponent,
    RecaptchaModule,
    PopupErrorComponent,
    NgIf,
    NgHcaptchaModule,
  ],
  templateUrl: './login.component.html',
})
export class LoginComponent implements OnInit {
  protected readonly routeName = routeName;
  protected formGroup!: FormGroup<{
    phoneNumber: FormControl<string | null>;
    password: FormControl<string | null>;
    captchaToken: FormControl<string | null>;
  }>;

  @ViewChild('recaptchaRef') recaptchaRef: RecaptchaComponent | undefined;
  @ViewChild('hcaptchaRef') hcaptchaRef: NgHcaptchaComponent | undefined;
  @ViewChild('popupError') popupError!: PopupErrorComponent;

  protected captchaSetting!: CaptchaSettingResponse;
  protected loading: boolean = false;
  private returnUrl: string = routeName.root;

  constructor(
    private _fb: FormBuilder,
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router,
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
      password: ['', Validators.compose([validators.required('Vui lòng nhập mật khẩu')])],
      captchaToken: [
        '',
        Validators.compose(this.captchaSetting.enable ? [validators.required('Chưa xác nhận bạn là con người')] : []),
      ],
    });
  }

  async ngOnInit(): Promise<void> {
    this.route.queryParams.subscribe((params) => {
      this.returnUrl = (params['returnUrl'] as string) || routeName.root;
    });
    this.loading = true;
    try {
      this.captchaSetting = await firstValueFrom(this.authService.getCaptchaSetting());
      this._initialForm();
    } catch (error) {
      console.error('Failed to load captcha settings', error);
      this._initialForm();
      await this.popupError.open('Không thể tải được cài đặt captcha. Vui lòng thử lại sau.');
    } finally {
      this.loading = false;
    }
  }

  resolvedCaptcha(captchaResponse: string | null): void {
    this.formGroup.controls.captchaToken.setValue(captchaResponse);
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
      const loginResp = await firstValueFrom(
        this.authService.login({
          phone: this.formGroup.controls.phoneNumber.value!,
          password: this.formGroup.controls.password.value!,
          captchaToken: this.formGroup.controls.captchaToken.value!,
        }),
      );
      if (loginResp.token) {
        this.loading = false;
        await this.router.navigateByUrl(this.returnUrl);
        return;
      }
      await this.popupError.open(loginResp.message ?? 'Đăng nhập không thành công');
      this.resetCaptcha();
      this.loading = false;
    } catch (error: any) {
      // console.log(error);
      if (error instanceof HttpErrorResponse) {
        const message: string = (error.error as string) ?? 'Đăng nhập không thành công';
        await this.popupError.open(message);
      } else if (error instanceof Error) {
        await this.popupError.open(error.message);
      } else {
        await this.popupError.open('Đăng nhập không thành công');
      }
      this.resetCaptcha();
      this.loading = false;
      return;
    }
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
