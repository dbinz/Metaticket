import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { ButtonComponent, FormInputComponent } from 'src/app/lib/components';
import { routeName } from 'src/app/lib/constants';
import { normalizePhoneNumber, validationRegex, validators } from 'src/app/lib/utils';
import { AuthService, NotificationService } from 'src/app/lib/services';
import { AccessoryCartQuery, AccessoryCheckoutQuery, AccessoryCheckoutService } from 'src/app/lib/stores';
import { UserProfileModel } from 'src/app/lib/entities';

/**
 * Step 2 "Điền thông tin" — collects recipient name, phone, and email only.
 * On submit, values are saved to the checkout store and the flow advances to
 * "Thông tin nhận hàng" (checkout API runs later from that step).
 */
@Component({
  selector: 'accessory-information',
  standalone: true,
  imports: [ReactiveFormsModule, NzTypographyModule, NzDividerComponent, FormInputComponent, ButtonComponent],
  templateUrl: './accessory-information.component.html',
})
export class AccessoryInformationComponent implements OnInit, OnDestroy {
  purchaseFormGroup: FormGroup<{
    name: FormControl<string | null>;
    phone: FormControl<string | null>;
    email: FormControl<string | null>;
  }>;

  private _userProfileSubscription?: Subscription;

  constructor(
    private _fb: FormBuilder,
    private _router: Router,
    private _notification: NotificationService,
    private _authService: AuthService,
    private _cartQuery: AccessoryCartQuery,
    private _checkoutQuery: AccessoryCheckoutQuery,
    private _checkoutService: AccessoryCheckoutService,
  ) {
    this.purchaseFormGroup = this._fb.group({
      name: [
        '',
        Validators.compose([
          validators.required('Họ và tên không được để trống'),
          validators.regExp('valid', validationRegex.fullName, 'Họ và tên không hợp lệ'),
        ]),
      ],
      phone: [
        '',
        Validators.compose([
          validators.required('Số điện thoại không được để trống'),
          validators.regExp('valid', validationRegex.phoneNumber, 'Số điện thoại không hợp lệ'),
        ]),
      ],
      email: [
        '',
        Validators.compose([
          validators.required('Email không được để trống'),
          validators.regExp('valid', validationRegex.email, 'Email không hợp lệ'),
        ]),
      ],
    });
  }

  ngOnInit(): void {
    const state = this._checkoutQuery.getValue();
    this.purchaseFormGroup.patchValue({
      name: state.customerName || '',
      phone: state.customerPhone || '',
      email: state.customerEmail || '',
    });

    this._userProfileSubscription = this._authService.userProfile$.subscribe({
      next: (userProfile) => {
        if (userProfile) {
          this._fillFromProfile(userProfile);
        }
      },
      error: (error) => console.error('get user profile error', error),
    });
  }

  ngOnDestroy(): void {
    this._userProfileSubscription?.unsubscribe();
  }

  private _fillFromProfile(userProfile: UserProfileModel): void {
    const controls = this.purchaseFormGroup.controls;
    if (!controls.name.value && userProfile.name) {
      controls.name.setValue(userProfile.name);
    }
    if (!controls.phone.value && userProfile.phone) {
      controls.phone.setValue(normalizePhoneNumber(userProfile.phone));
    }
    if (!controls.email.value && userProfile.email) {
      controls.email.setValue(userProfile.email);
    }
  }

  onGoBack(): void {
    const accessoryShowId = this._cartQuery.getValue().accessoryShowId;
    const navigation = accessoryShowId
      ? this._router.navigate(['/', routeName.productDetail, accessoryShowId], { queryParams: { openCart: 1 } })
      : this._router.navigate(['/', routeName.product]);
    navigation.then(() => {
      setTimeout(() => window.scrollTo(0, 0), 100);
    });
  }

  /** Validate and persist contact info, then open the fulfillment step (no checkout API). */
  onSubmit(): void {
    if (this.purchaseFormGroup.invalid) {
      Object.values(this.purchaseFormGroup.controls).forEach((control) => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });
      return;
    }

    const cart = this._cartQuery.getValue();
    if (cart.lines.length === 0) {
      this._notification.showErrorNotification('Giỏ hàng đang trống. Vui lòng thêm sản phẩm.');
      return;
    }

    const form = this.purchaseFormGroup.value;
    this._checkoutService.saveCustomerInfo({
      customerName: form.name ?? '',
      customerPhone: form.phone ?? '',
      customerEmail: form.email ?? '',
    });
  }
}
