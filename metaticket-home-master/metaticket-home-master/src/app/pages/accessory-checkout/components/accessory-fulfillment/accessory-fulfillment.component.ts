import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { Subscription } from 'rxjs';
import {
  ButtonComponent,
  FormInputComponent,
  FormSelectComponent,
  ImageComponent,
  LoadingComponent,
} from 'src/app/lib/components';
import { appConstants } from 'src/app/lib/constants';
import { SelectOptionItem } from 'src/app/lib/dto';
import {
  AccessoryCheckoutRequest,
  AccessoryFulfillmentResponse,
  AccessoryPickupPointResponse,
  AccessoryShowResponse,
  AccessoryShowSessionResponse,
} from 'src/app/lib/entities';
import { AccessoryService, AuthService, NotificationService } from 'src/app/lib/services';
import {
  AccessoryCartQuery,
  AccessoryCartService,
  AccessoryCheckoutQuery,
  AccessoryCheckoutService,
} from 'src/app/lib/stores';
import { appIcons } from 'src/app/lib/theme';
import { validators } from 'src/app/lib/utils';

type FulfillmentTypeUi = 'PickupAtShow' | 'MetaTicketPickup' | 'Delivery';

/**
 * Step 3 "Thông tin nhận hàng" — modes / sessions / META pickup points from
 * `GET public/accessory/show/fetch`. Continue submits `POST accessory/checkout` and advances to payment.
 *
 * Do not import ReactiveFormsModule here: `app-form-select` / `app-form-input` take a FormControl
 * via `@Input() formControl`. Importing the reactive directive makes `[formControl]` attach
 * FormControlDirective and crash select (no ControlValueAccessor → writeValue on null).
 */
@Component({
  selector: 'accessory-fulfillment',
  standalone: true,
  imports: [
    CommonModule,
    NzTypographyModule,
    NzDividerComponent,
    FormInputComponent,
    FormSelectComponent,
    ButtonComponent,
    ImageComponent,
    LoadingComponent,
  ],
  templateUrl: './accessory-fulfillment.component.html',
})
export class AccessoryFulfillmentComponent implements OnInit, OnDestroy {
  protected readonly appIcons = appIcons;

  loadingShow = false;
  submitting = false;
  show?: AccessoryShowResponse;

  /** Province options from post-merger catalog (`appConstants.newProvinces`). */
  readonly listProvincesData: SelectOptionItem[] = appConstants.newProvinces.map((item) => ({
    name: item.name,
    value: item.name,
  }));

  /** Ward options for the currently selected province (`appConstants.newWards`). */
  listWardsData: SelectOptionItem[] = [];

  fulfillmentForm: FormGroup<{
    fulfillmentType: FormControl<FulfillmentTypeUi | null>;
    showSessionId: FormControl<string | null>;
    pickupPointId: FormControl<string | null>;
    city: FormControl<string | null>;
    ward: FormControl<string | null>;
    address: FormControl<string | null>;
    note: FormControl<string | null>;
  }>;

  private citySub?: Subscription;

  constructor(
    private _fb: FormBuilder,
    private _accessoryService: AccessoryService,
    private _authService: AuthService,
    private _notification: NotificationService,
    private _cartQuery: AccessoryCartQuery,
    private _cartService: AccessoryCartService,
    private _checkoutQuery: AccessoryCheckoutQuery,
    private _checkoutService: AccessoryCheckoutService,
  ) {
    this.fulfillmentForm = this._fb.group({
      fulfillmentType: this._fb.control<FulfillmentTypeUi>('PickupAtShow'),
      showSessionId: this._fb.control<string>(''),
      pickupPointId: this._fb.control<string>(''),
      city: this._fb.control<string>(''),
      ward: this._fb.control<string>(''),
      address: this._fb.control<string>(''),
      note: this._fb.control<string>(''),
    });
  }

  /** Loads show fulfillment settings and wires province → ward filtering. */
  ngOnInit(): void {
    const draft = this._checkoutQuery.getValue();
    this.fulfillmentForm.patchValue({
      showSessionId: draft.showSessionId || '',
      pickupPointId: draft.pickupPointId || '',
      city: draft.deliveryCity || '',
      ward: draft.deliveryWard || '',
      address: draft.deliveryAddress || '',
      note: draft.deliveryNote || '',
    });

    // Rebuild ward list when province changes; clear ward if it is no longer valid.
    this.citySub = this.fulfillmentForm.controls.city.valueChanges.subscribe((cityName) => {
      this._refreshWardsForCity(cityName, /* resetWardIfInvalid */ true);
    });
    this._refreshWardsForCity(this.fulfillmentForm.controls.city.value, /* resetWardIfInvalid */ false);

    const showId = this._cartQuery.getValue().accessoryShowId;
    if (!showId) {
      this._notification.showErrorNotification('Thiếu thông tin show trong giỏ hàng.');
      return;
    }

    this.loadingShow = true;
    this._accessoryService.getAccessoryShow(showId).subscribe({
      next: (res) => {
        this.show = res;
        this.loadingShow = false;
        this._applyEnabledModes(draft.fulfillmentType);
        this._initDefaultSession();
        this._initDefaultPickupPoint();
      },
      error: (err: unknown) => {
        this.loadingShow = false;
        console.log('fetch accessory show for fulfillment error', err);
        this._notification.showHttpErrorNotification(err as HttpErrorResponse);
      },
    });
  }

