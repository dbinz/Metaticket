import { Component } from '@angular/core';
import { FormBuilder, FormControl, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { NotificationService } from '../../@core/metaticket/data.service';
import { MailTemplateModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './create_mail_template.component.html',
    styleUrls: ['./create_mail_template.component.scss'],
    selector: 'create-mail-template',
})
export class CreateMailTemplateComponent {
    editMailTemplateForm = this.formBuilder.group({
        id: new FormControl(undefined, Validators.required),
        root: new FormControl(undefined, Validators.required),
        customFieldItem: new FormControl(undefined),
        priceItem: new FormControl(undefined, Validators.required),
        promotionItem: new FormControl(undefined, Validators.required),
        ticketPdfTemplate: new FormControl(undefined),
        ticketPdfFileName: new FormControl(undefined),
    });

    loading: number = 0;
    template: MailTemplateModel | null = null;

    constructor(
        private formBuilder: FormBuilder,
        private notificationService: NotificationService,
        private toastrService: NbToastrService,
        private activatedRoute: ActivatedRoute,
    ) {
        const id = activatedRoute.snapshot.queryParams['id'];
        if (id && id.toString().trim()) {
            notificationService
                .getMailTemplate(id)
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
                    (template: MailTemplateModel) => {
                        if (!template) return;
                        this.template = template;
                        this.idFormControl.setValue(template.id);
                        this.rootFormControl.setValue((template.templates as any).root);
                        this.customFieldItemFormControl.setValue((template.templates as any).customFieldItem);
                        this.priceItemFormControl.setValue((template.templates as any).priceItem);
                        this.promotionItemFormControl.setValue((template.templates as any).promotionItem);
                        if (template.attachments) {
                            if (template.attachments['ticketPdf']) {
                                this.ticketPdfTemplateFormControl.setValue(
                                    template.attachments['ticketPdf']['template'],
                                );
                                this.ticketPdfFileNameFormControl.setValue(
                                    template.attachments['ticketPdf']['filename'],
                                );
                            }
                        }
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Load mail template thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }

    get idFormControl(): FormControl {
        return <FormControl>this.editMailTemplateForm.get('id');
    }

    get rootFormControl(): FormControl {
        return <FormControl>this.editMailTemplateForm.get('root');
    }

    get customFieldItemFormControl(): FormControl {
        return <FormControl>this.editMailTemplateForm.get('customFieldItem');
    }

    get priceItemFormControl(): FormControl {
        return <FormControl>this.editMailTemplateForm.get('priceItem');
    }

    get promotionItemFormControl(): FormControl {
        return <FormControl>this.editMailTemplateForm.get('promotionItem');
    }

    get ticketPdfTemplateFormControl(): FormControl {
        return <FormControl>this.editMailTemplateForm.get('ticketPdfTemplate');
    }

    get ticketPdfFileNameFormControl(): FormControl {
        return <FormControl>this.editMailTemplateForm.get('ticketPdfFileName');
    }

    onSubmit() {
        if (this.editMailTemplateForm.invalid) {
            this.editMailTemplateForm.markAsDirty();
            this.toastrService.show('Vui lòng kiểm tra lại', `Dữ liệu nhập không đúng`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }
        this.loading += 1;
        if (!this.template) {
            this.notificationService
                .createMailTemplate(
                    this.idFormControl.value,
                    this.rootFormControl.value,
                    this.customFieldItemFormControl.value,
                    this.priceItemFormControl.value,
                    this.promotionItemFormControl.value,
                    this.ticketPdfTemplateFormControl.value,
                    this.ticketPdfFileNameFormControl.value,
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
                        if (model) {
                            this.toastrService.show('Tạo mail template thành công', 'Tạo mail template thành công', {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: 'success',
                            });
                        }
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Tạo mail template thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        } else {
            this.notificationService
                .updateMailTemplate(
                    this.idFormControl.value,
                    this.rootFormControl.value,
                    this.customFieldItemFormControl.value,
                    this.priceItemFormControl.value,
                    this.promotionItemFormControl.value,
                    this.ticketPdfTemplateFormControl.value,
                    this.ticketPdfFileNameFormControl.value,
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
                        console.log(model);
                        if (model) {
                            this.toastrService.show(
                                'Cập nhật mail template thành công',
                                'Cập nhật mail template thành công',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                            );
                        }
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Cập nhật mail template thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }
}
