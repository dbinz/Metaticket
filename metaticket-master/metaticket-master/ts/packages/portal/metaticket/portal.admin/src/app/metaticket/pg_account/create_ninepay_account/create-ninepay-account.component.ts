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
import { PaymentService } from '@admin.portal/@core/metaticket/data.service';
import { AddNinePayAccountRequest } from '@admin.portal/@core/metaticket/model/pg.account.model';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './create-ninepay-account.component.html',
    styleUrls: ['../pg-account.component.scss'],
    standalone: true,
    imports: [NbCardModule, ReactiveFormsModule, NbCheckboxModule, NbInputModule, NbButtonModule, NbSpinnerModule],
})
export class CreateNinePayAccountComponent {
    formGroup = this.formBuilder.group({
        code: new FormControl(undefined, Validators.required),
        enabled: new FormControl(true, Validators.required),
        name: new FormControl(undefined, Validators.required),
        description: new FormControl(undefined, Validators.required),
        baseUrl: new FormControl(undefined, Validators.required),
        merchantKey: new FormControl(undefined, Validators.required),
        merchantSecretKey: new FormControl(undefined, Validators.required),
        checksumKey: new FormControl(undefined, Validators.required),
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

    get baseUrlControl(): FormControl {
        return this.formGroup.controls.baseUrl;
    }

    get merchantKeyControl(): FormControl {
        return this.formGroup.controls.merchantKey;
    }

    get merchantSecretKeyControl(): FormControl {
        return this.formGroup.controls.merchantSecretKey;
    }

    get checksumKeyControl(): FormControl {
        return this.formGroup.controls.checksumKey;
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
        const request: AddNinePayAccountRequest = {
            code: this.codeControl.value.trim(),
            enabled: this.enabledControl.value,
            name: this.nameControl.value.trim(),
            description: this.descriptionControl.value.trim(),
            baseUrl: this.baseUrlControl.value.trim(),
            merchantKey: this.merchantKeyControl.value.trim(),
            merchantSecretKey: this.merchantSecretKeyControl.value.trim(),
            checksumKey: this.checksumKeyControl.value.trim(),
        };

        this.loading += 1;
        this.paymentService
            .addNinePayAccount(request)
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
                    this.router.navigateByUrl(`/portal/pg-account/ninepay/detail?code=${pgAccount.code}`);
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
