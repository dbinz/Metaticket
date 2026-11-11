import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { AdminService, OrderService } from '@admin.portal/@core/metaticket/data.service';
import {
    OrderPromotionModel,
    ShowSessionDraftModel,
    ShowSessionDraftPromotionModel,
    ShowSessionModel,
} from '@admin.portal/@core/metaticket/model';
import { tap } from 'rxjs/operators';
import { EditPromotionDialogComponent } from './edit_promotion_dialog/edit_promotion_dialog.component';

@Component({
    templateUrl: './update_show_session_promotion.component.html',
    styleUrls: ['./update_show_session_promotion.component.scss'],
    selector: 'update-show-session-promotion',
})
export class UpdateShowSessionPromotionComponent implements AfterViewInit {
    @Input() showId?: string;
    @Input() showSession?: ShowSessionModel;
    @Input() showSessionDraft: ShowSessionDraftModel;
    @Output() updateSucceed: EventEmitter<ShowSessionDraftModel> = new EventEmitter<ShowSessionDraftModel>();
    @Output() gotoNextStep: EventEmitter<void> = new EventEmitter<void>();

    draftPromotions: ShowSessionDraftPromotionModel[] = [];
    promotions: OrderPromotionModel[] = [];

    loading: number = 0;

    constructor(
        private formBuilder: FormBuilder,
        private adminService: AdminService,
        private orderService: OrderService,
        private dialogService: NbDialogService,
        private toastrService: NbToastrService,
    ) {}

    ngAfterViewInit(): void {
        if (this.showSessionDraft) {
            this.loading += 1;
            this.adminService
                .getShowSessionsDraftPromotions$(this.showSessionDraft.id)
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
                    promotions => {
                        this.draftPromotions = promotions;
                    },
                    (error: HttpErrorResponse) => {
                        if (error.status !== 404) {
                            this.toastrService.show(
                                'Load session draft promotion thất bại, vui lòng tải lại trang',
                                'Load session draft promotion thất bại',
                                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                            );
                        }
                    },
                );
        } else if (this.showSession) {
            this.orderService.getPromotions(this.showId, this.showSession.id).subscribe(
                promotions => {
                    this.promotions = promotions;
                    console.log(promotions);
                },
                (error: HttpErrorResponse) => {
                    if (error.status !== 404) {
                        this.toastrService.show(
                            'Load session promotion thất bại, vui lòng tải lại trang',
                            'Load session promotion thất bại',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    }
                },
            );
        }
    }

    onClickAddPromotion() {
        if (this.showSession) {
            this.dialogService.open(EditPromotionDialogComponent, {
                dialogClass: 'dialog-nb-card',
                context: {
                    showId: this.showId,
                    sessionId: this.showSession?.id,
                    adminService: this.adminService,
                    orderService: this.orderService,
                    onUpdateSuccess: () => {
                        this.orderService.getPromotions(this.showId, this.showSession.id).subscribe(promotions => {
                            this.promotions = promotions;
                        });
                    },
                },
            });
        } else {
            this.dialogService.open(EditPromotionDialogComponent, {
                dialogClass: 'dialog-nb-card',
                context: {
                    sessionDraftId: this.showSessionDraft.id,
                    adminService: this.adminService,
                    orderService: this.orderService,
                    onUpdateDraftSuccess: (model: ShowSessionDraftPromotionModel) => {
                        for (let i = 0; i < this.draftPromotions.length; i++) {
                            if (this.draftPromotions[i].id === model.id) {
                                this.draftPromotions[i] = model;
                            }
                        }
                        this.loading -= 1;
                    },
                    onAddDraftSuccess: (model: ShowSessionDraftPromotionModel) => {
                        this.draftPromotions.push(model);
                        this.loading -= 1;
                    },
                },
            });
        }
    }

    onClickEditDraftPromotion(promotion?: ShowSessionDraftPromotionModel) {
        this.loading += 1;
        this.dialogService.open(EditPromotionDialogComponent, {
            dialogClass: 'dialog-nb-card',
            context: {
                sessionDraftId: this.showSessionDraft.id,
                adminService: this.adminService,
                orderService: this.orderService,
                draftPromotion: promotion,
                onUpdateDraftSuccess: (model: ShowSessionDraftPromotionModel) => {
                    for (let i = 0; i < this.draftPromotions.length; i++) {
                        if (this.draftPromotions[i].id === model.id) {
                            this.draftPromotions[i] = model;
                        }
                    }
                    this.loading -= 1;
                },
                onAddDraftSuccess: (model: ShowSessionDraftPromotionModel) => {
                    this.draftPromotions.push(model);
                    this.loading -= 1;
                },
            },
        });
    }

    onClickEditPromotion(promotion?: OrderPromotionModel) {
        this.dialogService.open(EditPromotionDialogComponent, {
            dialogClass: 'dialog-nb-card',
            context: {
                showId: this.showId,
                sessionId: this.showSession?.id,
                adminService: this.adminService,
                orderService: this.orderService,
                promotion: promotion,
                onUpdateSuccess: () => {
                    this.orderService.getPromotions(this.showId, this.showSession.id).subscribe(promotions => {
                        this.promotions = promotions;
                    });
                },
            },
        });
    }
}
