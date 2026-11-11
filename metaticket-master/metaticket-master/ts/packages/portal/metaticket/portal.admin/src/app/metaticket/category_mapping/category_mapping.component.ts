import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { ShowService } from '../../@core/metaticket/data.service';
import { CategoryItemModel, SeatplanCategoryModel, ShowModel, ShowSessionModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { TicketGateway } from '../gateway/ticket.gateway';
import { MetaticketPolicyDescService } from '../service/metaticket.policydesc.service';
import { MetaticketSeatplannerService } from '../service/metaticket.seatplanner.service';
import removeAccents from 'remove-accents';
import { Observable, ReplaySubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';

@Component({
    templateUrl: './category_mapping.component.html',
    styleUrls: ['./category_mapping.component.scss'],
    selector: 'category-mapping',
})
export class CategoryMappingComponent {
    categoryFormGroups = this.formBuilder.group({
        show: new FormControl({ value: '', disabled: true }, Validators.required),
        session: new FormControl({ value: '', disabled: true }, Validators.required),
        kindMap: new FormControl({ value: '', disabled: true }, Validators.required),
        map: new FormControl({ value: '', disabled: true }),
        categoryItemFormGroups: this.formBuilder.array([]),
    });

    showList: ShowModel[] = [];
    sessionList: ShowSessionModel[] = [];
    filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>();
    filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>();

    selectedShow: ShowModel;
    selectedSession: ShowSessionModel;
    selectedSeatplan: SeatplanCategoryModel;

    @ViewChild('autoInputShow') inputShow: ElementRef<HTMLInputElement>;
    @ViewChild('autoInputSession') inputSession: ElementRef<HTMLInputElement>;

    loading: number = 0;

    constructor(
        private formBuilder: FormBuilder,
        private showService: ShowService,
        private ticketService: TicketGateway,
        private toastrService: NbToastrService,
        private seatplannerService: MetaticketSeatplannerService,
        private policydescService: MetaticketPolicyDescService,
    ) {
        this.loading += 1;
        showService
            .getShows$()
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
                shows => {
                    if (!shows) return;
                    this.showFormControl.enable();
                    this.showList = shows.sort((s1, s2) => (s1.createdAt > s2.createdAt ? -1 : 1));
                    this.filteredShowList$.next(this.showList);
                },
                error => {
                    this.toastrService.show(
                        getErrorMessageFromResponse(error),
                        'Load thông tin show thất bại, vui lòng tải lại trang',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
            );
    }

    async onInputShowChange(value: string) {
        this.filteredShowList$.next(this.filterShow(value));
    }

    async onInputSessionChange(value: string) {
        this.filteredSessionList$.next(this.filterSession(value));
    }

    onSelectionShowChange($event: string) {
        if (!this.inputShow) return;

        this.resetSession();

        this.filteredShowList$.next(this.showList.filter(s => s.id === $event));
        this.selectedShow = this.showList.filter(s => s.id === $event)[0];
        this.inputShow.nativeElement.value = this.selectedShow?.showName;
        this.sessionList = this.selectedShow.sessions;
        this.filteredSessionList$.next(this.sessionList);
        if (this.sessionList.length === 1) {
            this.sessionFormControl.setValue(this.sessionList[0].id);
        } else {
            this.sessionFormControl.setValue('');
        }
        this.sessionFormControl.enable();
        // setValue does not emit nb-autocomplete selectedChange, so a lone session must be applied here;
        // otherwise selectedSession stays undefined while category items can still be added.
        if (this.sessionList.length === 1) this.applySession(this.sessionList[0].id);
    }

    getSessionName(session: ShowSessionModel): string {
        let name: string = '';
        if (session) {
            if (session.code?.trim()?.length > 0 && session.sessionName)
                name = `${session.code} - ${session.sessionName}`;
            else if (session.code?.trim()?.length > 0 && !session.sessionName) name = session.code;
            else if (session.sessionName && session.code?.trim()?.length <= 0) name = session.sessionName;
        }
        return name;
    }

    onSelectionSessionChange($event: string) {
        if (!this.inputSession?.nativeElement?.value) return;

        this.applySession($event);
    }

    /** Selects the session, then reloads its seat map/policy and category items. */
    private applySession(sessionId: string) {
        this.resetSession();

        this.filteredSessionList$.next(this.sessionList.filter(s => s.id === sessionId));
        this.selectedSession = this.sessionList.filter(s => s.id === sessionId)[0];
        this.inputSession.nativeElement.value = this.getSessionName(this.selectedSession);
        this.kindMapFormControl.setValue(this.selectedSession.edType);
        this.kindMapFormControl.enable();
        this.loadSeatMap();
        this.categoryFormGroups.controls.categoryItemFormGroups.clear();
        this.loadCategories();
    }

    private resetSession() {
        this.selectedSession = undefined;
        this.selectedSeatplan = undefined;
        this.categoryFormGroups.controls.kindMap.reset();
        this.categoryFormGroups.controls.map.reset();
        // clear() not reset(): reset() keeps the items, which then render against an undefined session.
        this.categoryFormGroups.controls.categoryItemFormGroups.clear();
    }

    private filterShow(input: string): ShowModel[] {
        const filterValue = removeAccents(input.toLowerCase());
        return this.showList.filter(
            optionValue =>
                !input || input === '' || removeAccents(optionValue.showName.toLowerCase()).indexOf(filterValue) > -1,
        );
    }

    private filterSession(input: string): ShowSessionModel[] {
        const filterValue = removeAccents(input.toLowerCase());
        return this.sessionList.filter(
            optionValue =>
                !input ||
                input === '' ||
                removeAccents(optionValue.sessionName?.toLowerCase() || '').indexOf(filterValue) > -1 ||
                removeAccents(optionValue.code?.toLowerCase() || '').indexOf(filterValue) > -1,
        );
    }

    get showFormControl() {
        return <FormControl>this.categoryFormGroups.controls.show;
    }

    get sessionFormControl() {
        return <FormControl>this.categoryFormGroups.controls.session;
    }

    get kindMapFormControl() {
        return <FormControl>this.categoryFormGroups.controls.kindMap;
    }

    get mapFormControl() {
        return <FormControl>this.categoryFormGroups.controls.map;
    }

    getFormGroups() {
        return (this.categoryFormGroups.controls.categoryItemFormGroups as FormArray).controls as FormGroup[];
    }

    createNewCatItemFormGroup() {
        (<FormArray>this.categoryFormGroups.controls.categoryItemFormGroups).push(
            this.formBuilder.group({
                old: new FormControl(undefined),
                displayName: new FormControl(undefined, Validators.required),
                category: new FormControl(undefined),
                price: new FormControl(undefined, Validators.required),
                color: new FormControl(undefined),
                openDate: new FormControl(undefined),
                closeDate: new FormControl(undefined),
                editMode: true,
                newly: true,
                loading: false,
            }),
        );
    }

    initCategoryItemFormGroup(cat: CategoryItemModel) {
        (<FormArray>this.categoryFormGroups.controls.categoryItemFormGroups).push(
            this.formBuilder.group({
                old: new FormControl(cat.displayName, Validators.required),
                displayName: new FormControl(cat.displayName, Validators.required),
                category: new FormControl({ value: cat.category, disabled: false }),
                price: new FormControl(cat.price, Validators.required),
                color: new FormControl(cat.color),
                openDate: new FormControl(cat.openDate ? new Date(cat.openDate) : undefined),
                closeDate: new FormControl(cat.closeDate ? new Date(cat.closeDate) : undefined),
                editMode: false,
                newly: false,
                loading: false,
            }),
        );
    }

    removeItem(index: number, form: FormGroup) {
        if (['Open'].includes(this.selectedSession?.status)) {
            this.toastrService.show(
                `session status is ${this.selectedSession?.status}`,
                'Không thể xóa hạng vé, show đã được mở bán',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        const newly = form.get('newly');
        if (newly.value) {
            this.categoryFormGroups.controls.categoryItemFormGroups.removeAt(index);
            return;
        }

        const cat = form.get('category');
        form.get('loading').setValue(true);
        this.ticketService
            .deleteCategory$(
                this.categoryFormGroups.controls.show.value,
                this.categoryFormGroups.controls.session.value,
                cat.value,
            )
            .pipe(
                tap(
                    _ => {
                        form.get('loading').setValue(false);
                    },
                    _ => {
                        form.get('loading').setValue(false);
                    },
                ),
            )
            .subscribe(
                resp => {
                    this.categoryFormGroups.controls.categoryItemFormGroups.removeAt(index);
                    this.toastrService.show(
                        `Xóa hạng vé ${resp.deletedCat}, số ticket đã xóa ${resp.noTicketDeleted} của ${form.get('displayName')}`,
                        'Xóa hạng vé thành công',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                    );
                },
                (error: HttpErrorResponse) => {
                    this.toastrService.show(getErrorMessageFromResponse(error), {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    createCategory(form: FormGroup) {
        if (['Open'].includes(this.selectedSession?.status)) {
            this.toastrService.show(
                `session status is ${this.selectedSession?.status}`,
                'Không thể tạo hạng vé, show đã được mở bán',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        if (form.invalid) {
            form.markAsDirty();
            this.toastrService.show('Vui lòng kiểm tra lại', `Dữ liệu nhập không đúng`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }
        if (this.categoryFormGroups.controls.categoryItemFormGroups.length === 1) {
            const o: any = form.value;
            const c: any = {};
            if (o.openDate) c.openDate = Date.parse(o.openDate);
            if (o.closeDate) c.closeDate = Date.parse(o.closeDate);
            c.category = o.category?.trim() || o.displayName?.trim() || undefined;
            c.color = o.color?.trim() || undefined;
            c.displayName = o.displayName?.trim() || undefined;
            c.price = o.price;

            if (this.selectedSeatplan && (!o.category || o.category.length < 1)) {
                this.toastrService.show('category không được để trống', `Dữ liệu nhập không đúng`, {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'danger',
                    duration: 30000,
                });
                return;
            }

            form.get('loading').setValue(true);
            this.ticketService
                .createCategoryMapping$(
                    this.categoryFormGroups.controls.show.value,
                    this.categoryFormGroups.controls.session.value,
                    this.categoryFormGroups.controls.kindMap.value,
                    this.selectedSeatplan?.id,
                    c,
                )
                .pipe(
                    tap(
                        _ => {
                            form.get('loading').setValue(false);
                        },
                        _ => {
                            form.get('loading').setValue(false);
                        },
                    ),
                )
                .subscribe(
                    model => {
                        form.get('newly').setValue(false);
                        form.get('editMode').setValue(false);
                        form.get('category').disable();

                        this.toastrService.show(`Tạo hạng vé ${c.displayName} thành công`, `Tạo hạng vé thành công`, {
                            status: 'success',
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        });
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Tạo hạng vé thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        } else {
            this.addOrUpdateCategory(form);
        }
    }

    addOrUpdateCategory(form: FormGroup) {
        if (['Open'].includes(this.selectedSession?.status)) {
            this.toastrService.show(
                `session status is ${this.selectedSession?.status}`,
                'Không thể cập nhật hạng vé, show đã được mở bán',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }
        if (form.invalid) {
            form.markAsDirty();
            this.toastrService.show('Vui lòng kiểm tra lại', `Dữ liệu nhập không đúng`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }
        const o: any = form.value;
        const c: any = {};
        if (o.openDate) c.openDate = Date.parse(o.openDate);
        if (o.closeDate) c.closeDate = Date.parse(o.closeDate);
        c.category = o.category?.trim() || o.displayName?.trim() || undefined;
        c.color = o.color?.trim() || undefined;
        c.displayName = o.displayName?.trim() || undefined;
        c.price = o.price;

        if (this.selectedSeatplan && (!o.category || o.category.length < 1)) {
            this.toastrService.show('category không được để trống', `Dữ liệu nhập không đúng`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }

        form.get('loading').setValue(true);
        this.ticketService
            .addOrUpdateCategory$(
                this.categoryFormGroups.controls.show.value,
                this.categoryFormGroups.controls.session.value,
                o.old,
                c,
            )
            .pipe(
                tap(
                    _ => {
                        form.get('loading').setValue(false);
                    },
                    _ => {
                        form.get('loading').setValue(false);
                    },
                ),
            )
            .subscribe(
                resp => {
                    form.get('editMode').setValue(false);
                    form.get('newly').setValue(false);
                    form.get('category').disable();

                    this.toastrService.show(
                        `Add/Update hạng vé ${resp.updatedCat} thành công, updated ${resp.noTicketUpdated} ticket`,
                        'Add/Update hạng vé thành công',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                    );
                },
                (error: HttpErrorResponse) => {
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Add/Update hạng vé thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    private loadSeatMap() {
        let seatMap: Observable<SeatplanCategoryModel>;
        switch (this.selectedSession.edType) {
            case 'SeatPlanner': {
                seatMap = this.seatplannerService.getSeatplanCategory(this.selectedSession.seatPlanId);
                break;
            }
            case 'PolicyDesc': {
                seatMap = this.policydescService.getSeatplanCategory(this.selectedSession.seatPlanId);
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
                m => {
                    this.selectedSeatplan = m;
                    this.mapFormControl.setValue(m.displayName || m.id);
                    this.mapFormControl.enable();
                },
                error => {
                    console.log('load seat map failed', error);
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Load seat map thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    private loadCategories() {
        this.loading += 1;
        this.ticketService
            .loadCategoryMappingByShowAndSession$(this.selectedShow.id, this.selectedSession.id)
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
                catMapping => {
                    if (catMapping) {
                        new Map(Object.entries(catMapping.catMapping)).forEach((a, b) =>
                            this.initCategoryItemFormGroup(a),
                        );
                    }
                },
                (error: HttpErrorResponse) => {
                    console.log('error when load category mapping', error);
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Tải hạng vé thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }
}
