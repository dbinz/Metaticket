import { AfterViewInit, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { FormBuilder, FormControl, Validators } from '@angular/forms';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { AdminService, PaymentService, ShowService } from '@admin.portal/@core/metaticket/data.service';
import {
    PAYMENT_TYPE_LABELS,
    PaymentGatewayType,
    PaymentType,
    ShowSessionDraftModel,
    ShowSessionModel,
} from '@admin.portal/@core/metaticket/model';
import { PGAccountModel, PGConfigModel } from '@admin.portal/@core/metaticket/model/pg.account.model';
import { getErrorMessageFromResponse } from '@admin.portal/@core/metaticket/utils/error.response.utils';
import removeAccents from 'remove-accents';
import { ReplaySubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './update_show_session_payment.component.html',
    styleUrls: ['./update_show_session_payment.component.scss'],
    selector: 'update-show-session-payment',
})
export class UpdateShowSessionPaymentComponent {
    @Input() showSessionDraft: ShowSessionDraftModel | null;
    @Input() showSession: ShowSessionModel | null;
    @Input() showId: string;
    @Output() updateSessionDraftSucceed: EventEmitter<ShowSessionDraftModel> =
        new EventEmitter<ShowSessionDraftModel>();
    @Output() updateSessionSucceed: EventEmitter<ShowSessionModel> = new EventEmitter<ShowSessionModel>();
    @Output() gotoNextStep: EventEmitter<void> = new EventEmitter<void>();

    paymentType: 'paymentOffline' | 'paymentGateway';
    paymentGatewayList: PaymentGatewayType[] = ['NinePay', 'VNPay', 'OnePay'];
    paymentMethodList = PAYMENT_TYPE_LABELS;
    pgAccountList: PGAccountModel[] = [];
    filteredPGList$: Subject<PaymentGatewayType[]> = new ReplaySubject<PaymentGatewayType[]>();
    filteredPGAccountList$: Subject<PGAccountModel[]> = new ReplaySubject<PGAccountModel[]>();

    @ViewChild('autoPaymentGateway') inputPaymentGateway;
    @ViewChild('autoPGAccountCode') inputPGAccountCode;

    updatePaymentForm = this.formBuilder.group({
        paymentNote: new FormControl(undefined),
        paymentGateway: new FormControl<PaymentGatewayType>(undefined),
        pgAccountCode: new FormControl(undefined),
        usePGAccountDefault: new FormControl(true),
        paymentMethods: new FormControl<PaymentType[]>([], Validators.minLength(1)),
    });

    loading: number = 0;

    pgConfig: PGConfigModel;

