import { Component, OnInit, ViewChild } from '@angular/core';
import {
  ButtonComponent,
  FormInputComponent,
  LayoutComponent,
  PopupBasicComponent,
  PopupErrorComponent,
} from 'src/app/lib/components';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { RecaptchaModule } from 'ng-recaptcha';
import { routeName } from 'src/app/lib/constants';
import { Router } from '@angular/router';
import { UserService } from 'src/app/lib/services';
import { validationRegex, validators } from 'src/app/lib/utils';
import { firstValueFrom } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-register-user-info',
  standalone: true,
  imports: [
    ButtonComponent,
    LayoutComponent,
    NzTypographyComponent,
    FormInputComponent,
    FormsModule,
    NzFormModule,
    ReactiveFormsModule,
    NzCheckboxModule,
    RecaptchaModule,
    PopupBasicComponent,
    PopupErrorComponent,
    NgIf,
  ],
  templateUrl: './register.user.info.component.html',
})
export class RegisterUserInfoComponent implements OnInit {
  protected readonly routeName = routeName;
  protected formGroup!: FormGroup<{
    phoneNumber: FormControl<string | null>;
    name: FormControl<string | null>;
    email: FormControl<string | null>;
  }>;

  protected loading = false;

  @ViewChild('popupBasic') popupBasic!: PopupBasicComponent;
  @ViewChild('popupError') popupError!: PopupErrorComponent;

  private readonly phone: string = '';
  private readonly password: string = '';
  private readonly otp: string = '';
  private readonly email: string = '';

  constructor(
    private readonly _fb: FormBuilder,
    private readonly router: Router,
    private readonly userService: UserService,
  ) {
    const navigation = this.router.getCurrentNavigation();
    if (navigation?.extras?.state) {
      this.phone = navigation.extras.state['phone'] as string;
      this.password = navigation.extras.state['password'] as string;
      this.otp = navigation.extras.state['otp'] as string;
      this.email = navigation.extras.state['email'] as string;
    }
    if (!this.phone || !this.password || !this.otp) {
      this.router.navigate([routeName.register]);
      throw new Error('Missing phone number or password or otp');
    }
  }

  private _initialForm(): void {
    this.formGroup = this._fb.group({
      phoneNumber: [this.phone],
      email: [
        { value: this.email, disabled: !!this.email },
        Validators.compose([
          validators.required('Vui lòng nhập email'),
          validators.regExp('valid', validationRegex.email, 'Vui lòng nhập đúng định dạng email'),
        ]),
      ],
      name: [
        '',
        Validators.compose([
          validators.required('Vui lòng nhập họ tên'),
          validators.regExp('valid', validationRegex.fullName, 'Họ tên không hợp lệ'),
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

    this.loading = true;

    const name = this.formGroup.controls['name'].value!;
    const email = this.formGroup.controls['email'].value!;
    try {
      await firstValueFrom(
        this.userService.createUser({
          phone: this.phone,
          password: this.password,
          otp: this.otp,
          name: name,
          email: email,
        }),
      );
      await this.popupBasic.open('Đăng ký thành công');
      await this.router.navigate([routeName.login]);
    } catch (e: unknown) {
      if (e instanceof HttpErrorResponse) {
        const message: string = (e.error as string) ?? 'Đăng ký tài khoản thất bại';
        await this.popupError.open(message);
      } else if (e instanceof Error) {
        await this.popupError.open(e.message ?? 'Đăng ký tài khoản thất bại');
      } else {
        await this.popupError.open('Lỗi không xác định');
      }
      await this.router.navigate([routeName.register]);
    } finally {
      this.loading = false;
    }
  }
}
