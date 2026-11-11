import { NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
    NbButtonModule,
    NbCardModule,
    NbCheckboxModule,
    NbDialogService,
    NbGlobalPhysicalPosition,
    NbIconModule,
    NbInputModule,
    NbSpinnerModule,
    NbToastrService,
} from '@nebular/theme';
import { ReplaySubject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { PaymentService } from '../../../@core/metaticket/data.service';
import { NinePayAccountModel, UpdateNinePayAccountRequest } from '../../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../../@core/metaticket/utils/error.response.utils';
import { DialogConfirmComponent } from '../../dialog/dialog.confirm/dialog.confirm.component';

@Component({
    templateUrl: './detail-ninepay-account.component.html',
    styleUrls: ['../pg-account.component.scss'],
    standalone: true,
    imports: [
        NbCardModule,
        ReactiveFormsModule,
        NbCheckboxModule,
        NbInputModule,
        NbButtonModule,
        NbSpinnerModule,
        NgIf,
        NbIconModule,
    ],
})
export class DetailNinePayAccountComponent {
    formGroup = this.formBuilder.group({
        code: new FormControl(undefined, Validators.required),
        enabled: new FormControl(undefined, Validators.required),
        name: new FormControl(undefined, Validators.required),
        description: new FormControl(undefined, Validators.required),
        baseUrl: new FormControl(undefined, Validators.required),
        returnUrl: new FormControl(undefined, Validators.required),
        ipnUrl: new FormControl(undefined, Validators.required),
    });

    loading = 0;

    editMode = false;
    code: string;

    constructor(
        private router: Router,
        private route: ActivatedRoute,
        private formBuilder: FormBuilder,
        private paymentService: PaymentService,
        private toastrService: NbToastrService,
        private dialogService: NbDialogService,
    ) {
        const code = this.route.snapshot.queryParamMap.get('code');
        this.loading += 1;
        this.paymentService
            .getPGAccount(code)
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
                (pgAccount: NinePayAccountModel) => {
                    this.assignPGAccount(pgAccount);
                },
                error => {
                    this.toastrService.show(
                        getErrorMessageFromResponse(error),
                        'Load payment gateway account thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
            );
    }

    private assignPGAccount(pgAccount: NinePayAccountModel) {
        this.formGroup = this.formBuilder.group({
            code: new FormControl(pgAccount.code, Validators.required),
            enabled: new FormControl(pgAccount.enabled, Validators.required),
            name: new FormControl(pgAccount.name, Validators.required),
            description: new FormControl(pgAccount.description, Validators.required),
            baseUrl: new FormControl(pgAccount.baseUrl, Validators.required),
            returnUrl: new FormControl(pgAccount.returnUrl, Validators.required),
            ipnUrl: new FormControl(pgAccount.ipnUrl, Validators.required),
        });
    }

    get codeControl(): FormControl {
        return this.formGroup.controls.code;
    }

    get enabledControl(): FormControl {
        return this.formGroup.controls.enabled;
    }

    get nameControl(): FormControl {
        return this.formGroup.controls.name;
    }

    get descriptionControl(): FormControl {
        return this.formGroup.controls.description;
    }

    get baseUrlControl(): FormControl {
        return this.formGroup.controls.baseUrl;
    }

    get returnUrlControl(): FormControl {
        return this.formGroup.controls.returnUrl;
    }

    get ipnUrlControl(): FormControl {
        return this.formGroup.controls.ipnUrl;
    }

    update() {
        if (this.formGroup.invalid) {
            this.formGroup.markAsDirty();
            console.error(`Cập nhật payment gateway account ${this.codeControl.value} thất bại, verify field sai`);
            this.toastrService.show(
                `Cập nhật payment gateway account ${this.codeControl.value} thất bại, verify field bị sai`,
                'Thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }
        const request: UpdateNinePayAccountRequest = {
            code: this.codeControl.value,
            name: this.nameControl.value.trim(),
            description: this.descriptionControl.value.trim(),
            baseUrl: this.baseUrlControl.value.trim(),
        };

        this.loading += 1;
        this.paymentService
            .updateNinePayAccount(request)
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
                pgAccount => {
                    console.log(`Cập nhật payment gateway account ${this.codeControl.value} thành công`);
                    this.toastrService.show(
                        `Cập nhật payment gateway account ${this.codeControl.value} thành công`,
                        'Thành công',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                    );
                    this.editMode = false;
                },
                err => {
                    console.error(`Cập nhật payment gateway account ${this.codeControl.value} thất bại`, err);
                    this.toastrService.show(
                        `Cập nhật payment gateway account ${this.codeControl.value} thất bại`,
                        'Thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
            );
    }

    enable() {
        this.loading += 1;
        this.paymentService
            .enablePGAccount(this.codeControl.value)
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
                pgAccount => {
                    console.log(`Enable payment gateway account ${this.codeControl.value} thành công`);
                    this.toastrService.show(
                        `Enable payment gateway account ${this.codeControl.value} thành công`,
                        'Thành công',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                    );
                    this.editMode = false;
                    this.assignPGAccount(pgAccount);
                },
                err => {
                    console.error(`Enable payment gateway account ${this.codeControl.value} thất bại`, err);
                    this.toastrService.show(
                        `Enable payment gateway account ${this.codeControl.value} thất bại`,
                        'Thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
            );
    }

    disable() {
        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: 'Xác nhận disable payment gateway account',
                content: 'Bạn có chắc chắn muốn disable payment gateway account hay không?',
                onAction$: onAction$,
            },
        });
        onAction$.subscribe(act => {
            if (act === 'confirm') {
                this.loading += 1;
                this.paymentService
                    .disablePGAccount(this.codeControl.value)
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
                        pgAccount => {
                            console.log(`Disable payment gateway account ${this.codeControl.value} thành công`);
                            this.toastrService.show(
                                `Disable payment gateway account ${this.codeControl.value} thành công`,
                                'Thành công',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                            );
                            this.editMode = false;
                            this.assignPGAccount(pgAccount);
                        },
                        err => {
                            console.error(`Disable payment gateway account ${this.codeControl.value} thất bại`, err);
                            this.toastrService.show(
                                `Disable payment gateway account ${this.codeControl.value} thất bại`,
                                'Thất bại',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                            );
                        },
                    );
            }
        });
    }

    remove() {
        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: 'Xác nhận xóa payment gateway account',
                content: 'Bạn có chắc chắn muốn xóa payment gateway account hay không?',
                onAction$: onAction$,
            },
        });
        onAction$.subscribe(act => {
            if (act === 'confirm') {
                this.loading += 1;
                this.paymentService
                    .deletePGAccount(this.codeControl.value)
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
                        pgAccount => {
                            console.log(`Xóa payment gateway account ${this.codeControl.value} thành công`);
                            this.toastrService.show(
                                `Xóa payment gateway account ${this.codeControl.value} thành công`,
                                'Thành công',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                            );
                            this.editMode = false;
                            this.assignPGAccount(pgAccount);
                        },
                        err => {
                            console.error(`Xóa payment gateway account ${this.codeControl.value} thất bại`, err);
                            this.toastrService.show(
                                `Xóa payment gateway account ${this.codeControl.value} thất bại`,
                                'Thất bại',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                            );
                        },
                    );
            }
        });
    }
}
