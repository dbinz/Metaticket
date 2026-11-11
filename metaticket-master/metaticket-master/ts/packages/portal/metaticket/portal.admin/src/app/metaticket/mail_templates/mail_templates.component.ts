import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { NotificationService } from '../../@core/metaticket/data.service';
import { MailTemplateModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { IColumnType, LocalDataSource, Settings } from 'angular2-smart-table';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './mail_templates.component.html',
    selector: 'mail-templates',
})
export class MailTemplatesComponent {
    source: LocalDataSource = new LocalDataSource();

    settings: Settings = {
        rowClassFunction: row => 'ng2-custom-actions-inline',
        hideSubHeader: true,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
            custom: [
                {
                    name: 'edit-template',
                    title: '<i class="eva eva-edit-2-outline" title="Edit"></i>',
                },
            ],
            position: 'right',
        },
        columns: {
            id: {
                title: 'Id',
                type: IColumnType.Text,
            },
        },
    };

    loading: number = 0;

    constructor(
        private notificationService: NotificationService,
        private router: Router,
        private toastrService: NbToastrService,
    ) {
        this.loading += 1;
        notificationService
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
                templates => {
                    if (!templates) return;
                    this.source.load(templates);
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

    onCustomButton(event: { action: 'edit-template'; data: MailTemplateModel }) {
        switch (event.action) {
            case 'edit-template': {
                this.router.navigateByUrl('/portal/mail/template/update?id=' + event.data.id);
                break;
            }
            default: {
                break;
            }
        }
    }
}