  ngOnDestroy(): void {
    this.citySub?.unsubscribe();
  }

  get fulfillment(): AccessoryFulfillmentResponse {
    return (
      this.show?.fulfillment ?? {
        enablePickupAtShow: true,
        enableMetaTicketPickup: false,
        enableDelivery: false,
        pickupAtShowSessionIds: [],
        pickupPoints: [],
      }
    );
  }

  get enablePickupAtShow(): boolean {
    return this.fulfillment.enablePickupAtShow !== false;
  }

  get enableMetaTicketPickup(): boolean {
    return !!this.fulfillment.enableMetaTicketPickup;
  }

  get enableDelivery(): boolean {
    return !!this.fulfillment.enableDelivery;
  }

  /**
   * Sessions selectable for PickupAtShow — all show sessions, optionally filtered by
   * `fulfillment.pickupAtShowSessionIds` when that list is non-empty.
   */
  get sessions(): AccessoryShowSessionResponse[] {
    const all = this.show?.sessions ?? [];
    const allowed = this.fulfillment.pickupAtShowSessionIds ?? [];
    if (!allowed.length) {
      return all;
    }
    const allowedSet = new Set(allowed);
    return all.filter((s) => allowedSet.has(s.id));
  }

  get hasSessions(): boolean {
    return this.sessions.length > 0;
  }

  get pickupPoints(): AccessoryPickupPointResponse[] {
    return this.fulfillment.pickupPoints ?? [];
  }

  get isPickupAtShow(): boolean {
    return this.fulfillmentForm.controls.fulfillmentType.value === 'PickupAtShow';
  }

  get isMetaTicketPickup(): boolean {
    return this.fulfillmentForm.controls.fulfillmentType.value === 'MetaTicketPickup';
  }

  get isDelivery(): boolean {
    return this.fulfillmentForm.controls.fulfillmentType.value === 'Delivery';
  }

  /** Whether the primary CTA can be pressed. */
  get canContinue(): boolean {
    if (this.submitting || this.loadingShow) {
      return false;
    }
    if (this.isPickupAtShow) {
      if (!this.enablePickupAtShow) {
        return false;
      }
      if (this.hasSessions) {
        return !!this.fulfillmentForm.controls.showSessionId.value;
      }
      return true;
    }
    if (this.isMetaTicketPickup) {
      return this.enableMetaTicketPickup && !!this.fulfillmentForm.controls.pickupPointId.value;
    }
    if (this.isDelivery) {
      return this.enableDelivery;
    }
    return false;
  }

  get continueTitle(): string {
    return this.submitting ? 'Đang tạo đơn...' : 'Tiếp tục';
  }

  /** Pick a mode that is enabled on the show (prefer draft if still valid). */
  private _applyEnabledModes(draftType: string): void {
    const preferred: FulfillmentTypeUi[] = [];
    if (this.enablePickupAtShow) {
      preferred.push('PickupAtShow');
    }
    if (this.enableMetaTicketPickup) {
      preferred.push('MetaTicketPickup');
    }
    if (this.enableDelivery) {
      preferred.push('Delivery');
    }
    if (!preferred.length) {
      // Defensive: BE validation requires ≥1 mode; fall back to pickup-at-show.
      preferred.push('PickupAtShow');
    }
    const isDraftOk =
      (draftType === 'PickupAtShow' || draftType === 'MetaTicketPickup' || draftType === 'Delivery') &&
      preferred.includes(draftType);
    this.fulfillmentForm.controls.fulfillmentType.setValue(isDraftOk ? draftType : preferred[0]);
    this._syncDeliveryValidators(this.fulfillmentForm.controls.fulfillmentType.value === 'Delivery');
  }

