import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { AdminService, OrderService } from '@admin.portal/@core/metaticket/data.service';
import {
    CustomFieldTmpl,
    CustomFieldType,
    ShowSessionDraftModel,
    ShowSessionModel,
} from '@admin.portal/@core/metaticket/model';
import { getErrorMessageFromResponse } from '@admin.portal/@core/metaticket/utils/error.response.utils';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './update_order_custom_fields.component.html',
    styleUrls: ['./update_order_custom_fields.component.scss'],
    selector: 'update-order-custom-fields',
})
export class UpdateOrderCustomFieldsComponent implements AfterViewInit {
    @Input() showSessionDraft?: ShowSessionDraftModel;
    @Input() showSession?: ShowSessionModel;
    @Input() showId?: string;
    @Output() gotoNextStep: EventEmitter<void> = new EventEmitter<void>();
    customFieldTypeValue: string;
    customFieldsForm = this.formBuilder.group({
        customFields: this.formBuilder.array([]),
    });
    sessionDraftOrderCustomFieldId: string;
    customFields: CustomFieldTmpl[] = [];

    loading: number = 0;

    constructor(
        private formBuilder: FormBuilder,
        private adminService: AdminService,
        private orderService: OrderService,
        private toastrService: NbToastrService,
    ) {}

