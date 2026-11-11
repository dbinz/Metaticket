import { AfterContentInit, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { FormBuilder, FormControl, Validators } from '@angular/forms';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { AdminService, ShowService } from '@admin.portal/@core/metaticket/data.service';
import { SeatplanCategoryModel, ShowSessionDraftModel, ShowSessionModel } from '@admin.portal/@core/metaticket/model';
import { getErrorMessageFromResponse } from '@admin.portal/@core/metaticket/utils/error.response.utils';
import { MetaticketPolicyDescService } from '../../../service/metaticket.policydesc.service';
import { MetaticketSeatplannerService } from '../../../service/metaticket.seatplanner.service';
import { Observable, ReplaySubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './update_show_session_seat_plan.component.html',
    styleUrls: ['./update_show_session_seat_plan.component.scss'],
    selector: 'update-show-session-seat-plan',
})
export class UpdateShowSessionSeatPlanComponent implements AfterContentInit {
    @Input() showSessionDraft?: ShowSessionDraftModel;
    @Input() showSession?: ShowSessionModel;
    @Input() showId?: string;
    @Output() updateSessionDraftSucceed: EventEmitter<ShowSessionDraftModel> = new EventEmitter();
    @Output() updateSessionSucceed: EventEmitter<ShowSessionModel> = new EventEmitter();
    @Output() gotoNextStep: EventEmitter<void> = new EventEmitter<void>();
    filteredMapList$: Subject<SeatplanCategoryModel[]> = new ReplaySubject();
    mapList: SeatplanCategoryModel[] = [];

    @ViewChild('autoInputMap') inputMap;

    updateSeatPlanForm = this.formBuilder.group({
        edType: new FormControl('', Validators.required),
        seatPlanId: new FormControl({ value: '', disabled: true }),
        extraInfo: new FormControl(''),
    });

    loading: number = 0;

    constructor(
        private formBuilder: FormBuilder,
        private adminService: AdminService,
        private showService: ShowService,
        private toastrService: NbToastrService,
        private seatPlannerService: MetaticketSeatplannerService,
        private policyDescService: MetaticketPolicyDescService,
    ) {}

    /** Vé theo lịch chỉ bán vé số lượng (PolicyConfig), không có sơ đồ ghế — backend từ chối cấu hình khác. */
    get isCalendar(): boolean {
        return (this.showSessionDraft ?? this.showSession)?.sessionType === 'CALENDAR';
    }