  /** Apply a default session when the show has sessions and none is selected yet. */
  private _initDefaultSession(): void {
    if (!this.hasSessions) {
      this.fulfillmentForm.controls.showSessionId.setValue('');
      return;
    }
    const current = this.fulfillmentForm.controls.showSessionId.value;
    const valid = this.sessions.find((s) => s.id === current);
    this.fulfillmentForm.controls.showSessionId.setValue(valid ? valid.id : this.sessions[0].id);
  }

  private _initDefaultPickupPoint(): void {
    if (this.pickupPoints.length === 0) {
      this.fulfillmentForm.controls.pickupPointId.setValue('');
      return;
    }
    const current = this.fulfillmentForm.controls.pickupPointId.value;
    const valid = this.pickupPoints.find((p) => p.id === current);
    this.fulfillmentForm.controls.pickupPointId.setValue(valid ? valid.id : this.pickupPoints[0].id);
  }

  /** Short label for a session card, e.g. "Hà Nội · 25/07 19:00". */
  sessionLabel(session: AccessoryShowSessionResponse): string {
    const date = new Date(session.date);
    const dd = `${date.getDate()}`.padStart(2, '0');
    const mm = `${date.getMonth() + 1}`.padStart(2, '0');
    const hh = `${date.getHours()}`.padStart(2, '0');
    const mi = `${date.getMinutes()}`.padStart(2, '0');
    return `${session.city} · ${dd}/${mm} ${hh}:${mi}`;
  }

  onFulfillmentTypeChange(type: FulfillmentTypeUi): void {
    if (type === 'PickupAtShow' && !this.enablePickupAtShow) {
      return;
    }
    if (type === 'MetaTicketPickup' && !this.enableMetaTicketPickup) {
      return;
    }
    if (type === 'Delivery' && !this.enableDelivery) {
      return;
    }
    this.fulfillmentForm.controls.fulfillmentType.setValue(type);
    this._syncDeliveryValidators(type === 'Delivery');
    if (type === 'MetaTicketPickup') {
      this._initDefaultPickupPoint();
    }
    if (type === 'PickupAtShow') {
      this._initDefaultSession();
    }
    this._persistDraft();
  }

  onSessionChange(sessionId: string): void {
    this.fulfillmentForm.controls.showSessionId.setValue(sessionId);
    const showId = this.show?.id;
    if (showId) {
      this._cartService.setSession(sessionId, showId);
    }
    this._persistDraft();
  }

  onPickupPointChange(pointId: string): void {
    this.fulfillmentForm.controls.pickupPointId.setValue(pointId);
    this._persistDraft();
  }

  onGoBack(): void {
    this._persistDraft();
    this._checkoutService.onPreviousIndex();
  }

  /** Validate selection, call checkout API, then advance to payment. */
  onContinue(): void {
    if (this.submitting) {
      return;
    }
    if (this.isPickupAtShow && this.hasSessions && !this.fulfillmentForm.controls.showSessionId.value) {
      this._notification.showErrorNotification('Vui lòng chọn buổi diễn nhận hàng.');
      return;
    }
    if (this.isMetaTicketPickup && !this.fulfillmentForm.controls.pickupPointId.value) {
      this._notification.showErrorNotification('Vui lòng chọn điểm Pickup META Ticket.');
      return;
    }
    if (this.isDelivery && !this._isDeliveryAddressComplete()) {
      this._notification.showErrorNotification('Vui lòng nhập đầy đủ địa chỉ giao hàng.');
      return;
    }
    if (!this.canContinue) {
      return;
    }

    this._persistDraft();

    const draft = this._checkoutQuery.getValue();
    const cart = this._cartQuery.getValue();
    if (!cart.lines.length) {
      this._notification.showErrorNotification('Giỏ hàng trống.');
      return;
    }
    if (!draft.customerName || !draft.customerPhone || !draft.customerEmail) {
      this._notification.showErrorNotification('Thiếu thông tin người nhận. Vui lòng quay lại bước trước.');
      return;
    }

    const type = this.fulfillmentForm.controls.fulfillmentType.value;
    if (type !== 'PickupAtShow' && type !== 'MetaTicketPickup' && type !== 'Delivery') {
      return;
    }

    const body: AccessoryCheckoutRequest = {
      lines: cart.lines.map((line) => ({
        accessoryId: line.accessoryId,
        ...(line.variantKey ? { variantKey: line.variantKey } : {}),
        quantity: line.quantity,
      })),
      customerName: draft.customerName,
      customerPhone: draft.customerPhone,
      customerEmail: draft.customerEmail,
      ...(draft.discountCode ? { discountCode: draft.discountCode } : {}),
      fulfillment: type,
    };

    if (type === 'PickupAtShow') {
      const sessionId = this.fulfillmentForm.controls.showSessionId.value?.trim();
      if (sessionId) {
        body.showSessionId = sessionId;
      }
    } else if (type === 'MetaTicketPickup') {
      body.pickupPointId = this.fulfillmentForm.controls.pickupPointId.value ?? undefined;
    } else {
      body.deliveryAddress = {
        city: (this.fulfillmentForm.controls.city.value ?? '').trim(),
        ward: (this.fulfillmentForm.controls.ward.value ?? '').trim(),
        address: (this.fulfillmentForm.controls.address.value ?? '').trim(),
        ...((this.fulfillmentForm.controls.note.value ?? '').trim()
          ? { note: (this.fulfillmentForm.controls.note.value ?? '').trim() }
          : {}),
      };
    }

    this.submitting = true;
    this._accessoryService.checkout(body).subscribe({
      next: (res) => {
        this.submitting = false;
        this._checkoutService.checkoutCompleted({
          customerName: draft.customerName,
          customerPhone: draft.customerPhone,
          customerEmail: draft.customerEmail,
          encodedOrderId: res.encodedOrderId,
          encodedPaymentId: res.encodedPaymentId,
          orderCode: res.orderCode,
          total: res.total,
          ownerUserId: this._authService.getUserId() ?? '',
        });
      },
      error: (err: HttpErrorResponse) => {
        this.submitting = false;
        let apiMessage = '';
        if (err.error && typeof err.error === 'object') {
          const msg = (err.error as { message?: unknown }).message;
          if (typeof msg === 'string') {
            apiMessage = msg.trim();
          }
        }
        // Discount failures: clear stale preview so the customer re-applies from the cart.
        if (apiMessage.includes('Mã giảm giá') || apiMessage.includes('giảm giá') || err.status === 409) {
          this._checkoutService.clearDiscount();
        }
        this._notification.showErrorNotification(
          apiMessage || 'Không tạo được đơn hàng. Vui lòng kiểm tra giỏ hàng / mã giảm giá và thử lại.',
        );
      },
    });
  }

