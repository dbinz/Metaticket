import { Component, OnInit, ViewChild } from '@angular/core';
import {
  ButtonComponent,
  FormInputComponent,
  LayoutComponent,
  NavbarComponent,
  OtpComponent,
  PopupBasicComponent,
  PopupErrorComponent,
} from 'src/app/lib/components';
import { AsyncPipe, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { splitNormalizedPhoneNumber } from 'src/app/lib/pipes';
import { UserService } from 'src/app/lib/services';
import { UserProfileModel } from 'src/app/lib/entities';
import { BehaviorSubject, firstValueFrom } from 'rxjs';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { serverErrorText, validationRegex, validators } from 'src/app/lib/utils';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-account',
  standalone: true,
  templateUrl: './account.component.html',
  imports: [
    LayoutComponent,
    NavbarComponent,
    RouterLink,
    NgIf,
    AsyncPipe,
    FormInputComponent,
    ButtonComponent,
    FormsModule,
    ReactiveFormsModule,
    OtpComponent,
    PopupBasicComponent,
    PopupErrorComponent,
  ],
})
export class AccountComponent implements OnInit {
  protected account: BehaviorSubject<UserProfileModel> = new BehaviorSubject<UserProfileModel>({} as UserProfileModel);
  protected formGroup!: FormGroup<{
    name: FormControl<string | null>;
    email: FormControl<string | null>;
  }>;
  protected isEditing = false;
  protected loading = false;
  protected showOtpModal = false;
  protected otpError: BehaviorSubject<string> = new BehaviorSubject<string>('');
  protected otpLoading = false;

  @ViewChild('popupBasic') popupBasic!: PopupBasicComponent;
  @ViewChild('popupError') popupError!: PopupErrorComponent;

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.userService.getUserProfile().subscribe((p) => {
      this.account.next(p);
      this.initForm(p);
    });
  }

  private initForm(user: UserProfileModel): void {
    this.formGroup = new FormGroup({
      name: new FormControl(user.name, [
        validators.required('Vui lòng nhập tên'),
        validators.regExp('valid', validationRegex.fullName, 'Họ tên không hợp lệ'),
      ]),
      email: new FormControl(user.email, [
        validators.required('Vui lòng nhập email'),
        validators.regExp('valid', validationRegex.email, 'Email không hợp lệ'),
      ]),
    });
    this.isEditing = false;
    // this.formGroup.disable();
  }

  protected editInfo(): void {
    this.isEditing = true;
    // this.formGroup.enable();
  }

  protected cancelEdit(): void {
    this.isEditing = false;
    // this.formGroup.disable();
    this.formGroup.reset({
      name: this.account.value.name,
      email: this.account.value.email,
    });
  }

  protected async submit(): Promise<void> {
    if (this.formGroup.invalid) {
      this.formGroup.markAllAsTouched();
      return;
    }

    const newName = this.formGroup.controls.name.value!;
    const newEmail = this.formGroup.controls.email.value!;
    const currentEmail = this.account.value.email;

    if (newEmail !== currentEmail) {
      // Email changed -> Init session and send OTP
      this.loading = true;
      try {
        await firstValueFrom(this.userService.initSessionUpdateProfile());
        await firstValueFrom(this.userService.sendOtpUpdateProfile(newEmail));
        this.showOtpModal = true;
      } catch (error: any) {
        // API cũ có thể trả lỗi nghiệp vụ trong HTTP 200; không hiển thị trang lỗi HTML/5xx của gateway.
        let msg = 'Không thể gửi mã xác thực. Vui lòng thử lại.';
        if (error instanceof HttpErrorResponse) {
          msg = serverErrorText(error) || msg;
        } else if (error instanceof Error && error.message) {
          msg = error.message;
        }
        await this.popupError.open(msg);
      } finally {
        this.loading = false;
      }
    } else {
      // Name only changed
      this.loading = true;
      try {
        await firstValueFrom(this.userService.updateProfile(newName, newEmail));
        this.account.next({ ...this.account.value, name: newName });
        this.isEditing = false;
        // this.formGroup.disable();
        await this.popupBasic.open('Cập nhật thông tin thành công');
      } catch (error: any) {
        const message = error instanceof HttpErrorResponse ? serverErrorText(error) : '';
        await this.popupError.open(message || 'Cập nhật thất bại');
      } finally {
        this.loading = false;
      }
    }
  }

  protected async onResendOtp(): Promise<void> {
    this.otpLoading = true;
    try {
      await firstValueFrom(this.userService.resendOtpUpdateProfile());
    } catch (error: any) {
      let msg = 'Gửi lại OTP thất bại';
      if (error instanceof HttpErrorResponse) {
        msg = serverErrorText(error) || msg;
      }
      this.otpError.next(msg);
    } finally {
      this.otpLoading = false;
    }
  }

  protected async onVerifyOtp(otp: string): Promise<void> {
    this.otpLoading = true;
    const newName = this.formGroup.controls.name.value!;
    const newEmail = this.formGroup.controls.email.value!;

    try {
      // Verify first
      await firstValueFrom(this.userService.verifyOtpUpdateProfile(newEmail, otp));
      // Then update
      await firstValueFrom(this.userService.updateProfile(newName, newEmail, otp));

      this.showOtpModal = false;
      this.isEditing = false;
      // this.formGroup.disable();
      this.account.next({ ...this.account.value, name: newName, email: newEmail });
      await this.popupBasic.open('Cập nhật thông tin thành công');
    } catch (error: any) {
      if (error instanceof HttpErrorResponse) {
        this.otpError.next(serverErrorText(error) || 'Xác thực thất bại');
      } else {
        this.otpError.next('Xác thực thất bại');
      }
    } finally {
      this.otpLoading = false;
    }
  }

  protected closeOtpModal(): void {
    this.showOtpModal = false;
    this.otpError.next('');
  }

  protected readonly splitNormalizedPhoneNumber = splitNormalizedPhoneNumber;
}
