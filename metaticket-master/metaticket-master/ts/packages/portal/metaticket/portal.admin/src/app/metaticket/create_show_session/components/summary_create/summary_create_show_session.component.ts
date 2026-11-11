import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { tap } from 'rxjs/operators';
import { ShowSessionDraftModel } from '../../../../@core/metaticket/model';
import { AdminService } from '../../../../@core/metaticket/data.service';
import { getErrorMessageFromResponse } from '../../../../@core/metaticket/utils/error.response.utils';

@Component({
    templateUrl: './summary_create_show_session.component.html',
    styleUrls: ['./summary_create_show_session.component.scss'],
    selector: 'summary-create-show-session',
})
export class SummaryCreateShowSessionComponent {
    @Input()
    showSessionDraft!: ShowSessionDraftModel;
    @Output() updateSucceed: EventEmitter<void> = new EventEmitter<void>();

    loading: number = 0;

    constructor(
        private adminService: AdminService,
        private toastrService: NbToastrService,
    ) {}

    onBtnClick() {
        if (!this.showSessionDraft.sessionId) {
            this.loading += 1;
            this.adminService
                .createShowSession$(this.showSessionDraft.id)
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
                        this.showSessionDraft.sessionId = model.sessionId;
                        this.toastrService.show('Tạo session thành công', 'Tạo session thành công', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'success',
                        });
                        this.updateSucceed.emit();
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Tạo show session thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        } else {
            this.loading += 1;
            this.adminService
                .updateShowSession$(this.showSessionDraft.id)
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
                        this.toastrService.show('Hiện tại chưa cho phép cập nhật session', 'Denied', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Cập nhật session thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }
}