  private _isDeliveryAddressComplete(): boolean {
    const c = this.fulfillmentForm.controls;
    return !!(c.city.value?.trim() && c.ward.value?.trim() && c.address.value?.trim());
  }

  /**
   * Filters `newWards` by the selected province's `code`.
   * When [resetWardIfInvalid] is true, clears ward if empty options or ward not in the new list.
   */
  private _refreshWardsForCity(cityName: string | null | undefined, resetWardIfInvalid: boolean): void {
    const province = appConstants.newProvinces.find((p) => p.name === (cityName || '').trim());
    const provinceCode = province?.code;
    this.listWardsData = provinceCode
      ? appConstants.newWards
          .filter((w) => w.province_code === provinceCode)
          .map((w) => ({ name: w.name, value: w.name }))
      : [];

    if (!resetWardIfInvalid) return;

    const currentWard = this.fulfillmentForm.controls.ward.value?.trim() || '';
    const isStillValid = this.listWardsData.some((w) => w.value === currentWard);
    if (!isStillValid) {
      this.fulfillmentForm.controls.ward.setValue('');
    }
  }

  /** Required validators only while Delivery is selected (FormSelect uses them for the * marker). */
  private _syncDeliveryValidators(enabled: boolean): void {
    const { city, ward, address } = this.fulfillmentForm.controls;
    if (enabled) {
      city.setValidators(validators.required('Tỉnh/Thành phố không được để trống'));
      ward.setValidators(validators.required('Phường/Xã không được để trống'));
      address.setValidators(validators.required('Địa chỉ không được để trống'));
    } else {
      city.clearValidators();
      ward.clearValidators();
      address.clearValidators();
    }
    city.updateValueAndValidity({ emitEvent: false });
    ward.updateValueAndValidity({ emitEvent: false });
    address.updateValueAndValidity({ emitEvent: false });
  }

  private _persistDraft(): void {
    const type = this.fulfillmentForm.controls.fulfillmentType.value;
    const normalized: '' | FulfillmentTypeUi =
      type === 'PickupAtShow' || type === 'MetaTicketPickup' || type === 'Delivery' ? type : '';
    this._checkoutService.saveFulfillmentDraft({
      fulfillmentType: normalized,
      showSessionId: this.fulfillmentForm.controls.showSessionId.value ?? '',
      pickupPointId: this.fulfillmentForm.controls.pickupPointId.value ?? '',
      deliveryCity: this.fulfillmentForm.controls.city.value ?? '',
      deliveryWard: this.fulfillmentForm.controls.ward.value ?? '',
      deliveryAddress: this.fulfillmentForm.controls.address.value ?? '',
      deliveryNote: this.fulfillmentForm.controls.note.value ?? '',
    });
  }
}
