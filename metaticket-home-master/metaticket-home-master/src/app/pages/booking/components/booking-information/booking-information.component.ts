import { Component, OnDestroy, OnInit } from '@angular/core';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { Subscription } from 'rxjs';
import {
  ButtonComponent,
  DataCollectionInputComponent,
  FormCheckboxComponent,
  FormInputComponent,
  FormSelectComponent,
  ImageComponent,
  LoadingComponent,
} from 'src/app/lib/components';
import { FormatPricePipe, SecondsToTimePipe } from 'src/app/lib/pipes';
import {
  CreateOrderRequest,
  CreatePaymentRequest,
  CustomFieldConstraint,
  CustomFieldRequest,
  CustomFieldResponse,
  GenerateOdcLinkRequest,
  GetCustomFieldsRequest,
  OrderDeliveryRequest,
  OrderSummaryResponse,
  TentativePriceResponse,
  UserProfileModel,
} from 'src/app/lib/entities';
import { FormBuilder, FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { escapeHtml, normalizePhoneNumber, serverErrorText, validationRegex, validators } from 'src/app/lib/utils';
import { appConstants } from 'src/app/lib/constants';
import { AuthService, NotificationService, OrderService, TicketService } from 'src/app/lib/services';
import { BookingTicketItemComponent } from '../booking-ticket-item/booking-ticket-item.component';
import { BookingQuery, BookingService } from 'src/app/lib/stores';
import { appIcons } from 'src/app/lib/theme';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { CustomFieldType, TicketReservationStatus } from 'src/app/lib/enums';
import { SeatPlanTriggerItem, SelectOptionItem } from 'src/app/lib/dto';
import { HttpErrorResponse } from '@angular/common/http';

/**
 * Hold states no order can be created from any more: service.order fails the hold of an order it refuses
 * (`OrderFailed`), expiry removes it. `Reserved`/`OrderPaying`/`OrderSuccessful` are deliberately absent.
 */
const RELEASED_RESERVATION_STATUSES: TicketReservationStatus[] = [
  TicketReservationStatus.OrderFailed,
  TicketReservationStatus.OrderCancelled,
  TicketReservationStatus.Removing,
  TicketReservationStatus.Removed,
];
const RESTART_BOOKING_MESSAGE = 'Đặt vé không thành công. Vui lòng chọn vé và đặt lại.';
const CREATE_ORDER_FAILED_MESSAGE = 'Đặt vé không thành công. Vui lòng kiểm tra thông tin và thử lại.';
const PAYING_ELSEWHERE_MESSAGE =
  'Đơn hàng này đang được thanh toán ở cửa sổ khác. Vui lòng hoàn tất thanh toán ở cửa sổ đó.';
/** Statuses of an order whose payment was already started (by another tab on the same hold): `payment/create` refuses them. */
const PAYMENT_STARTED_ORDER_STATUSES = ['WaitingForPayment', 'PaymentProcessing'];

@Component({
  selector: 'booking-information',
  standalone: true,
  imports: [
    NzTypographyModule,
    NgTemplateOutlet,
    NzDividerComponent,
    FormInputComponent,
    FormSelectComponent,
    LoadingComponent,
    NgIf,
    ButtonComponent,
    FormatPricePipe,
    NgForOf,
    BookingTicketItemComponent,
    SecondsToTimePipe,
    ImageComponent,
    NzDrawerModule,
    FormCheckboxComponent,
    DataCollectionInputComponent,
  ],
  templateUrl: './booking-information.component.html',
})
export class BookingInformationComponent implements OnInit, OnDestroy {
  private readonly _reservationId: string;
  private readonly _promotionCode: string;
  /** Order (with its payment) this hold already has: set when the customer comes back from the payment step. */
  private readonly _existingOrder?: { orderId: string; code: string; paymentId: string };
  readonly listTickets: SeatPlanTriggerItem[];
  readonly tentativePrice: TentativePriceResponse;
  protected readonly appIcons = appIcons;
  protected readonly customFieldType = CustomFieldType;
  protected readonly listProvincesData: SelectOptionItem[] = appConstants.provinces.map((item) => ({
    name: item,
    value: item,
  }));

  optionSelectData?: { [key: string]: SelectOptionItem[] };
  dataCollectors?: {
    [key: string]: {
      odcLink: string;
      isRequired: boolean;
      isValid: boolean;
    };
  };
  bottomSheetVisible = false;
  isLoading = false;
  createOrderLoading = false;
  expireInS = 0;
  expireTimeInterval?: number;
  customFields: CustomFieldResponse[] = [];
  isDisable = false;
  existingOrderSummary?: OrderSummaryResponse;
  private userProfileSubscription?: Subscription;

  purchaseFormGroup: FormGroup<{
    name: FormControl<string | null>;
    phone: FormControl<string | null>;
    email: FormControl<string | null>;
  }>;

  deliveryFormGroup?: FormGroup<{
    deliveryName: FormControl<string | null>;
    deliveryPhone: FormControl<string | null>;
    provinceName: FormControl<string | null>;
    districtName: FormControl<string | null>;
    wardName: FormControl<string | null>;
    address: FormControl<string | null>;
  }>;

  customFormGroup?: FormGroup;

  constructor(
    private _fb: FormBuilder,
    private _orderService: OrderService,
    private _ticketService: TicketService,
    private _notificationService: NotificationService,
    private _bookingQuery: BookingQuery,
    private _bookingService: BookingService,
    private _authService: AuthService,
  ) {
    const state = this._bookingQuery.getValue();
    this._reservationId = state.reservationId;
    this.listTickets = state.listSelectedTickets;
    this.tentativePrice = state.tentativePrice;
    this._promotionCode = state.promotionCode;
    if (state.orderId && state.paymentId) {
      this._existingOrder = { orderId: state.orderId, code: state.code ?? '', paymentId: state.paymentId };
    }

    // initial form
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

    if (state.requireDelivery) {
      this.deliveryFormGroup = this._fb.group({
        deliveryName: [
          '',
          Validators.compose([
            validators.required('Họ và tên không được để trống'),
            validators.regExp('valid', validationRegex.fullName, 'Họ và tên không hợp lệ'),
          ]),
        ],
        deliveryPhone: [
          '',
          Validators.compose([
            validators.required('Số điện thoại không được để trống'),
            validators.regExp('valid', validationRegex.phoneNumber, 'Số điện thoại không hợp lệ'),
          ]),
        ],
        provinceName: ['', validators.required('Tỉnh/Thành phố không được để trống')],
        districtName: ['', validators.required('Quận/Huyện không được để trống')],
        wardName: ['', validators.required('Phường/Xã không được để trống')],
        address: ['', Validators.compose([validators.required('Tên đường, số nhà không được để trống')])],
      });
    }
  }

  get hasExistingOrder(): boolean {
    return !!this._existingOrder;
  }

  get existingOrderCode(): string {
    return this._existingOrder?.code ?? '';
  }

  getCustomFormControl(id: string): FormControl {
    if (this.customFormGroup) {
      return this.customFormGroup.controls[id] as FormControl;
    }

    return new FormControl();
  }

  onOpenBottomSheet(): void {
    this.bottomSheetVisible = true;
  }

  onCloseBottomSheet(): void {
    this.bottomSheetVisible = false;
  }

  onGoBack(): void {
    this._ticketService.releaseReservation(this._reservationId).subscribe(() => {
      this._bookingService.onPreviousIndex();
    });
  }

  onSubmit(): void {
    if (this._existingOrder) {
      // The order and its payment already exist for this hold. Creating again only answers the same order, now
      // WaitingForPayment (and a 500 on backends without the repeated-create answer), so resume paying it instead.
      this._bookingService.bookingInformation(this._existingOrder);
      return;
    }

    const isValid =
      this.purchaseFormGroup.valid && (this.deliveryFormGroup?.valid ?? true) && (this.customFormGroup?.valid ?? true);

    if (!isValid) {
      Object.values(this.purchaseFormGroup.controls).forEach((control) => {
        if (control.invalid) {
          control.markAsDirty();
          control.updateValueAndValidity({ onlySelf: true });
        }
      });

      if (this.deliveryFormGroup) {
        Object.values(this.deliveryFormGroup.controls).forEach((control) => {
          if (control.invalid) {
            control.markAsDirty();
            control.updateValueAndValidity({ onlySelf: true });
          }
        });
      }

      if (this.customFormGroup) {
        Object.values(this.customFormGroup.controls).forEach((control) => {
          if (control.invalid) {
            control.markAsDirty();
            control.updateValueAndValidity({ onlySelf: true });
          }
        });
      }
      return;
    }

    const purchaseFormValue = this.purchaseFormGroup.value;
    let body: CreateOrderRequest = {
      reservationId: this._reservationId,
      name: purchaseFormValue.name ?? '',
      phone: purchaseFormValue.phone ?? '',
      email: purchaseFormValue.email ?? '',
      customFields: [],
      promotionCode: this._promotionCode,
    };

    if (this.deliveryFormGroup) {
      const deliveryFormValue = this.deliveryFormGroup.value;
      const delivery: OrderDeliveryRequest = {
        deliveryName: deliveryFormValue.deliveryName ?? '',
        deliveryPhone: deliveryFormValue.deliveryPhone ?? '',
        provinceName: deliveryFormValue.provinceName ?? '',
        districtName: deliveryFormValue.districtName ?? '',
        wardName: deliveryFormValue.wardName ?? '',
        address: deliveryFormValue.address ?? '',
      };
      body = { ...body, delivery };
    }

    if (this.customFormGroup) {
      const customFields: CustomFieldRequest[] = this.customFields.map((item) => {
        let value = (this.customFormGroup?.get(item.id)?.value ?? '') as string;

        if (item.type === CustomFieldType.Boolean) {
          value = JSON.stringify(this.customFormGroup?.get(item.id)?.value ?? '');
        }

        if (item.type === CustomFieldType.DataCollector) {
          value = this.dataCollectors![item.id].odcLink;
        }

        return {
          id: item.id,
          name: item.displayName,
          value: value,
        };
      });
      body = { ...body, customFields: customFields };
    }

    this.createOrderLoading = true;
    this._orderService.createOrder(body).subscribe({
      next: (response) => {
        if (response.status == 'PaymentPending' || response.status == 'PaymentSucceed') {
          const params = {
            orderId: response.id,
            code: response.code,
          };
          this._bookingService.bookingInformation(params);
          this.createOrderLoading = false;
          return;
        }
        if (PAYMENT_STARTED_ORDER_STATUSES.includes(response.status)) {
          // service.order answers a repeated create with the hold's existing order. Its payment belongs to the other
          // tab, so keep this tab's session (shared localStorage) and send the customer back there.
          this.createOrderLoading = false;
          this._notificationService.showBlockingWarning(PAYING_ELSEWHERE_MESSAGE);
          return;
        }
        const body: CreatePaymentRequest = { orderId: response.id };
        this._orderService.createPayment(body).subscribe({
          next: (data) => {
            const params = {
              orderId: response.id,
              code: response.code,
              paymentId: data,
            };
            this._bookingService.bookingInformation(params);
            this.createOrderLoading = false;
          },
          error: (err: HttpErrorResponse) => {
            console.error('create payment error', err);
            this.createOrderLoading = false;
            this._notificationService.showErrorNotification(
              escapeHtml(serverErrorText(err) || 'Không thể tạo thanh toán. Vui lòng thử lại.'),
            );
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        console.error('create order error', err);
        this._handleCreateOrderFailure(err);
      },
    });
  }

  /**
   * A refused order comes back as the same plain-text 4xx whether the customer mistyped their email (hold still
   * Reserved: fix the form and resubmit) or the promotion code was refused / its last use was taken (service.order
   * already released the hold, so resubmitting and "Quay lại" can only fail). The body carries no code to tell them
   * apart, so ask the hold itself; only a released one sends the customer back to pick tickets again.
   */
  private _handleCreateOrderFailure(err: HttpErrorResponse): void {
    const stayOnForm = (): void => {
      this.createOrderLoading = false;
      // Server text (e.g. a wrong email) is the useful part; escaped because the notification renders innerHTML.
      this._notificationService.showErrorNotification(escapeHtml(serverErrorText(err) || CREATE_ORDER_FAILED_MESSAGE));
    };
    this._ticketService.getSingleReservation(this._reservationId).subscribe({
      next: (reservation) => {
        if (RELEASED_RESERVATION_STATUSES.includes(reservation.status)) {
          this._restartBooking(err);
        } else {
          stayOnForm();
        }
      },
      error: (lookupErr: HttpErrorResponse) => {
        // 404: the hold is gone. Anything else is unknown: keep the old behaviour rather than drop a live hold.
        if (lookupErr.status === 404) {
          this._restartBooking(err);
        } else {
          stayOnForm();
        }
      },
    });
  }

  private _restartBooking(err: HttpErrorResponse): void {
    this.createOrderLoading = false;
    // Nginx error pages (502/504 HTML) or non-4xx responses must not leak into customer messages.
    const serverText = serverErrorText(err);
    // Stays until closed: the page changes underneath, a 3s toast is easy to miss.
    this._notificationService.showBlockingWarning(escapeHtml(serverText || RESTART_BOOKING_MESSAGE));
    this._bookingService.expireBookingSession();
  }

  private _getSingleReservation(): void {
    this._ticketService.getSingleReservation(this._reservationId).subscribe({
      next: (response) => {
        const currentTime = Date.now();
        const expireTime = currentTime + response.expireInS * 1000;
        this._startCountdown(expireTime);
      },
      error: (error) => {
        console.log('get single reservation error', error);
      },
    });
  }

  private _startCountdown(expireTime: number): void {
    // eslint-disable-next-line @typescript-eslint/explicit-function-return-type
    const updateCountdown = () => {
      const currentTime = Date.now();
      const remainingTime = Math.max(0, expireTime - currentTime);

      this.expireInS = Math.floor(remainingTime / 1000);
      if (remainingTime === 0) {
        this._bookingService.expireBookingSession();
        clearInterval(this.expireTimeInterval);
      }
    };

    updateCountdown();
    this.expireTimeInterval = window.setInterval(updateCountdown, 1000);
  }

  private _getCustomFields(): void {
    this.isLoading = true;
    const state = this._bookingQuery.getValue();
    const body: GetCustomFieldsRequest = {
      showId: state.showId,
      sessionId: state.sessionId,
    };
    this._orderService.getCustomFields(body).subscribe({
      next: (response) => {
        if (response.length > 0) {
          this.customFields = response;
          this.customFormGroup = this._fb.group({});
          response.forEach((item) => {
            if (item.type === CustomFieldType.DataCollector) {
              const constraints = JSON.parse(item.constraints) as CustomFieldConstraint;
              const request: GenerateOdcLinkRequest = {
                resId: this._reservationId,
              };
              this._orderService.generateOdcLink(request).subscribe({
                next: (res) => {
                  this.isDisable = true;
                  this.dataCollectors = {
                    ...this.dataCollectors,
                    [item.id]: {
                      odcLink: res,
                      isRequired: constraints.require,
                      isValid: false,
                    },
                  };
                },
                error: (err) => {
                  console.log('Generate odc link err', err);
                },
              });
              return;
            }

            const validator: ValidatorFn[] = [];
            const constraints = JSON.parse(item.constraints) as CustomFieldConstraint;
            if (constraints.require) {
              validator.push(validators.required(`${item.displayName} không được để trống`));
            }

            if (constraints.regex) {
              const regex = new RegExp(constraints.regex);
              validator.push(validators.regExp('valid', regex, `${item.displayName} không hợp lệ`));
            }

            if (constraints.options) {
              this.optionSelectData = {
                ...this.optionSelectData,
                [item.id]: constraints.options,
              };
            }

            this.customFormGroup!.addControl(item.id, this._fb.control('', Validators.compose(validator)));
          });
        }
        this.isLoading = false;
      },
      error: (error) => {
        console.log('get custom fields error', error);
        this.isLoading = false;
      },
    });
  }

  validateOdcLink(odcLink: string): void {
    if (this.dataCollectors) {
      Object.keys(this.dataCollectors).forEach((item) => {
        if (this.dataCollectors![item].odcLink === odcLink) {
          this.dataCollectors = {
            ...this.dataCollectors,
            [item]: {
              isRequired: this.dataCollectors![item].isRequired,
              odcLink: odcLink,
              isValid: true,
            },
          };
        }
      });

      this.isDisable = Object.values(this.dataCollectors).some((item) => !item.isValid);
    }
  }

  ngOnInit(): void {
    this._getSingleReservation();
    if (this._existingOrder) {
      this._getExistingOrderSummary(this._existingOrder.orderId);
      return;
    }
    this._getCustomFields();
    this._fillUserProfileIfAuthenticated();
  }

  /** Buyer details already saved on the order, shown read-only. Best effort: on failure the notice alone is shown. */
  private _getExistingOrderSummary(orderId: string): void {
    this.isLoading = true;
    this._orderService.getOrderSummary(orderId).subscribe({
      next: (summary) => {
        this.existingOrderSummary = summary;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      },
    });
  }

  private _fillUserProfileIfAuthenticated(): void {
    this.userProfileSubscription = this._authService.userProfile$.subscribe({
      next: (userProfile) => {
        if (userProfile) {
          this._fillUserProfile(userProfile);
        }
      },
      error: (error) => {
        console.error('Error getting user profile:', error);
      },
    });
  }

  private _fillUserProfile(userProfile: UserProfileModel): void {
    // Chuyển đổi số điện thoại từ định dạng quốc tế sang địa phương
    const normalizedPhone = normalizePhoneNumber(userProfile.phone || '');

    // Điền thông tin vào form mua vé
    this.purchaseFormGroup.patchValue({
      name: userProfile.name || '',
      phone: normalizedPhone,
      email: userProfile.email || '',
    });

    // Điền thông tin vào form giao hàng nếu có
    if (this.deliveryFormGroup) {
      this.deliveryFormGroup.patchValue({
        deliveryName: userProfile.name || '',
        deliveryPhone: normalizedPhone,
      });
    }
  }

  ngOnDestroy(): void {
    if (this.expireTimeInterval) {
      clearInterval(this.expireTimeInterval);
    }
    if (this.userProfileSubscription) {
      this.userProfileSubscription.unsubscribe();
    }
  }
}
