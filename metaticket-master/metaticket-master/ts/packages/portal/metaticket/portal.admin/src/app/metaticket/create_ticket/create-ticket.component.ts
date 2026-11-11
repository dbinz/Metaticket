import { AsyncPipe, NgClass, NgForOf, NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, ViewChild } from '@angular/core';
import {
    FormArray,
    FormBuilder,
    FormControl,
    FormGroup,
    FormsModule,
    ReactiveFormsModule,
    Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
    NbAutocompleteModule,
    NbButtonModule,
    NbCardModule,
    NbGlobalPhysicalPosition,
    NbInputModule,
    NbListModule,
    NbOptionModule,
    NbSelectModule,
    NbSpinnerModule,
    NbTagModule,
    NbToastrService,
} from '@nebular/theme';
import { ShowService } from '../../@core/metaticket/data.service';
import {
    CategoryMappingModel,
    SeatplanModel,
    ShowModel,
    ShowSessionModel,
    TicketModel,
} from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { CreateTicketDocRequest, TicketDocDTO } from '../gateway/request.ticket';
import { TicketGateway } from '../gateway/ticket.gateway';
import { MetaticketPolicyDescService } from '../service/metaticket.policydesc.service';
import { MetaticketSeatplannerService } from '../service/metaticket.seatplanner.service';
import removeAccents from 'remove-accents';
import { BehaviorSubject, Observable, of, ReplaySubject, Subject } from 'rxjs';
import { mergeMap, tap } from 'rxjs/operators';

@Component({
    templateUrl: 'create-ticket.component.html',
    styleUrls: ['create-ticket.component.scss'],
    selector: 'create-ticket',
    standalone: true,
    imports: [
        NbCardModule,
        AsyncPipe,
        FormsModule,
        NbAutocompleteModule,
        NbInputModule,
        NbOptionModule,
        NgForOf,
        NgClass,
        NgIf,
        NbSelectModule,
        ReactiveFormsModule,
        NbTagModule,
        NbButtonModule,
        NbSpinnerModule,
        NbListModule,
        RouterLink,
    ],
})
export class CreateTicketComponent {
    rootFormGroups = this.formBuilder.group({
        show: new FormControl({ value: '', disabled: true }, Validators.required),
        session: new FormControl({ value: '', disabled: true }, Validators.required),
        kindMap: new FormControl({ value: '', disabled: true }, Validators.required),
        map: new FormControl({ value: '', disabled: true }, Validators.required),
        catFormGroup: this.formBuilder.array([]),
    });

    showList: ShowModel[] = [];
    sessionList: ShowSessionModel[] = [];
    filteredShowList$: Subject<ShowModel[]> = new ReplaySubject<ShowModel[]>();
    filteredSessionList$: Subject<ShowSessionModel[]> = new ReplaySubject<ShowSessionModel[]>();

    selectedShow: ShowModel;
    selectedSession: ShowSessionModel;
    categoryMapping: CategoryMappingModel;
    tickets: TicketModel[] = [];
    seatPlan: SeatplanModel;

    seatZoneFormGs: Map<string, FormGroup<CategoryFormGroup>> = new Map();
    policyConfFormGs: Map<string, FormGroup<CategoryFormGroup>> = new Map();

    /** Vé của session lịch được sinh theo ngày ở màn Lịch bán vé; màn này không tạo/sửa được (vé sẽ thiếu ngày). */
    get isCalendarSession(): boolean {
        return this.selectedSession?.sessionType === 'CALENDAR';
    }

    @ViewChild('autoInputShow') inputShow: ElementRef<HTMLInputElement>;
    @ViewChild('autoInputSession') inputSession: ElementRef<HTMLInputElement>;

    loading: number = 0;

