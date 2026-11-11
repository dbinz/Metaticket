import { Component } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import {
    NbButtonModule,
    NbCardModule,
    NbCheckboxModule,
    NbGlobalPhysicalPosition,
    NbInputModule,
    NbSpinnerModule,
    NbToastrService,
} from '@nebular/theme';
import { tap } from 'rxjs/operators';
import { PaymentService } from '../../../@core/metaticket/data.service';
import { AddVNPayAccountRequest } from '../../../@core/metaticket/model';

@Component({
    templateUrl: './create-vnpay-account.component.html',
    styleUrls: ['../pg-account.component.scss'],
    standalone: true,
    imports: [NbCardModule, ReactiveFormsModule, NbCheckboxModule, NbInputModule, NbButtonModule, NbSpinnerModule],
})
export class CreateVNPayAccountComponent {
    formGroup = this.formBuilder.group({
        code: new FormControl(undefined, Validators.required),
        enabled: new FormControl<boolean>(true, Validators.required),
        name: new FormControl(undefined, Validators.required),
        description: new FormControl(undefined, Validators.required),
        payUrl: new FormControl(undefined, Validators.required),
        queryUrl: new FormControl(undefined, Validators.required),
        expireInMinute: new FormControl<number>(undefined, Validators.required),
        tmnCode: new FormControl(undefined, Validators.required),
        hashSecret: new FormControl(undefined, Validators.required),
    });

    loading = 0;

    constructor(
        private router: Router,
        private formBuilder: FormBuilder,
        private paymentService: PaymentService,
        private toastrService: NbToastrService,
    ) {}

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

    get payUrlControl(): FormControl {
        return this.formGroup.controls.payUrl;
    }

    get queryUrlControl(): FormControl {
        return this.formGroup.controls.queryUrl;
    }

    get expireInMinuteControl(): FormControl {
        return this.formGroup.controls.expireInMinute;
    }

    get tmnCodeControl(): FormControl {
        return this.formGroup.controls.tmnCode;
    }

    get hashSecretControl(): FormControl {
        return this.formGroup.controls.hashSecret;
    }

    create() {
        if (this.formGroup.invalid) {
            this.formGroup.markAsDirty();
            console.error(`Tạo payment gateway account ${this.codeControl.value} thất bại, verify field sai`);
            this.toastrService.show(
                `Tạo payment gateway account ${this.codeControl.value} thất bại, verify field bị sai`,
                'Thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }
        const request: AddVNPayAccountRequest = {
            code: this.codeControl.value.trim(),
            enabled: this.enabledControl.value,
            name: this.nameControl.value.trim(),
            description: this.descriptionControl.value.trim(),
            payUrl: this.payUrlControl.value.trim(),
            queryUrl: this.queryUrlControl.value.trim(),
            expireInMinute: this.expireInMinuteControl.value,
            tmnCode: this.tmnCodeControl.value.trim(),
            hashSecret: this.hashSecretControl.value.trim(),
        };

        this.loading += 1;
        this.paymentService
            .addVNPayAccount(request)
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
                    console.log(`Tạo payment gateway account ${this.codeControl.value} thành công`);
                    this.toastrService.show(
                        `Tạo payment gateway account ${this.codeControl.value} thành công`,
                        'Thành công',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                    );
                    this.router.navigateByUrl(`/portal/pg-account/vnpay/detail?code=${pgAccount.code}`);
                },
                err => {
                    console.error(`Tạo payment gateway account ${this.codeControl.value} thất bại`, err);
                    this.toastrService.show(
                        `Tạo payment gateway account ${this.codeControl.value} thất bại`,
                        'Thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
            );
    }
}
