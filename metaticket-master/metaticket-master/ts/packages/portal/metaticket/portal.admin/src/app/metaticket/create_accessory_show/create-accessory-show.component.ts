import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import {
    AbstractControl,
    FormArray,
    FormBuilder,
    FormControl,
    FormGroup,
    ValidationErrors,
    Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { Observable, ReplaySubject, Subscription, of } from 'rxjs';
import { tap } from 'rxjs/operators';
import removeAccents from 'remove-accents';import {
    AccessoryShowService,
    NotificationService,
    PaymentService,
    ShowService,
} from '../../@core/metaticket/data.service';
import {
    AccessoryPickupPointModel,
    AccessoryShowModel,
    AccessoryShowSessionModel,
    AccessoryShowStatus,
    AccessoryShowUpsertInput,
    CityModel,
    PAYMENT_TYPE_LABELS,
    PaymentType,
} from '../../@core/metaticket/model';
import { MailTemplateModel } from '../../@core/metaticket/model/notification.model';
import { PaymentGatewayType } from '../../@core/metaticket/model/payment.model';
import { PGAccountModel, PGConfigModel } from '../../@core/metaticket/model/pg.account.model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { generateDefaultSlug, isValidSlug, normalizeSlug } from '../../@core/metaticket/utils/slug.util';

/** Builds a short unique id for new sessions / pickup points in the form. */
function newLocalId(prefix: string): string {
    return `${prefix}-${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 8)}`;
}

/**
 * Cross-field validation: ≥1 fulfillment mode; sessions/points constraints
 * when the corresponding mode is enabled.
 */
function fulfillmentSettingsValidator(control: AbstractControl): ValidationErrors | null {
    const enablePickup = !!control.get('enablePickupAtShow')?.value;
    const enableMeta = !!control.get('enableMetaTicketPickup')?.value;
    const enableDelivery = !!control.get('enableDelivery')?.value;
    if (!enablePickup && !enableMeta && !enableDelivery) {
        return { fulfillmentRequired: true };
    }
    if (enablePickup) {
        const ids = (control.get('pickupAtShowSessionIds')?.value as string[]) || [];
        if (ids.length === 0) {
            return { pickupSessionsRequired: true };
        }
    }
    if (enableMeta) {
        const points = control.get('pickupPoints') as FormArray;
        if (!points || points.length === 0) {
            return { pickupPointsRequired: true };
        }
        if (points.controls.some(c => c.invalid)) {
            return { pickupPointsInvalid: true };
        }
    }
    return null;
}

/** Waiting + a chosen datetime: the instant must be strictly after now. */
function saleOpenAtFutureValidator(control: AbstractControl): ValidationErrors | null {
    if (control.get('status')?.value !== 'Waiting') {
        return null;
    }
    const raw = control.get('saleOpenAt')?.value;
    if (!raw) {
        return null;
    }
    const ms = raw instanceof Date ? raw.getTime() : new Date(raw).getTime();
    if (!Number.isFinite(ms) || ms <= Date.now()) {
        return { saleOpenAtNotFuture: true };
    }
    return null;
}

@Component({
    selector: 'create-accessory-show',
    templateUrl: './create-accessory-show.component.html',
    styleUrls: ['./create-accessory-show.component.scss'],
})
export class CreateAccessoryShowComponent implements OnInit, OnDestroy {
    @ViewChild('autoInputCity') autoInputCity?: ElementRef<HTMLInputElement>;
    @ViewChild('autoPaymentGateway') autoPaymentGateway?: ElementRef<HTMLInputElement>;
    @ViewChild('autoPGAccountCode') autoPGAccountCode?: ElementRef<HTMLInputElement>;

    show: AccessoryShowModel | null = null;
    isEdit = false;
    loading = 0;
    submitPressed = false;
    /** Active nb-tabset tab: 0 info, 1 fulfillment, 2 sessions. */
    activeTabIndex = 0;
    /** Collapses rarely edited payment / mail fields. */
    showAdvanced = false;

    readonly paymentMethodList = PAYMENT_TYPE_LABELS;
    readonly paymentGatewayList: PaymentGatewayType[] = ['NinePay', 'VNPay', 'OnePay'];

    cityList: CityModel[] = [];
    filteredCityList$: Observable<CityModel[]> = of([]);
    private readonly filteredCitySubject = new ReplaySubject<CityModel[]>(1);
    private readonly subs = new Subscription();

    pgConfig?: PGConfigModel;
    pgAccountList: PGAccountModel[] = [];
    filteredPGList$ = new ReplaySubject<PaymentGatewayType[]>(1);
    filteredPGAccountList$ = new ReplaySubject<PGAccountModel[]>(1);

    mailTemplates: MailTemplateModel[] = [];
    mailPlugins: string[] = [];

    /** Draft sessions edited in the form (not yet submitted). */
    sessions: AccessoryShowSessionModel[] = [];

    /** Stop overwriting slug when the operator edits it by hand. */
    private slugManuallyEdited = false;

    private readonly slugFormatValidator = (control: AbstractControl): ValidationErrors | null => {
        const error = isValidSlug(control.value);
        return error ? { slugFormat: error } : null;
    };

    form = this.fb.group(
        {
            code: new FormControl('', Validators.required),
            codePrefix: new FormControl('AC', Validators.required),
            name: new FormControl('', Validators.required),
            slug: new FormControl('', [this.slugFormatValidator]),
            description: new FormControl(''),
            bannerImageUrl: new FormControl(''),
            status: new FormControl<AccessoryShowStatus>('Open', Validators.required),
            saleOpenAt: new FormControl<Date | null>(null),
            pickupNote: new FormControl(''),
            deliveryNote: new FormControl(''),
            usePGAccountDefault: new FormControl(true),
            paymentGateway: new FormControl<PaymentGatewayType | ''>(''),
            pgAccountCode: new FormControl(''),
            allowedPaymentMethods: new FormControl<PaymentType[]>([]),
            mailPlugin: new FormControl('PluginMailTemplateAccessoryOrder'),
            mailTemplateId: new FormControl(''),
            enablePickupAtShow: new FormControl(true),
            enableMetaTicketPickup: new FormControl(false),
            enableDelivery: new FormControl(false),
            pickupAtShowSessionIds: new FormControl<string[]>([]),
            pickupPoints: this.fb.array([]),
            // Session draft inputs (add row)
            sessionCityId: new FormControl(''),
            sessionVenue: new FormControl(''),
            sessionDatetime: new FormControl<Date | null>(null),
        },
        { validators: [fulfillmentSettingsValidator, saleOpenAtFutureValidator] },
    );

    constructor(
        private fb: FormBuilder,
        private route: ActivatedRoute,
        private router: Router,
        private accessoryShowService: AccessoryShowService,
        private showService: ShowService,
        private paymentService: PaymentService,
        private notificationService: NotificationService,
        private toastr: NbToastrService,
    ) {
        this.filteredCityList$ = this.filteredCitySubject.asObservable();
    }

    get codeCtrl(): FormControl {
        return this.form.get('code') as FormControl;
    }
    get slugCtrl(): FormControl {
        return this.form.get('slug') as FormControl;
    }
    get enablePickupAtShow(): boolean {
        return !!this.form.get('enablePickupAtShow')?.value;
    }
    get enableMetaTicketPickup(): boolean {
        return !!this.form.get('enableMetaTicketPickup')?.value;
    }
    get enableDelivery(): boolean {
        return !!this.form.get('enableDelivery')?.value;
    }
    get pickupPoints(): FormArray {
        return this.form.get('pickupPoints') as FormArray;
    }
    get selectedSessionIds(): string[] {
        return (this.form.get('pickupAtShowSessionIds')?.value as string[]) || [];
    }
    get usePGAccountDefault(): boolean {
        return !!this.form.get('usePGAccountDefault')?.value;
    }

    /** Sale-open date field is only shown when status is Waiting. */
    get isWaitingStatus(): boolean {
        return this.form.get('status')?.value === 'Waiting';
    }

    get paymentGatewayCtrl(): FormControl {
        return this.form.get('paymentGateway') as FormControl;
    }
    get pgAccountCodeCtrl(): FormControl {
        return this.form.get('pgAccountCode') as FormControl;
    }
    get allowedPaymentMethodsCtrl(): FormControl {
        return this.form.get('allowedPaymentMethods') as FormControl;
    }

    /** Short summary for the sticky header while editing. */
    get headerTitle(): string {
        if (this.isEdit && this.show) {
            return this.form.get('name')?.value || this.show.name || 'Sửa show';
        }
        return this.isEdit ? 'Sửa show' : 'Tạo show phụ kiện';
    }

    /** Toggles a fulfillment mode checkbox from a clickable mode card. */
    toggleFulfillmentMode(controlName: 'enablePickupAtShow' | 'enableMetaTicketPickup' | 'enableDelivery'): void {
        const ctrl = this.form.get(controlName);
        if (!ctrl) return;
        ctrl.setValue(!ctrl.value);
        this.form.updateValueAndValidity();
    }

    ngOnInit(): void {
        this.loadCities();
        this.loadPgConfig();
        this.loadMailOptions();
        const id = this.route.snapshot.queryParamMap.get('id');
        this.isEdit = !!id;
        if (id) {
            this.loadShow(id);
        } else {
            this.form.get('code')?.enable();
        }

        this.subs.add(
            this.slugCtrl.valueChanges.subscribe(() => {
                this.slugManuallyEdited = true;
            }),
        );
        this.subs.add(
            this.form.get('name')!.valueChanges.subscribe(name => {
                if (this.slugManuallyEdited || this.isEdit) return;
                const next = generateDefaultSlug(name || '', new Set());
                this.slugCtrl.setValue(next, { emitEvent: false });
            }),
        );
        this.subs.add(
            this.form.get('enablePickupAtShow')!.valueChanges.subscribe(on => {
                if (!on) {
                    this.form.get('pickupAtShowSessionIds')?.setValue([]);
                }
            }),
        );
        this.subs.add(
            this.form.get('enableMetaTicketPickup')!.valueChanges.subscribe(on => {
                if (on && this.pickupPoints.length === 0) {
                    this.addPickupPoint();
                }
            }),
        );
        this.subs.add(
            this.form.get('usePGAccountDefault')!.valueChanges.subscribe(useDefault => {
                if (useDefault) {
                    this.form.patchValue({ paymentGateway: '', pgAccountCode: '' });
                }
            }),
        );
    }

    ngOnDestroy(): void {
        this.subs.unsubscribe();
    }

    /** Syncs activeTabIndex when the user clicks a Nebular tab header. */
    onTabChange(event: { tabTitle: string }): void {
        const title = (event?.tabTitle || '').trim();
        if (title.startsWith('Thông tin')) this.activeTabIndex = 0;
        else if (title.startsWith('Nhận hàng')) this.activeTabIndex = 1;
        else if (title.startsWith('Session')) this.activeTabIndex = 2;
    }

    /** Loads City catalog from ticket-show API (read-only reuse). */
    private loadCities(): void {
        this.loading += 1;
        this.showService.getCities().subscribe({
            next: cities => {
                this.loading -= 1;
                this.cityList = cities || [];
                this.filteredCitySubject.next(this.cityList);
            },
            error: error => {
                this.loading -= 1;
                this.toastr.show(getErrorMessageFromResponse(error), 'Không tải được danh sách thành phố', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                    duration: 15000,
                });
            },
        });
    }

    /** Loads PG accounts/config — same source as session payment settings. */
    private loadPgConfig(): void {
        this.loading += 1;
        this.paymentService.getPGConfig().subscribe({
            next: pgConfig => {
                this.loading -= 1;
                this.pgConfig = pgConfig;
                this.pgAccountList = pgConfig?.pgAccount || [];
                this.filteredPGList$.next(this.paymentGatewayList);
                this.applyPgAccountFromCode(this.form.get('pgAccountCode')?.value as string);
            },
            error: error => {
                this.loading -= 1;
                this.toastr.show(getErrorMessageFromResponse(error), 'Load PG config thất bại', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                    duration: 15000,
                });
            },
        });
    }

    /** Loads mail plugin/template options — same APIs as session email settings. */
    private loadMailOptions(): void {
        this.loading += 1;
        this.notificationService.getMailTemplates().subscribe({
            next: templates => {
                this.loading -= 1;
                this.mailTemplates = templates || [];
            },
            error: error => {
                this.loading -= 1;
                this.toastr.show(getErrorMessageFromResponse(error), 'Load mail template thất bại', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                    duration: 15000,
                });
            },
        });
        this.loading += 1;
        this.notificationService.getMailPlugins().subscribe({
            next: plugins => {
                this.loading -= 1;
                this.mailPlugins = (plugins || []).map(p => String(p));
            },
            error: error => {
                this.loading -= 1;
                this.toastr.show(getErrorMessageFromResponse(error), 'Load mail plugin thất bại', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                    duration: 15000,
                });
            },
        });
    }

    /** Fetches an existing mock show into the form. */
    private loadShow(id: string): void {
        this.loading += 1;
        this.accessoryShowService
            .getShow$(id)
            .pipe(
                tap({
                    next: () => {
                        this.loading -= 1;
                    },
                    error: () => {
                        this.loading -= 1;
                    },
                }),
            )
            .subscribe({
                next: show => {
                    this.show = show;
                    this.sessions = [...(show.sessions || [])];
                    this.form.patchValue({
                        code: show.code,
                        codePrefix: show.codePrefix,
                        name: show.name,
                        slug: show.slug || '',
                        description: show.description || '',
                        bannerImageUrl: show.bannerImageUrl || '',
                        status: show.status,
                        saleOpenAt: show.saleOpenAt ? new Date(show.saleOpenAt) : null,
                        pickupNote: show.pickupNote || '',
                        deliveryNote: show.deliveryNote || '',
                        usePGAccountDefault: !show.pgAccountCode,
                        pgAccountCode: show.pgAccountCode || '',
                        allowedPaymentMethods: show.allowedPaymentMethods || [],
                        mailPlugin: show.mailPlugin,
                        mailTemplateId: show.mailTemplateId || '',
                        enablePickupAtShow: show.fulfillment.enablePickupAtShow,
                        enableMetaTicketPickup: show.fulfillment.enableMetaTicketPickup,
                        enableDelivery: show.fulfillment.enableDelivery,
                        pickupAtShowSessionIds: [...show.fulfillment.pickupAtShowSessionIds],
                    });
                    this.slugManuallyEdited = true;
                    this.applyPgAccountFromCode(show.pgAccountCode);
                    this.codeCtrl.disable();
                    this.pickupPoints.clear();
                    for (const p of show.fulfillment.pickupPoints || []) {
                        this.pickupPoints.push(this.buildPickupPointGroup(p));
                    }
                    if (show.pgAccountCode || (show.allowedPaymentMethods || []).length || show.mailTemplateId) {
                        this.showAdvanced = true;
                    }
                },
                error: error => {
                    this.toastr.show(getErrorMessageFromResponse(error), 'Thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                    this.router.navigateByUrl('/portal/accessory/shows');
                },
            });
    }

    /** Filters city autocomplete by accent-insensitive name/code. */
    onInputCityChange(raw: string): void {
        const q = removeAccents((raw || '').trim().toLowerCase());
        if (!q) {
            this.filteredCitySubject.next(this.cityList);
            return;
        }
        this.filteredCitySubject.next(
            this.cityList.filter(c => {
                const name = removeAccents((c.name || '').toLowerCase());
                const code = removeAccents((c.code || '').toLowerCase());
                return name.includes(q) || code.includes(q);
            }),
        );
    }

    /** Sets sessionCityId when an autocomplete option is chosen. */
    onSelectionCityChange(cityId: string): void {
        const city = this.cityList.find(c => c.id === cityId);
        this.form.get('sessionCityId')?.setValue(cityId);
        if (city && this.autoInputCity?.nativeElement) {
            this.autoInputCity.nativeElement.value = city.name;
        }
    }

    /** Appends a session from the draft inputs into the local sessions list. */
    addSession(): void {
        const cityId = this.form.get('sessionCityId')?.value as string;
        const venue = (this.form.get('sessionVenue')?.value as string)?.trim();
        const dt = this.form.get('sessionDatetime')?.value as Date | null;
        const city = this.cityList.find(c => c.id === cityId);

        if (!city || !venue || !dt) {
            this.toastr.show('Chọn thành phố, nhập venue và datetime', 'Thiếu thông tin session', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }

        const session: AccessoryShowSessionModel = {
            id: newLocalId('sess'),
            city: { id: city.id, name: city.name, code: city.code },
            venue,
            datetime: dt instanceof Date ? dt.getTime() : new Date(dt).getTime(),
        };
        this.sessions = [...this.sessions, session].sort((a, b) => a.datetime - b.datetime);

        // Auto-check new session when pickup-at-show is enabled
        if (this.enablePickupAtShow) {
            const ids = new Set(this.selectedSessionIds);
            ids.add(session.id);
            this.form.get('pickupAtShowSessionIds')?.setValue([...ids]);
        }

        this.form.patchValue({ sessionCityId: '', sessionVenue: '', sessionDatetime: null });
        if (this.autoInputCity?.nativeElement) {
            this.autoInputCity.nativeElement.value = '';
        }
        this.form.updateValueAndValidity();
    }

    /** Removes a session and cleans fulfillment session ids. */
    removeSession(sessionId: string): void {
        this.sessions = this.sessions.filter(s => s.id !== sessionId);
        this.form
            .get('pickupAtShowSessionIds')
            ?.setValue(this.selectedSessionIds.filter(id => id !== sessionId));
        this.form.updateValueAndValidity();
    }

    /** Toggles whether a session is enabled for pickup-at-show checkout. */
    togglePickupSession(sessionId: string, checked: boolean): void {
        const ids = new Set(this.selectedSessionIds);
        if (checked) ids.add(sessionId);
        else ids.delete(sessionId);
        this.form.get('pickupAtShowSessionIds')?.setValue([...ids]);
        this.form.updateValueAndValidity();
    }

    isPickupSessionChecked(sessionId: string): boolean {
        return this.selectedSessionIds.includes(sessionId);
    }

    /** Adds an empty pickup-point form group. */
    addPickupPoint(point?: AccessoryPickupPointModel): void {
        this.pickupPoints.push(this.buildPickupPointGroup(point));
        this.form.updateValueAndValidity();
    }

    /** Removes a pickup point row. */
    removePickupPoint(index: number): void {
        this.pickupPoints.removeAt(index);
        this.form.updateValueAndValidity();
    }

    private buildPickupPointGroup(point?: AccessoryPickupPointModel): FormGroup {
        return this.fb.group({
            id: new FormControl(point?.id || newLocalId('pp')),
            name: new FormControl(point?.name || '', Validators.required),
            address: new FormControl(point?.address || '', Validators.required),
            note: new FormControl(point?.note || ''),
        });
    }

    /** Mirrors session payment: sync pressed payment-method toggles into the form. */
    updatePaymentMethodsValue(value: PaymentType[]): void {
        this.allowedPaymentMethodsCtrl.setValue(value || []);
    }

    /** Whether a payment method button should appear pressed. */
    isPaymentMethodPressed(method: PaymentType): boolean {
        return (this.allowedPaymentMethodsCtrl.value || []).includes(method);
    }

    onInputPaymentGatewayChange(value: string): void {
        this.filteredPGList$.next(this.filterPaymentGatewayType(value));
    }

    onInputPaymentAccountChange(value: string): void {
        if (!this.paymentGatewayCtrl.value) {
            this.filteredPGAccountList$.next([]);
            return;
        }
        this.filteredPGAccountList$.next(this.filterPaymentGatewayAccount(value));
    }

    /**
     * nbAutocomplete re-emits selectedChange on every programmatic write (writeValue), including form
     * patches and the first render of the input. So these handlers must never write back to their own
     * control (that recursed until "Maximum call stack size exceeded"); the directive already updates it.
     * A still-valid account is kept so re-rendering the panel does not wipe a stored pgAccountCode.
     */
    onSelectionPGChange(gateway: PaymentGatewayType): void {
        this.pgAccountList = (this.pgConfig?.pgAccount || []).filter(a => a.gateway === gateway);
        this.filteredPGAccountList$.next(this.pgAccountList);
        if (this.pgAccountList.some(a => a.code === this.pgAccountCodeCtrl.value)) return;
        this.pgAccountCodeCtrl.setValue(this.pgAccountList.length === 1 ? this.pgAccountList[0].code : '');
    }

    onSelectionPGAccountChange(code: string): void {
        this.filteredPGAccountList$.next(this.pgAccountList.filter(s => s.code === code));
    }

    /** Prefills gateway autocomplete from a stored pg account code. */
    private applyPgAccountFromCode(code?: string | null): void {
        if (!code || !this.pgConfig) {
            this.form.patchValue({ usePGAccountDefault: !code, paymentGateway: '' });
            return;
        }
        const account = this.pgConfig.pgAccount.find(a => a.code === code);
        if (!account) {
            this.form.patchValue({ usePGAccountDefault: false, paymentGateway: '', pgAccountCode: code });
            return;
        }
        this.form.patchValue({
            usePGAccountDefault: false,
            paymentGateway: account.gateway,
            pgAccountCode: account.code,
        });
        this.pgAccountList = this.pgConfig.pgAccount.filter(a => a.gateway === account.gateway);
        this.filteredPGList$.next(this.paymentGatewayList.filter(g => g === account.gateway));
        this.filteredPGAccountList$.next(this.pgAccountList.filter(a => a.code === account.code));
    }

    private filterPaymentGatewayType(input: string): PaymentGatewayType[] {
        const filterValue = removeAccents((input || '').toLowerCase());
        return this.paymentGatewayList.filter(
            optionValue =>
                !input || removeAccents(optionValue.toLowerCase()).indexOf(filterValue) > -1,
        );
    }

    private filterPaymentGatewayAccount(input: string): PGAccountModel[] {
        const filterValue = removeAccents((input || '').toLowerCase());
        return this.pgAccountList.filter(
            optionValue =>
                !input ||
                removeAccents((optionValue.name || '').toLowerCase()).indexOf(filterValue) > -1 ||
                removeAccents((optionValue.code || '').toLowerCase()).indexOf(filterValue) > -1,
        );
    }

    /** Epoch millis for saleOpenAt, or null when the date is empty. */
    private toEpochMillis(value: Date | string | null | undefined): number | null {
        if (!value) {
            return null;
        }
        const ms = value instanceof Date ? value.getTime() : new Date(value).getTime();
        return Number.isFinite(ms) && ms > 0 ? ms : null;
    }

    /** Submits create/update against private accessory show APIs. */
    onSubmit(): void {
        this.submitPressed = true;
        this.form.markAllAsTouched();
        this.form.updateValueAndValidity();
        if (this.form.invalid) {
            this.focusInvalidTab();
            const err = this.form.errors;
            let msg = 'Vui lòng kiểm tra lại form';
            if (err?.['fulfillmentRequired']) msg = 'Chọn ít nhất 1 hình thức nhận hàng';
            else if (err?.['pickupSessionsRequired']) msg = 'Chọn ít nhất 1 session cho “Nhận tại show”';
            else if (err?.['pickupPointsRequired']) msg = 'Thêm ít nhất 1 điểm pickup';
            else if (err?.['pickupPointsInvalid']) msg = 'Điểm pickup thiếu Tên hoặc Địa chỉ';
            else if (err?.['saleOpenAtNotFuture']) msg = 'Ngày giờ mở bán phải sau thời điểm hiện tại';
            this.toastr.show(msg, 'Form chưa hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }

        const raw = this.form.getRawValue();
        const input: AccessoryShowUpsertInput = {
            code: raw.code,
            codePrefix: raw.codePrefix,
            name: raw.name,
            slug: normalizeSlug(raw.slug || '') || null,
            description: raw.description || undefined,
            bannerImageUrl: raw.bannerImageUrl || undefined,
            sessions: this.sessions,
            status: raw.status,
            saleOpenAt: this.toEpochMillis(raw.saleOpenAt),
            pickupNote: raw.pickupNote || undefined,
            deliveryNote: raw.enableDelivery ? raw.deliveryNote || undefined : undefined,
            pgAccountCode: raw.usePGAccountDefault ? undefined : raw.pgAccountCode || undefined,
            allowedPaymentMethods: raw.allowedPaymentMethods || [],
            mailPlugin: raw.mailPlugin || 'PluginMailTemplateAccessoryOrder',
            mailTemplateId: (raw.mailTemplateId || '').trim() || undefined,
            fulfillment: {
                enablePickupAtShow: !!raw.enablePickupAtShow,
                enableMetaTicketPickup: !!raw.enableMetaTicketPickup,
                enableDelivery: !!raw.enableDelivery,
                pickupAtShowSessionIds: raw.enablePickupAtShow ? [...(raw.pickupAtShowSessionIds || [])] : [],
                pickupPoints: raw.enableMetaTicketPickup
                    ? (this.pickupPoints.getRawValue() as AccessoryPickupPointModel[]).map(p => ({
                          id: p.id,
                          name: p.name.trim(),
                          address: p.address.trim(),
                          note: p.note?.trim() || undefined,
                      }))
                    : [],
            },
        };

        this.loading += 1;
        const req$ =
            this.isEdit && this.show
                ? this.accessoryShowService.updateShow$(this.show.id, input)
                : this.accessoryShowService.createShow$(input);

        req$
            .pipe(
                tap({
                    next: () => {
                        this.loading -= 1;
                    },
                    error: () => {
                        this.loading -= 1;
                    },
                }),
            )
            .subscribe({
                next: saved => {
                    this.toastr.show('Đã lưu show', 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                    this.router.navigateByUrl('/portal/accessory/show/update?id=' + saved.id);
                },
                error: error => {
                    this.toastr.show(getErrorMessageFromResponse(error), 'Lỗi slug link khi lưu show', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'warning',
                        duration: 12000,
                    });
                },
            });
    }

    /** Jumps to the tab that contains the first validation problem. */
    private focusInvalidTab(): void {
        const err = this.form.errors;
        if (
            err?.['fulfillmentRequired'] ||
            err?.['pickupSessionsRequired'] ||
            err?.['pickupPointsRequired'] ||
            err?.['pickupPointsInvalid']
        ) {
            this.activeTabIndex = 1;
            return;
        }
        const infoKeys = ['code', 'codePrefix', 'name', 'status'];
        if (infoKeys.some(k => this.form.get(k)?.invalid)) {
            this.activeTabIndex = 0;
            return;
        }
        this.activeTabIndex = 0;
    }
}