    ngAfterContentInit(): void {
        if (this.isCalendar) {
            const source = this.showSessionDraft ?? this.showSession;
            this.updateSeatPlanForm.patchValue({
                edType: 'PolicyConfig',
                seatPlanId: '',
                extraInfo: source.extraInfo || '',
            });
            this.edTypeFormControl.disable();
            return;
        }
        let seatMap: Observable<SeatplanCategoryModel[]>;
        if (this.showSessionDraft) {
            this.updateSeatPlanForm.patchValue({
                seatPlanId: this.showSessionDraft.seatPlanId,
                edType: this.showSessionDraft.edType,
                extraInfo: this.showSessionDraft.extraInfo || '',
            });

            switch (this.showSessionDraft.edType) {
                case 'SeatPlanner': {
                    seatMap = this.seatPlannerService.getAllSeatplanCategory();
                    break;
                }
                case 'PolicyDesc': {
                    seatMap = this.policyDescService.getAllSeatplanCategory();
                    break;
                }
                default:
                    this.loading -= 1;
                    return;
            }
            this.loading += 1;
            seatMap
                .pipe(
                    tap(
                        e => (this.loading -= 1),
                        e => (this.loading -= 1),
                    ),
                )
                .subscribe(
                    maps => {
                        this.seatPlanIdFormControl.enable();
                        this.filteredMapList$.next(maps);
                        this.mapList = maps;
                        this.seatPlanIdFormControl.setValue(this.showSessionDraft.seatPlanId);
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Load seat map thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        } else if (this.showSession) {
            this.updateSeatPlanForm.patchValue({
                seatPlanId: this.showSession.seatPlanId,
                edType: this.showSession.edType,
                extraInfo: this.showSession.extraInfo || '',
            });
            switch (this.showSession.edType) {
                case 'SeatPlanner': {
                    seatMap = this.seatPlannerService.getAllSeatplanCategory();
                    break;
                }
                case 'PolicyDesc': {
                    seatMap = this.policyDescService.getAllSeatplanCategory();
                    break;
                }
                default:
                    return;
            }
            this.loading += 1;
            seatMap
                .pipe(
                    tap(
                        e => (this.loading -= 1),
                        e => (this.loading -= 1),
                    ),
                )
                .subscribe(
                    maps => {
                        this.seatPlanIdFormControl.enable();
                        this.filteredMapList$.next(maps);
                        this.mapList = maps;
                        this.seatPlanIdFormControl.setValue(this.showSession.seatPlanId);
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Liad seat plain thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }

    get seatPlanIdFormControl() {
        return <FormControl>this.updateSeatPlanForm.get('seatPlanId');
    }

    get edTypeFormControl() {
        return <FormControl>this.updateSeatPlanForm.get('edType');
    }

    get extraInfoFormControl() {
        return <FormControl>this.updateSeatPlanForm.get('extraInfo');
    }

    onSubmit() {
        if (this.updateSeatPlanForm.invalid) {
            this.updateSeatPlanForm.markAsDirty();
            this.toastrService.show('Vui lòng kiểm tra lại', `Dữ liệu nhập không đúng`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }
        const extraInfo = this.extraInfoFormControl.value ?? '';
        if (this.showSessionDraft) {
            this.loading += 1;
            this.adminService
                .updateShowSessionDraftSeatPlan$(
                    this.showSessionDraft.id,
                    this.updateSeatPlanForm.get('seatPlanId').value,
                    this.updateSeatPlanForm.get('edType').value,
                    extraInfo,
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
                            'Cập nhật thông tin seatplan nháp thành công',
                            'Cập nhật thông tin seatplan nháp thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        this.toastrService.show(
                            getErrorMessageFromResponse(error),
                            'Cập nhật thông tin seatplan nháp thất bại',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    },
                );
        } else if (this.showSession) {
            this.loading += 1;
            this.showService
                .updateShowSessionSeatPlan$(
                    this.showId,
                    this.showSession.id,
                    this.updateSeatPlanForm.get('seatPlanId').value,
                    this.updateSeatPlanForm.get('edType').value,
                    extraInfo,
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
                        this.extraInfoFormControl.setValue(model.extraInfo || '');
                        this.updateSessionSucceed.emit(model);
                        this.toastrService.show(
                            'Cập nhật thông tin seatplan thành công',
                            'Cập nhật thông tin seatplan thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        this.toastrService.show(
                            getErrorMessageFromResponse(error),
                            'Cập nhật thông tin seatplan thất bại',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    },
                );
        }
    }

    onSelectEdType($event: any) {
        let seatMap: Observable<SeatplanCategoryModel[]>;
        switch ($event) {
            case 'SeatPlanner': {
                seatMap = this.seatPlannerService.getAllSeatplanCategory();
                break;
            }
            case 'PolicyDesc': {
                seatMap = this.policyDescService.getAllSeatplanCategory();
                break;
            }
            default:
                return;
        }
        this.loading += 1;
        seatMap
            .pipe(
                tap(
                    e => (this.loading -= 1),
                    e => (this.loading -= 1),
                ),
            )
            .subscribe(
                maps => {
                    this.seatPlanIdFormControl.enable();
                    this.filteredMapList$.next(maps);
                    this.mapList = maps;

                    if (this.showSession?.edType === $event) {
                        this.seatPlanIdFormControl.setValue(this.showSession.seatPlanId);
                    } else if (this.showSessionDraft?.edType === $event) {
                        this.seatPlanIdFormControl.setValue(this.showSessionDraft.seatPlanId);
                    } else {
                        this.seatPlanIdFormControl.setValue('');
                    }
                },
                error => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Load seatplan thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    async onInputSeatPlanChange(value: string) {
        // this.filteredSessionList$.next(this.filterSession(value));
    }

    onSelectionSeatPlanChange($event: string) {
        if (!$event || $event.trim().length < 1) {
            this.inputMap.nativeElement.value = '';
            return;
        }
        const selectedSeatplan = this.mapList.filter(s => s.id === $event)[0];
        if (!this.inputMap || !selectedSeatplan) return;
        this.inputMap.nativeElement.value = selectedSeatplan.displayName || selectedSeatplan.id;
    }
}
