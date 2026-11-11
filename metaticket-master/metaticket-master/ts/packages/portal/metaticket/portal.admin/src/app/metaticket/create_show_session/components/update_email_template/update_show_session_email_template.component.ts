import { AfterViewInit, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { AdminService, NotificationService, ShowService } from '@admin.portal/@core/metaticket/data.service';
import { OrderStatus, ShowSessionDraftModel, ShowSessionModel } from '@admin.portal/@core/metaticket/model';
import { MailTemplateModel } from '@admin.portal/@core/metaticket/model/notification.model';
import { getErrorMessageFromResponse } from '@admin.portal/@core/metaticket/utils/error.response.utils';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './update_show_session_email_template.component.html',
    styleUrls: ['./update_show_session_email_template.component.scss'],
    selector: 'update-show-session-email-template',
})
export class UpdateEmailTemplateComponent implements AfterViewInit {
    @Input() showSessionDraft?: ShowSessionDraftModel;
    @Input() showSession?: ShowSessionModel;
    @Input() showId?: string;
    @Output() updateSessionDraftSucceed: EventEmitter<ShowSessionDraftModel> =
        new EventEmitter<ShowSessionDraftModel>();
    @Output() updateSessionSucceed: EventEmitter<ShowSessionModel> = new EventEmitter<ShowSessionModel>();
    mailTemplates: MailTemplateModel[] = [];
    @Output() gotoNextStep: EventEmitter<void> = new EventEmitter<void>();
    mailPlugins: String[] = [];
    orderStatus: string;
    sessionDraftMailTemplatesForm = this.formBuilder.group({
        items: this.formBuilder.array([]),
    });
    orderStatusArray = [
        {
            name: 'Created',
            value: OrderStatus.Created,
        },
        {
            name: 'WaitingForPayment',
            value: OrderStatus.WaitingForPayment,
        },
        {
            name: 'PaymentProcessing',
            value: OrderStatus.PaymentProcessing,
        },
        {
            name: 'PaymentSucceed',
            value: OrderStatus.PaymentSucceed,
        },
        {
            name: 'PaymentFailed',
            value: OrderStatus.PaymentFailed,
        },
        {
            name: 'PaymentCancelled',
            value: OrderStatus.PaymentCancelled,
        },
        {
            name: 'PaymentRefunded',
            value: OrderStatus.PaymentRefunded,
        },
        {
            name: 'Expired',
            value: OrderStatus.Expired,
        },
        {
            name: 'Failed',
            value: OrderStatus.Failed,
        },
        {
            name: 'Canceled',
            value: OrderStatus.Canceled,
        },
        {
            name: 'PaymentPending',
            value: OrderStatus.PaymentPending,
        },
    ];

    loading: number = 0;

    constructor(
        private formBuilder: FormBuilder,
        private notificationService: NotificationService,
        private adminService: AdminService,
        private showService: ShowService,
        private toastrService: NbToastrService,
    ) {}

    ngAfterViewInit(): void {
        this.loading += 1;
        this.notificationService
            .getMailTemplates()
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
                mailTemplates => {
                    this.mailTemplates = mailTemplates;
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Load mail template thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
        this.loading += 1;
        this.notificationService
            .getMailPlugins()
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
                mailPlugins => {
                    this.mailPlugins = mailPlugins;
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Load mail plugins thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );

        setTimeout(() => {
            if (this.showSessionDraft) {
                for (let i = 0; i < this.showSessionDraft.mailPluginTemplate.length; i++) {
                    const tmp = this.showSessionDraft.mailPluginTemplate[i];
                    this.addEmailTemplate(tmp.orderStatus, tmp.templateId, tmp.plugin);
                }
                // this.sessionDraftMailTemplatesForm.patchValue({
                //     items: this.showSessionDraft.paymentOffline,
                //     paymentNote: this.showSessionDraft.paymentNote,
                // });
            } else if (this.showSession) {
                for (let i = 0; i < this.showSession.mailPluginTemplate.length; i++) {
                    const tmp = this.showSession.mailPluginTemplate[i];
                    this.addEmailTemplate(tmp.orderStatus, tmp.templateId, tmp.plugin);
                }
            }
        });
    }

    onOrderStatusChange(value: string) {
        this.orderStatus = value;
    }

    addEmailTemplate(orderStatus: string, templateId: string, mailPlugin: string) {
        (<FormArray>this.sessionDraftMailTemplatesForm.controls['items']).push(
            this.formBuilder.group({
                templateId: new FormControl(templateId, Validators.required),
                orderStatus: new FormControl(orderStatus, Validators.required),
                plugin: new FormControl(mailPlugin, Validators.required),
            }),
        );
    }

    getItems() {
        return (this.sessionDraftMailTemplatesForm.get('items') as FormArray).controls as FormGroup[];
    }

    getMailTemplateFormControl(group: FormGroup) {
        return <FormControl>group.get('templateId');
    }

    getMailPluginFormControl(group: FormGroup) {
        return <FormControl>group.get('plugin');
    }

    onSubmit() {
        if (this.showSession) {
            this.loading += 1;
            this.showService
                .updateShowSessionMailTemplates$(
                    this.showId,
                    this.showSession.id,
                    this.sessionDraftMailTemplatesForm.getRawValue().items,
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
                            'Cập nhật mail template thành công',
                            'Cập nhật mail template thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Cập nhật mail template thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        } else if (this.showSessionDraft) {
            this.loading += 1;
            this.adminService
                .updateShowSessionDraftMailTemplates$(
                    this.showSessionDraft.id,
                    this.sessionDraftMailTemplatesForm.getRawValue().items,
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
                            'Cập nhật mail template nháp thành công',
                            'Cập nhật mail template nháp thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
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
