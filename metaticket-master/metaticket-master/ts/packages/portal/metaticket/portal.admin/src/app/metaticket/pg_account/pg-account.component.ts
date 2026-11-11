import { NgIf } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
    NbButtonModule,
    NbCardModule,
    NbGlobalPhysicalPosition,
    NbSpinnerModule,
    NbToastrService,
} from '@nebular/theme';
import { PaymentService } from '../../@core/metaticket/data.service';
import { PGAccountModel, PGConfigModel } from '../../@core/metaticket/model';
import { Angular2SmartTableModule, LocalDataSource, ViewCell, Settings, IColumnType } from 'angular2-smart-table';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './pg-account.component.html',
    standalone: true,
    imports: [NbCardModule, Angular2SmartTableModule, RouterLink, NbSpinnerModule, NbButtonModule],
})
export class PGAccountComponent {
    source: LocalDataSource = new LocalDataSource();
    loading: number = 0;

    settings: Settings = {
        hideSubHeader: false,
        actions: {
            columnTitle: 'Actions',
            add: false,
            edit: false,
            delete: false,
        },
        columns: {
            button: {
                filter: false,
                isSortable: false,
                title: 'Actions',
                type: IColumnType.Custom,
                renderComponent: ButtonViewComponent,
                onComponentInitFunction(instance: { action: { subscribe: (arg0: (event: any) => void) => void } }) {
                    instance.action.subscribe(event => {
                        event.data.comp.onCustomButton(event);
                    });
                },
            },
            index: {
                title: 'STT',
                filter: false,
                type: IColumnType.Html,
            },
            gateway: {
                title: 'Gateway',
                type: IColumnType.Text,
            },
            code: {
                title: 'Code',
                type: IColumnType.Text,
            },
            name: {
                title: 'Tên',
                type: IColumnType.Text,
            },
            description: {
                title: 'Description',
                type: IColumnType.Text,
            },
        },
    };

    constructor(
        private router: Router,
        private paymentService: PaymentService,
        private toastrService: NbToastrService,
    ) {
        this.loading += 1;
        paymentService
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
                    this.loadData(pgConfig);
                },
                err => {
                    console.error('Cannot load pg accounts', err);
                    this.toastrService.show(`Load pg account thất bại, vui lòng tải lại trang`, 'Thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    private loadData(pgConfig: PGConfigModel) {
        if (!pgConfig) return;
        this.source.load(
            pgConfig.pgAccount.map((pgAccount, index) => {
                const isDefault = pgAccount.code === pgConfig.pgAccountDefaultCode;
                return <Object>{
                    comp: this,
                    default: isDefault,
                    index: `<strong>${index + 1}` + `${isDefault ? ' (default)' : ''}</strong>`,
                    gateway: pgAccount.gateway,
                    code: pgAccount.code,
                    name: pgAccount.name,
                    description: pgAccount.description,
                };
            }),
        );
    }

    onCustomButton(event: { action: 'detail' | 'default'; data: PGAccountModel }) {
        switch (event.action) {
            case 'detail': {
                this.router.navigateByUrl(
                    `/portal/pg-account/${event.data.gateway.toLowerCase()}/detail?code=${event.data.code}`,
                );
                break;
            }
            case 'default': {
                this.loading += 1;
                this.paymentService
                    .setDefaultPGAccount(event.data.code)
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
                            this.loadData(pgConfig);
                            this.toastrService.show(
                                `Set Default payment gateway account ${event.data.code} thành công`,
                                'Thành công',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                            );
                        },
                        err => {
                            console.error('Cannot set default pg account', err);
                            this.toastrService.show(`Set default pg account ${event.data.code} thất bại`, 'Thất bại', {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: 'danger',
                                duration: 30000,
                            });
                        },
                    );
                break;
            }
            default: {
                break;
            }
        }
    }
}

@Component({
    selector: 'button-view',
    template: `
        <div class="d-flex justify-content-center" style="font-size: 20px">
            <i
                (click)="onAction('detail')"
                class="eva eva-eye-outline mr-2"
                title="Detail"
                [style.cursor]="'pointer'"></i>
            <i
                *ngIf="!rowData.default"
                (click)="onAction('default')"
                class="eva eva-star-outline"
                title="Set Default"
                [style.cursor]="'pointer'"></i>
            <i *ngIf="rowData.default" class="eva eva-star"></i>
        </div>
    `,
    standalone: true,
    imports: [NgIf],
})
class ButtonViewComponent implements ViewCell {
    @Input() value!: string | number;
    @Input() rowData: any;

    @Output() action: EventEmitter<{ action: 'detail' | 'default'; data: any }> = new EventEmitter();

    onAction(action: 'detail' | 'default') {
        this.action.emit({ action: action, data: this.rowData });
    }
}