    constructor(
        private formBuilder: FormBuilder,
        private adminService: AdminService,
        private showService: ShowService,
        private paymentService: PaymentService,
        private toastrService: NbToastrService,
    ) {
        this.loading += 1;
        this.paymentService
            .getPGConfig()
            .pipe(
                tap(
                    e => {
                        this.loading -= 1;
                    },
                    e => {
                        this.loading -= 1;
                    },
                ),
            )
            .subscribe(
                pgConfig => {
                    this.pgConfig = pgConfig;
                    this.pgAccountList = pgConfig.pgAccount;
                    if (this.showSessionDraft) {
                        this.paymentType = this.showSessionDraft.paymentOffline ? 'paymentOffline' : 'paymentGateway';
                        this.updatePaymentForm.patchValue({
                            paymentNote: this.showSessionDraft.paymentNote,
                            pgAccountCode: this.showSessionDraft.pgAccountCode,
                            paymentMethods: this.showSessionDraft.paymentMethods,
                            paymentGateway: this.pgConfig.pgAccount.find(
                                pgAccount => this.showSessionDraft.pgAccountCode === pgAccount.code,
                            )?.gateway,
                            usePGAccountDefault: !this.showSessionDraft.pgAccountCode,
                        });
                    } else if (this.showSession) {
                        this.paymentType = this.showSession.paymentOffline ? 'paymentOffline' : 'paymentGateway';
                        this.updatePaymentForm.patchValue({
                            paymentNote: this.showSession.paymentNote,
                            pgAccountCode: this.showSession.pgAccountCode,
                            paymentMethods: this.showSession.paymentMethods,
                            paymentGateway: this.pgConfig.pgAccount.find(
                                pgAccount => this.showSession.pgAccountCode === pgAccount.code,
                            )?.gateway,
                            usePGAccountDefault: !this.showSession.pgAccountCode,
                        });
                    }
                },
                error => {
                    this.toastrService.show(
                        getErrorMessageFromResponse(error),
                        'Load thông tin pg config thất bại, vui lòng tải lại trang',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
            );
    }

    get paymentNoteControl(): FormControl {
        return this.updatePaymentForm.controls.paymentNote;
    }

    get paymentGatewayControl(): FormControl {
        return this.updatePaymentForm.controls.paymentGateway;
    }

    get pgAccountCodeControl(): FormControl {
        return this.updatePaymentForm.controls.pgAccountCode;
    }

    get paymentMethodsControl(): FormControl {
        return this.updatePaymentForm.controls.paymentMethods;
    }

    get usePGAccountDefaultControl(): FormControl {
        return this.updatePaymentForm.controls.usePGAccountDefault;
    }

    onSubmit() {
        if (this.paymentType === 'paymentOffline' && this.paymentNoteControl.invalid) {
            this.paymentNoteControl.markAsDirty();
            this.toastrService.show(
                'Cập nhật thông tin payment thất bại, verify fields sai',
                'Cập nhật thông tin payment thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }
        if (this.showSessionDraft) {
            this.loading += 1;
            this.adminService
                .updateShowSessionDraftPayment$(
                    this.showSessionDraft.id,
                    this.paymentType === 'paymentOffline',
                    this.paymentNoteControl.value,
                    this.usePGAccountDefaultControl.value ? undefined : this.pgAccountCodeControl.value,
                    this.paymentMethodsControl.value,
                )
                .pipe(
                    tap(
                        e => {
                            this.loading -= 1;
                        },
                        e => {
                            this.loading -= 1;
                        },
                    ),
                )
                .subscribe(
                    model => {
                        this.updateSessionDraftSucceed.emit(model);
                        this.toastrService.show(
                            'Cập nhật thông tin payment thành công',
                            'Cập nhật thông tin payment thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        this.toastrService.show(
                            getErrorMessageFromResponse(error),
                            'Cập nhật thông tin payment thất bại',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    },
                );
        } else if (this.showSession) {
            this.loading += 1;
            this.showService
                .updateShowSessionPayment$(
                    this.showId,
                    this.showSession.id,
                    this.paymentType === 'paymentOffline',
                    this.paymentNoteControl.value,
                    this.usePGAccountDefaultControl.value ? undefined : this.pgAccountCodeControl.value,
                    this.paymentMethodsControl.value,
                )
                .pipe(
                    tap(
                        e => {
                            this.loading -= 1;
                        },
                        e => {
                            this.loading -= 1;
                        },
                    ),
                )
                .subscribe(
                    model => {
                        this.updateSessionSucceed.emit(model);
                        this.toastrService.show(
                            'Cập nhật thông tin payment thành công',
                            'Cập nhật thông tin payment thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        this.toastrService.show(
                            getErrorMessageFromResponse(error),
                            'Cập nhật thông tin payment thất bại',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    },
                );
        }
    }

    updatePaymentMethodsValue(value: PaymentType[]): void {
        this.paymentMethodsControl.setValue(value);
    }

    updatePaymentType(type: 'paymentOffline' | 'paymentGateway') {
        this.paymentType = type;
    }

    async onInputPaymentGatewayChange(value: string) {
        this.filteredPGList$.next(this.filterPaymentGatewayType(value));
    }

    async onInputPaymentAccountChange(value: string) {
        if (!this.paymentGatewayControl.value) this.filteredPGAccountList$.next(undefined);
        else this.filteredPGAccountList$.next(this.filterPaymentGatewayAccount(value));
    }

    onSelectionPGChange($event: string) {
        if (!this.inputPaymentGateway) return;

        this.pgAccountList = this.pgConfig.pgAccount;

        this.filteredPGList$.next(this.paymentGatewayList.filter(s => s === $event));
        this.pgAccountList = this.pgConfig.pgAccount.filter(a => a.gateway === this.paymentGatewayControl.value);
        this.filteredPGAccountList$.next(this.pgAccountList);
        if (this.pgAccountList.length === 1) {
            this.pgAccountCodeControl.setValue(this.pgAccountList[0].code);
        } else {
            this.pgAccountCodeControl.setValue('');
        }
        this.pgAccountCodeControl.enable();
    }

    onSelectionPGAccountChange($event: string) {
        if (!this.inputPGAccountCode?.nativeElement?.value) return;

        this.filteredPGAccountList$.next(this.pgAccountList.filter(s => s.code === $event));
    }

    private filterPaymentGatewayType(input: string): PaymentGatewayType[] {
        const filterValue = removeAccents(input.toLowerCase());
        return this.paymentGatewayList.filter(
            optionValue => !input || input === '' || removeAccents(optionValue.toLowerCase()).indexOf(filterValue) > -1,
        );
    }

    private filterPaymentGatewayAccount(input: string): PGAccountModel[] {
        const filterValue = removeAccents(input.toLowerCase());
        return this.pgAccountList.filter(
            optionValue =>
                !input ||
                input === '' ||
                removeAccents(optionValue.name?.toLowerCase() || '').indexOf(filterValue) > -1 ||
                removeAccents(optionValue.code?.toLowerCase() || '').indexOf(filterValue) > -1,
        );
    }
}