    ngAfterViewInit(): void {
        if (this.showSessionDraft) {
            console.log(this.showSessionDraft);
            this.loading += 1;
            this.adminService
                .getShowSessionsDraftOrderCustomFields$(this.showSessionDraft.id)
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
                    data => {
                        this.sessionDraftOrderCustomFieldId = data.id;
                        this.customFields = data.customFields;
                        for (let i = 0; i < data.customFields.length; i++) {
                            const customField = data.customFields[i];
                            switch (customField.type) {
                                case CustomFieldType.String:
                                    this.addCustomFieldString(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                                case CustomFieldType.Number:
                                    this.addCustomFieldNumber(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                                case CustomFieldType.Date:
                                    this.addCustomFieldDate(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                                case CustomFieldType.List:
                                    this.addCustomFieldList(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                                case CustomFieldType.Boolean:
                                    this.addCustomFieldBoolean(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                            }
                        }
                    },
                    (error: HttpErrorResponse) => {
                        if (error.status !== 404) {
                            this.toastrService.show(
                                'Load custom fields thất bại, vui lòng tải lại trang',
                                'Load custom fields thất bại',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                            );
                        }
                    },
                );
        } else if (this.showSession) {
            this.loading += 1;
            this.orderService
                .getOrderCustomFields(this.showId, this.showSession.id)
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
                    data => {
                        this.customFields = data;
                        for (let i = 0; i < data.length; i++) {
                            const customField = data[i];
                            switch (customField.type) {
                                case CustomFieldType.String:
                                    this.addCustomFieldString(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                                case CustomFieldType.Number:
                                    this.addCustomFieldNumber(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                                case CustomFieldType.Date:
                                    this.addCustomFieldDate(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                                case CustomFieldType.List:
                                    this.addCustomFieldList(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                                case CustomFieldType.Boolean:
                                    this.addCustomFieldBoolean(
                                        customField.id,
                                        customField.displayName,
                                        customField.note,
                                        customField.constraints,
                                    );
                                    break;
                            }
                        }
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Load custom fields thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }

    getCustomFields() {
        return (this.customFieldsForm.get('customFields') as FormArray).controls as FormGroup[];
    }

    onCustomFieldTypeItemChange(value: string) {
        this.customFieldTypeValue = value;
    }
    onBtnCreateCustomFieldPressed() {
        switch (this.customFieldTypeValue) {
            case CustomFieldType.String:
                this.addCustomFieldString('', '', '');
                break;
            case CustomFieldType.Number:
                this.addCustomFieldNumber('', '', '');
                break;
            case CustomFieldType.Date:
                this.addCustomFieldDate('', '', '');
                break;
            case CustomFieldType.List:
                this.addCustomFieldList('', '', '');
                break;
            case CustomFieldType.Boolean:
                this.addCustomFieldBoolean('', '', '');
                break;
        }
    }
    addCustomFieldString(id: string, displayName: string, note: string, constraints?: string) {
        (<FormArray>this.customFieldsForm.controls['customFields']).push(
            this.formBuilder.group({
                id: new FormControl(id, Validators.required),
                type: new FormControl(CustomFieldType.String, Validators.required),
                displayName: new FormControl(displayName, Validators.required),
                note: new FormControl(note),
                constraints: constraints
                    ? this.formBuilder.group({
                          regex: new FormControl(JSON.parse(constraints).regex),
                          require: new FormControl(JSON.parse(constraints).require),
                      })
                    : this.formBuilder.group({
                          regex: new FormControl(undefined),
                          require: new FormControl(false),
                      }),
            }),
        );
    }

    addCustomFieldNumber(id: string, displayName: string, note: string, constraints?: string) {
        (<FormArray>this.customFieldsForm.controls['customFields']).push(
            this.formBuilder.group({
                id: new FormControl(id, Validators.required),
                type: new FormControl(CustomFieldType.Number, Validators.required),
                displayName: new FormControl(displayName, Validators.required),
                note: new FormControl(note),
                constraints: constraints
                    ? this.formBuilder.group({
                          regex: new FormControl(JSON.parse(constraints).regex),
                          require: new FormControl(JSON.parse(constraints).require),
                      })
                    : this.formBuilder.group({
                          regex: new FormControl(undefined),
                          require: new FormControl(false),
                      }),
            }),
        );
    }

    addCustomFieldDate(id: string, displayName: string, note: string, constraints?: string) {
        (<FormArray>this.customFieldsForm.controls['customFields']).push(
            this.formBuilder.group({
                id: new FormControl(id, Validators.required),
                type: new FormControl(CustomFieldType.Date, Validators.required),
                displayName: new FormControl(displayName, Validators.required),
                note: new FormControl(note),
                constraints: constraints
                    ? this.formBuilder.group({
                          require: new FormControl(JSON.parse(constraints).require),
                      })
                    : this.formBuilder.group({
                          require: new FormControl(false),
                      }),
            }),
        );
    }

    addCustomFieldList(id: string, displayName: string, note: string, constraints?: string) {
        const constraintsFormGroup = this.formBuilder.group({
            require: new FormControl(false),
            options: this.formBuilder.array([]),
        });
        if (constraints) {
            const constraintsParse = JSON.parse(constraints);
            constraintsFormGroup.patchValue({ require: constraintsParse.require });
            for (let i = 0; i < constraintsParse.options.length; i++) {
                (<FormArray>constraintsFormGroup.controls['options']).push(
                    this.formBuilder.group({
                        name: new FormControl(constraintsParse.options[i].name, Validators.required),
                        value: new FormControl(constraintsParse.options[i].value, Validators.required),
                    }),
                );
            }
        }
        (<FormArray>this.customFieldsForm.controls['customFields']).push(
            this.formBuilder.group({
                id: new FormControl(id, Validators.required),
                type: new FormControl(CustomFieldType.List, Validators.required),
                displayName: new FormControl(displayName, Validators.required),
                note: new FormControl(note),
                constraints: constraintsFormGroup,
            }),
        );
    }

    addCustomFieldBoolean(id: string, displayName: string, note: string, constraints?: string) {
        (<FormArray>this.customFieldsForm.controls['customFields']).push(
            this.formBuilder.group({
                id: new FormControl(id, Validators.required),
                type: new FormControl(CustomFieldType.Boolean, Validators.required),
                displayName: new FormControl(displayName, Validators.required),
                note: new FormControl(note),
                constraints: constraints
                    ? this.formBuilder.group({
                          require: new FormControl(JSON.parse(constraints).require),
                      })
                    : this.formBuilder.group({
                          require: new FormControl(false),
                      }),
            }),
        );
    }

    removeItem(index: number) {
        (this.customFieldsForm.get('customFields') as FormArray).removeAt(index);
    }

    onSubmit() {
        if (this.customFieldsForm.invalid) {
            this.customFieldsForm.markAsDirty();
            this.toastrService.show('Vui lòng kiểm tra lại', `Dữ liệu nhập không đúng`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }
        const customFields = this.customFieldsForm.getRawValue().customFields;
        for (let i = 0; i < customFields.length; i++) {
            let constraints = (customFields[i] as any).constraints;
            constraints = JSON.stringify(constraints);
            (customFields[i] as any).constraints = constraints;
        }
        if (this.showSession) {
            this.loading += 1;
            this.orderService
                .updateOrderCustomFields(this.showId, this.showSession?.id ?? '', customFields)
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
                        console.log(model);
                        this.toastrService.show('Cập nhật custom fields thành công', 'Cập nhật thành công', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'success',
                        });
                        this.gotoNextStep.emit();
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Cập nhật thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        } else if (this.sessionDraftOrderCustomFieldId === undefined) {
            this.loading += 1;
            this.adminService
                .createShowSessionDraftOrderCustomFields$(this.showSessionDraft?.id ?? '', customFields)
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
                        this.toastrService.show('Lưu nháp custom fields thành công', 'Lưu nháp thành công', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'success',
                        });
                        this.gotoNextStep.emit();
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Lưu nháp thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        } else {
            this.loading += 1;
            this.adminService
                .updateShowSessionDraftOrderCustomFields$(this.sessionDraftOrderCustomFieldId, customFields)
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
                        this.toastrService.show('Lưu nháp custom fields thành công', 'Lưu nháp thành công', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'success',
                        });
                        this.gotoNextStep.emit();
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Lưu nháp thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }

    protected readonly CustomFieldType = CustomFieldType;
}
