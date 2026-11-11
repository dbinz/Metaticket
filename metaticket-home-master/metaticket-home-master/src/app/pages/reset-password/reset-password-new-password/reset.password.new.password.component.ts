import { Component, OnInit, ViewChild } from '@angular/core';
import {
  ButtonComponent,
  FormInputComponent,
  LayoutComponent,
  PopupBasicComponent,
  PopupErrorComponent,
} from 'src/app/lib/components';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { NzFormDirective } from 'ng-zorro-antd/form';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserService } from 'src/app/lib/services';
import { routeName } from 'src/app/lib/constants';
import { validators } from 'src/app/lib/utils';
import { firstValueFrom } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-reset-password-new-password',
  templateUrl: './reset.password.new.password.component.html',
  standalone: true,
  imports: [
    LayoutComponent,
    PopupErrorComponent,
    NzTypographyComponent,
    NzFormDirective,
    ReactiveFormsModule,
    FormInputComponent,
    ButtonComponent,
    PopupBasicComponent,
    NgIf,
  ],
})
export class ResetPasswordNewPasswordComponent implements OnInit {
  protected readonly routeName = routeName;
  public formGroup!: FormGroup<{
    phoneNumber: FormControl<string | null>;
    password: FormControl<string | null>;
    confirmationPassword: FormControl<string | null>;
  }>;

  @ViewChild('popupBasic') popupBasic!: PopupBasicComponent;
  @ViewChild('popupError') popupError!: PopupErrorComponent;

  protected loading: boolean = false;

  private readonly phone: string = '';
  private readonly otp: string = '';

  constructor(
    private router: Router,
    private _fb: FormBuilder,
    private userService: UserService,
  ) {
    const navigation = this.router.getCurrentNavigation();
    if (navigation?.extras?.state) {
      this.phone = navigation.extras.state['phone'] as string;
      this.otp = navigation.extras.state['otp'] as string;
    }
    if (!this.phone || !this.otp) {
      this.router.navigate([routeName.resetPassword]);
      throw new Error('Missing phone number or password or otp');
    }
  }

  private _initialForm(): void {
    this.formGroup = this._fb.group({
      phoneNumber: [this.phone],
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
    });
  }

  ngOnInit(): void {
    this._initialForm();
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
      await firstValueFrom(
        this.userService.resetPassword(this.phone, this.formGroup.controls.password.value!, this.otp),
      );
    } catch (e: unknown) {
      if (e instanceof HttpErrorResponse) {
        const message: string = (e.error as string) ?? 'Thay đổi mật khẩu thất bại';
        await this.popupError.open(message);
      } else if (e instanceof Error) {
        await this.popupError.open(e.message ?? 'Thay đổi mật khẩu thất bại');
      } else {
        await this.popupError.open('Lỗi không xác định');
      }
      this.loading = false;
      await this.router.navigate([routeName.resetPassword]);
      return;
    }

    await this.popupBasic.open('Đổi mật khẩu thành công');
    this.loading = false;
    await this.router.navigate([routeName.login]);
  }
}