    constructor(
        private formBuilder: FormBuilder,
        private showService: ShowService,
        private toastrService: NbToastrService,
        private seatplannerService: MetaticketSeatplannerService,
        private policydescService: MetaticketPolicyDescService,
        private ticketGateway: TicketGateway,
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
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Load show thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    get showFormControl() {
        return <FormControl>this.rootFormGroups.controls.show;
    }

    get sessionFormControl() {
        return <FormControl>this.rootFormGroups.controls.session;
    }

    get kindMapFormControl() {
        return <FormControl>this.rootFormGroups.controls.kindMap;
    }

    get mapFormControl() {
        return <FormControl>this.rootFormGroups.controls.map;
    }

    async onInputShowChange(value: string) {
        this.filteredShowList$.next(this.filterShow(value));
    }

    async onInputSessionChange(value: string) {
        this.filteredSessionList$.next(this.filterSession(value));
    }

    onSelectionShowChange($event: string) {
        if (!this.inputShow) return;

        this.rootFormGroups.controls.session.reset();

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

        this.resetSession();

        this.filteredSessionList$.next(this.sessionList.filter(s => s.id === $event));
        this.selectedSession = this.sessionList.filter(s => s.id === $event)[0];
        this.inputSession.nativeElement.value = this.getSessionName(this.selectedSession);
        this.kindMapFormControl.setValue(this.selectedSession.edType);
        this.kindMapFormControl.enable();
        this.rootFormGroups.controls.catFormGroup.clear();
        if (this.isCalendarSession) return;
        this.loadCategoryMapping()
            .pipe(mergeMap(_ => this.loadTickets()))
            .pipe(mergeMap(_ => this.loadSeatMap()))
            .pipe(tap(_ => this.loadPolicyConf()))
            .subscribe(
                _ => {},
                error => {
                    console.log('load data failed', error);
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Load dữ liệu thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }

    private resetSession() {
        this.seatPlan = undefined;
        this.tickets = [];
        this.categoryMapping = undefined;
        this.selectedSession = undefined;
        this.rootFormGroups.controls.kindMap.reset();
        this.rootFormGroups.controls.map.reset();
        this.rootFormGroups.controls.catFormGroup.reset();
        this.seatZoneFormGs.clear();
        this.policyConfFormGs.clear();
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

    private loadCategoryMapping(): Observable<CategoryMappingModel> {
        this.loading += 1;
        return this.ticketGateway
            .loadCategoryMappingByShowAndSession$(this.selectedShow.id, this.selectedSession.id)
            .pipe(
                tap(m => {
                    if (m) {
                        m.catMapping = new Map(Object.entries(m.catMapping));
                        this.categoryMapping = m;
                    } else {
                        throw { error: 'Không tìm thấy category' };
                    }
                }),
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
            );
    }

    private loadTickets(): Observable<TicketModel[]> {
        this.loading += 1;
        return this.ticketGateway
            .fetchAllTicket(this.selectedShow.id, this.selectedSession.id)
            .pipe(tap(t => (this.tickets = t)))
            .pipe(
                tap(
                    e => {
                        this.loading -= 1;
                    },
                    e => {
                        this.loading -= 1;
                    },
                ),
            );
    }

    private loadSeatMap(): Observable<SeatplanModel> {
        let seatMap: Observable<SeatplanModel>;
        switch (this.selectedSession.edType) {
            case 'SeatPlanner': {
                seatMap = this.seatplannerService.getSeatPlan(this.selectedSession.seatPlanId);
                break;
            }
            case 'PolicyDesc': {
                seatMap = this.policydescService.getSeatPlan(this.selectedSession.seatPlanId);
                break;
            }
            default:
                return of(undefined);
        }

        this.loading += 1;
        return seatMap
            .pipe(
                tap(seatPlan => {
                    this.seatPlan = seatPlan;
                    this.mapFormControl.setValue(seatPlan.displayName || seatPlan.id);
                    this.mapFormControl.enable();

                    const itemAdded: string[] = [];

                    seatPlan.items.forEach(i => {
                        if (!itemAdded.includes(i.sid) && ['Seat', 'SeatZone'].includes(i.type)) {
                            let formG = this.seatZoneFormGs.get(i.cat);
                            if (!formG) {
                                const cat = this.categoryMapping.catMapping.get(i.cat);
                                formG = this.initCategoryFormGroup('SeatZone', i.cat, cat?.displayName, cat?.price);
                                this.seatZoneFormGs.set(i.cat, formG);
                            }
                            const ticket = this.tickets.find(t => t.sid === i.sid);
                            if (i.type === 'SeatZone') {
                                const ticketForm = this.initTicketForm(
                                    i.cat,
                                    i.sid,
                                    ticket?.dname,
                                    ticket?.priceCat || formG.controls.ticketCat.value,
                                    ticket?.basePrice || formG.controls.ticketPrice.value,
                                    ticket?.totCap,
                                );
                                (<FormArray>formG.controls.zoneFormGroups).push(ticketForm);
                            }
                            if (i.type === 'Seat') {
                                const ticketForm = this.initTicketForm(
                                    i.cat,
                                    i.sid,
                                    ticket?.dname,
                                    ticket?.priceCat || formG.controls.ticketCat.value,
                                    ticket?.basePrice || formG.controls.ticketPrice.value,
                                    1,
                                );
                                (<FormArray>formG.controls.seatFormGroups).push(ticketForm);
                            }
                            itemAdded.push(i.sid);
                        }
                    });
                }),
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
            );
    }

    private resetSeatPolicyFormGs(catFormG: FormGroup<CategoryFormGroup>) {
        const item = structuredClone(catFormG.value);
        catFormG.controls.zoneFormGroups.clear();
        catFormG.reset({
            type: item.type,
            cat: item.cat,
            ticketCat: item.ticketCat,
            ticketPrice: item.ticketPrice,
            zoneFormGroups:
                item.zoneFormGroups.length > 0
                    ? [this.initTicketForm(item.cat, item.ticketCat, undefined, item.ticketCat, item.ticketPrice)]
                    : [],
            seatFormGroups: item.seatFormGroups,
        });
    }

    private resetPolicyConf(catFormG: FormGroup<CategoryFormGroup>) {
        const item = structuredClone(catFormG.value);
        catFormG.controls.zoneFormGroups.clear();
        catFormG.reset({
            type: item.type,
            cat: item.cat,
            ticketCat: item.ticketCat,
            ticketPrice: item.ticketPrice,
            zoneFormGroups:
                item.zoneFormGroups.length > 0
                    ? [this.initTicketForm(item.cat, item.ticketCat, undefined, item.ticketCat, item.ticketPrice)]
                    : [],
            seatFormGroups: item.seatFormGroups,
        });
        const ticketForm = this.initTicketForm(item.cat, item.ticketCat, undefined, item.ticketCat, item.ticketPrice);
        (<FormArray>catFormG.controls.zoneFormGroups).push(ticketForm);
    }

    private loadPolicyConf() {
        if (this.kindMapFormControl.value !== 'PolicyConfig') return;

        this.categoryMapping.catMapping.forEach((m, cat) => {
            let formG = this.policyConfFormGs.get(cat);
            if (!formG) {
                formG = this.initCategoryFormGroup('PolicyConf', cat, m.displayName, m.price);
                this.policyConfFormGs.set(cat, formG);
            }
            const ticket = this.tickets.find(t => t.sid === cat);
            const ticketForm = this.initTicketForm(
                cat,
                cat,
                ticket?.dname,
                ticket?.priceCat || formG.controls.ticketCat.value,
                ticket?.basePrice || formG.controls.ticketPrice.value,
                ticket?.totCap,
                ticket?.policy,
            );
            (<FormArray>formG.controls.zoneFormGroups).push(ticketForm);
        });
    }

    initCategoryFormGroup(
        type: CatFormGroupType,
        cat: string,
        ticketCat: string,
        ticketPrice: number,
    ): FormGroup<CategoryFormGroup> {
        return this.formBuilder.group({
            type: new FormControl(type),
            loading: new FormControl(false),
            cat: new FormControl(cat, Validators.required),
            ticketCat: new FormControl(ticketCat, Validators.required),
            ticketPrice: new FormControl(ticketPrice, Validators.required),
            zoneFormGroups: this.formBuilder.array([]),
            seatFormGroups: this.formBuilder.array([]),
        });
    }

    initTicketForm(
        category: string,
        sid: string,
        dname?: string,
        ticketCat?: string,
        ticketPrice?: number,
        quantity?: number,
        policy?: string,
    ): FormGroup<TicketFormGroup> {
        return this.formBuilder.group({
            loading: new FormControl(false),
            cat: new FormControl(category || undefined),
            sid: new FormControl(sid, Validators.required),
            dname: new FormControl(dname || undefined),
            ticketCat: new FormControl(ticketCat || undefined),
            ticketPrice: new FormControl(ticketPrice || undefined),
            policy: new FormControl(policy || undefined),
            quantity: new FormControl(quantity, [Validators.required, Validators.min(1), Validators.pattern('[0-9]+')]),
        });
    }

    get allForm(): FormGroup<CategoryFormGroup>[] {
        return [...this.seatZoneFormGs.values()];
    }

    get policyConfFormG(): FormGroup<CategoryFormGroup>[] {
        return [...this.policyConfFormGs.values()];
    }

    getSeatFormGroups(fg: FormGroup<CategoryFormGroup>): FormGroup<TicketFormGroup>[] {
        return fg.controls.seatFormGroups.controls as unknown as FormGroup<TicketFormGroup>[];
    }

    getZoneFormGroups(fg: FormGroup<CategoryFormGroup>): FormGroup<TicketFormGroup>[] {
        return fg.controls.zoneFormGroups.controls as unknown as FormGroup<TicketFormGroup>[];
    }

    isTicketExist(priceCat: string): boolean {
        return this.tickets.find(t => t.priceCat === priceCat) && true;
    }

    updateTicket(ticketCat: string, ticketFG: TicketFormGroup) {
        if (['Open'].includes(this.selectedSession?.status)) {
            this.toastrService.show(`Suất diễn đã được mở bán`, 'Không thể cập nhật vé', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }
        if (!this.isTicketExist(ticketCat)) {
            this.toastrService.show(`Không tồn tại vé để cập nhật`, 'Không thể cập nhật vé', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }
        ticketFG.loading.setValue(true);
        this.ticketGateway
            .updateTicket({
                showId: this.selectedShow.id,
                sessionId: this.selectedSession.id,
                sid: (ticketFG.sid.value as unknown as string)?.trim() || undefined,
                dname: (ticketFG.dname.value as unknown as string)?.trim() || undefined,
                priceCat: ticketCat,
                totCap: ticketFG.quantity.value as unknown as number,
                policy: (ticketFG.policy.value as unknown as string)?.trim() || undefined,
            })
            .pipe(
                tap(
                    e => {
                        ticketFG.loading.setValue(false);
                        this.toastrService.show('', `Cập nhật vé thành công`, {
                            status: 'success',
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        });
                    },
                    error => {
                        ticketFG.loading.setValue(false);
                        this.toastrService.show(getErrorMessageFromResponse(error), `Cập nhật vé thất bại`, {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                ),
            )
            .subscribe();
    }

    getTicketByPriceCat(cat: string): BehaviorSubject<TicketModel[]> {
        return new BehaviorSubject(this.tickets.filter(t => t.priceCat === cat) || []);
    }

    createTicket(fg: FormGroup<CategoryFormGroup>) {
        if (['Open'].includes(this.selectedSession?.status)) {
            this.toastrService.show(`Suất diễn đã được mở bán`, 'Không thể tạo vé', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }

        if (fg.invalid) {
            fg.markAsDirty();
            this.toastrService.show('Vui lòng kiểm tra lại', `Dữ liệu nhập không đúng`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }
        const categoryFG = fg.value;
        fg.controls.loading.setValue(true);
        const zone = categoryFG.zoneFormGroups as TicketFormGroup[];
        const seat = categoryFG.seatFormGroups as TicketFormGroup[];
        const zoneDTO = zone.map(
            z =>
                <TicketDocDTO>{
                    sid: (z.sid as unknown) || undefined,
                    dname: (z.dname as unknown as string)?.trim() || undefined,
                    seatType: 'SeatZone',
                    priceCat: (z.ticketCat as unknown as string)?.trim() || undefined,
                    basePrice: (z.ticketPrice as unknown as number) || undefined,
                    totCap: (z.quantity as unknown as number) || undefined,
                    policy: (z.policy as unknown as string)?.trim() || undefined,
                },
        );
        const seatDTO = seat.map(
            z =>
                <TicketDocDTO>{
                    sid: (z.sid as unknown) || undefined,
                    dname: (z.dname as unknown as string)?.trim() || undefined,
                    seatType: 'Seat',
                    priceCat: (z.ticketCat as unknown as string)?.trim() || undefined,
                    basePrice: (z.ticketPrice as unknown as number) || undefined,
                    totCap: (z.quantity as unknown as number) || undefined,
                },
        );
        const dto = zoneDTO.concat(seatDTO);
        const req = <CreateTicketDocRequest>{
            showId: this.rootFormGroups.controls.show.value,
            sessionId: this.rootFormGroups.controls.session.value,
            tickets: dto,
        };
        this.ticketGateway
            .createTicket(req)
            .pipe(
                tap(
                    (tickets: TicketModel[]) => {
                        fg.controls.loading.setValue(false);
                        this.tickets = this.tickets.concat(tickets);
                        this.toastrService.show('', `Đã tạo vé thành công`, {
                            status: 'success',
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        });
                    },
                    error => {
                        fg.controls.loading.setValue(false);
                        this.toastrService.show(getErrorMessageFromResponse(error), `Tạo vé thất bại`, {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                ),
            )
            .subscribe();
    }

    clearTicket(fg: FormGroup<CategoryFormGroup>) {
        if (['Open'].includes(this.selectedSession?.status)) {
            this.toastrService.show(`Suất diễn đã được mở bán.`, 'Không thể clear vé', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }

        fg.controls.loading.setValue(true);
        this.ticketGateway
            .deleteTicketOfCategory$(this.selectedShow.id, this.selectedSession.id, fg.controls.ticketCat.value)
            .pipe(
                tap(
                    e => {
                        fg.controls.loading.setValue(false);
                        this.tickets = this.tickets.filter(t => t.priceCat !== fg.controls.ticketCat.value);
                        if (fg.controls.type.value === 'PolicyConf') this.resetPolicyConf(fg);
                        if (fg.controls.type.value === 'SeatZone') this.resetSeatPolicyFormGs(fg);
                        this.toastrService.show(
                            `Clear vé thành công cho cat ${fg.controls.ticketCat.value} số lượng ${e}`,
                            `Clear vé thành công`,
                            {
                                status: 'success',
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            },
                        );
                    },
                    (error: HttpErrorResponse) => {
                        fg.controls.loading.setValue(false);
                        this.toastrService.show(
                            `Clear vé thất bại cho cat ${fg.controls.ticketCat.value} ${error.error}`,
                            `Clear vé thất bại`,
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                        );
                    },
                ),
            )
            .subscribe();
    }
}

interface TicketFormGroup {
    loading: FormControl<boolean>;
    sid: FormControl<string>;
    cat: FormControl<string>;
    ticketCat: FormControl<string>;
    ticketPrice: FormControl<number>;
    dname: FormControl<string>;
    policy: FormControl<string>;
    quantity: FormControl<number>;
}

type CatFormGroupType = 'SeatZone' | 'PolicyConf';

interface CategoryFormGroup {
    type: FormControl<CatFormGroupType>;
    loading: FormControl<boolean>;
    cat: FormControl<string>;
    ticketCat: FormControl<string>;
    ticketPrice: FormControl<number>;
    zoneFormGroups: FormArray<FormControl<any>>;
    seatFormGroups: FormArray<FormControl<any>>;
}
