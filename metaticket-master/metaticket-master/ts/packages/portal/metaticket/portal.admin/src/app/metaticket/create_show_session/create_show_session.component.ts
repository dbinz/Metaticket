import { HttpErrorResponse } from '@angular/common/http';
import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ViewChild } from '@angular/core';
import { AbstractControl, FormBuilder, FormControl, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { NbCalendarRange, NbDialogService, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { AdminService, ShowService } from '../../@core/metaticket/data.service';
import {
    CityModel,
    SessionCalendarConfig,
    SessionType,
    ShowModel,
    ShowSessionDraftModel,
    ShowSessionModel,
} from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import {
    generateDefaultSlug,
    isValidSlug,
    MAX_SLUG_LENGTH,
    normalizeSlug,
} from '../../@core/metaticket/utils/slug.util';
/**
 * Reactive validator mirroring the Show_Service `SlugValidator.checkFormat`. An absent slug
 * (empty / whitespace-only) is valid; otherwise the canonical form must satisfy the
 * Slug_Format_Rule. The {@link isValidSlug} reason ('empty' | 'too-long' | 'format') is surfaced
 * so the template can name the violated rule.
 */
function slugFormatValidator(control: AbstractControl): ValidationErrors | null {
    const error = isValidSlug(control.value);
    return error ? { slugFormat: error } : null;
}
import { SessionActionType } from './model';
import {
    epochToVnYmd,
    MAX_BOOKING_WINDOW_DAYS,
    toYmd,
    vnEpoch,
    ymdToLocalDate,
} from '../ticket_calendar/calendar.utils';
import { DialogConfirmComponent } from '../dialog/dialog.confirm/dialog.confirm.component';
import { QRCodeService } from '../service/metaticket.service.qrcode';
import removeAccents from 'remove-accents';
import { BehaviorSubject, ReplaySubject, Subject } from 'rxjs';
import { filter, mergeMap, tap } from 'rxjs/operators';

const DEFAULT_OPEN_TIME = '08:00';
const DEFAULT_CLOSE_TIME = '17:00';
const DEFAULT_BOOKING_WINDOW_DAYS = 90;
const DEFAULT_SAME_DAY_CUTOFF_MINUTES = 60;
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;
/** Chỉ đổi loại suất khi chưa gửi duyệt (vé đã sinh theo kiểu cũ không dùng được cho kiểu mới) — giống backend. */
const TYPE_CHANGEABLE_STATUSES = ['Created', 'Rejected'];

/** Thời gian + loại suất gửi lên API tạo/sửa session. */
interface SessionTiming {
    startTime: number;
    endTime: number;
    sessionType: SessionType;
    calendar?: SessionCalendarConfig;
}

@Component({
    templateUrl: './create_show_session.component.html',
    styleUrls: ['./create_show_session.component.scss'],
    selector: 'create-show-session',
    changeDetection: ChangeDetectionStrategy.Default,
})
export class CreateShowSessionComponent {
    sessions$: BehaviorSubject<ShowSessionModel[]> = new BehaviorSubject<ShowSessionModel[]>([]);

    show: ShowModel | undefined = undefined;
    showSessions: ShowSessionModel[] = [];
    showSessionsDraft: ShowSessionDraftModel[] = [];
    openEditShowCard = false;
    showSessionDraft: ShowSessionDraftModel | undefined;
    showSession: ShowSessionModel | undefined;
    step: string;

    loading: number = 0;

    /** Exposed to the template for the slug length error message. */
    readonly maxSlugLength = MAX_SLUG_LENGTH;

    /**
     * True once the organizer has typed in the slug field on the form. While false (and no stored
     * slug exists), the slug stays in sync with the entered session name via `generateDefaultSlug`.
     */
    private slugManuallyEdited = false;

    /** True when the session was loaded with a stored Session_Slug (shown unchanged). */
    private slugHasStored = false;

    /**
     * Canonical form of the slug stored on the loaded session/draft ('' when none). Used to detect
     * when an edited slug has been reverted to its original value, in which case the field is
     * treated as if it was never touched.
     */
    private originalSlug = '';

    private selectedCity: CityModel;
    cityList: CityModel[];
    filteredCityList$: Subject<CityModel[]> = new ReplaySubject<CityModel[]>();

    @ViewChild('autoInputCity') inputCity;

    ledgendImageUrl: string | undefined;
    eventSquareImageUrl: string | undefined;
    eventNameImageUrl: string | undefined;

    editSessionForm = this.formBuilder.group({
        sessionName: new FormControl('', Validators.required),
        code: new FormControl(''),
        slug: new FormControl('', slugFormatValidator),
        address: new FormControl('', Validators.required),
        startTime: new FormControl(new Date(), Validators.required),
        endTime: new FormControl(new Date(), Validators.required),
        requireDelivery: new FormControl(false, Validators.required),
        orderNote: new FormControl(''),
        city: new FormControl(null, Validators.required),
        ledgendImage: new FormControl(null),
        eventSquareImage: new FormControl(null),
        eventNameImage: new FormControl(null),
        isFree: new FormControl(false),
        showInHome: new FormControl(false),
        manageTicket: new FormControl(false),
        qrcodeEmail: new FormControl(false),
        sessionType: new FormControl<SessionType>('SINGLE'),
        calendarRange: new FormControl<NbCalendarRange<Date> | null>(null),
        openTime: new FormControl(DEFAULT_OPEN_TIME),
        closeTime: new FormControl(DEFAULT_CLOSE_TIME),
        bookingWindowDays: new FormControl(DEFAULT_BOOKING_WINDOW_DAYS),
        allowSameDaySale: new FormControl(false),
        sameDayCutoffMinutes: new FormControl(DEFAULT_SAME_DAY_CUTOFF_MINUTES),
    });

    readonly maxBookingWindowDays = MAX_BOOKING_WINDOW_DAYS;

    get sessionName(): FormControl {
        return <FormControl>this.editSessionForm.get('sessionName');
    }
    get code(): FormControl {
        return <FormControl>this.editSessionForm.get('code');
    }
    get slug(): FormControl {
        return <FormControl>this.editSessionForm.get('slug');
    }

    /**
     * Current provenance of the slug, surfaced as a small status label next to the field:
     *  - 'auto'    → auto-generated from the session name (no stored slug, not edited by hand)
     *  - 'manual'  → a previously saved slug shown unchanged
     *  - 'editing' → the organizer has edited the slug by hand on this form
     */
    get slugState(): 'auto' | 'manual' | 'editing' {
        if (this.slugManuallyEdited) return 'editing';
        if (this.slugHasStored) return 'manual';
        return 'auto';
    }

    /** Human-readable label for {@link slugState}. */
    get slugStateLabel(): string {
        switch (this.slugState) {
            case 'editing':
                return 'Đang chỉnh sửa';
            case 'manual':
                return 'Thủ công (đã lưu)';
            default:
                return 'Tự động theo tên suất diễn';
        }
    }
    get address(): FormControl {
        return <FormControl>this.editSessionForm.get('address');
    }
    get startTime(): FormControl {
        return <FormControl>this.editSessionForm.get('startTime');
    }
    get endTime(): FormControl {
        return <FormControl>this.editSessionForm.get('endTime');
    }

    get requireDelivery(): FormControl {
        return <FormControl>this.editSessionForm.get('requireDelivery');
    }

    get orderNote(): FormControl {
        return <FormControl>this.editSessionForm.get('orderNote');
    }

    get city(): FormControl {
        return <FormControl>this.editSessionForm.get('city');
    }

    get isFree(): FormControl {
        return <FormControl>this.editSessionForm.get('isFree');
    }

    get showInHome(): FormControl {
        return <FormControl>this.editSessionForm.get('showInHome');
    }

    get ledgendImage(): FormControl {
        return <FormControl>this.editSessionForm.get('ledgendImage');
    }

    get eventSquareImage(): FormControl {
        return <FormControl>this.editSessionForm.get('eventSquareImage');
    }

    get eventNameImage(): FormControl {
        return <FormControl>this.editSessionForm.get('eventNameImage');
    }

    get manageTicket(): FormControl {
        return <FormControl>this.editSessionForm.get('manageTicket');
    }

    get qrcodeEmail(): FormControl {
        return <FormControl>this.editSessionForm.get('qrcodeEmail');
    }

    get sessionType(): FormControl {
        return <FormControl>this.editSessionForm.get('sessionType');
    }

    get calendarRange(): FormControl {
        return <FormControl>this.editSessionForm.get('calendarRange');
    }

    get openTime(): FormControl {
        return <FormControl>this.editSessionForm.get('openTime');
    }

    get closeTime(): FormControl {
        return <FormControl>this.editSessionForm.get('closeTime');
    }

    get bookingWindowDays(): FormControl {
        return <FormControl>this.editSessionForm.get('bookingWindowDays');
    }

    get allowSameDaySale(): FormControl {
        return <FormControl>this.editSessionForm.get('allowSameDaySale');
    }

    get sameDayCutoffMinutes(): FormControl {
        return <FormControl>this.editSessionForm.get('sameDayCutoffMinutes');
    }

    get isCalendar(): boolean {
        return this.sessionType.value === 'CALENDAR';
    }

    constructor(
        private formBuilder: FormBuilder,
        private adminService: AdminService,
        private showService: ShowService,
        private qrcodeService: QRCodeService,
        private activatedRoute: ActivatedRoute,
        private toastrService: NbToastrService,
        private dialogService: NbDialogService,
        private clipboard: Clipboard,
        private changeDetectorRef: ChangeDetectorRef,
    ) {
        const showId = activatedRoute.snapshot.queryParams['showId'];
        this.step = activatedRoute.snapshot.queryParams['step'] ?? '';
        if (showId) {
            this.loading += 1;
            showService
                .getShow$(showId)
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
                    show => {
                        if (!show) return;
                        this.show = show;
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Load show thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
            this.loading += 1;
            showService
                .getShowSessions$(showId)
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
                    sessions => {
                        this.showSessions = sessions;
                        this.sessions$.next(sessions);
                        // Existing slugs are now known; re-run the duplicate check against them.
                        this.slug.updateValueAndValidity({ emitEvent: false });
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Load session thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
            this.loading += 1;
            adminService
                .getShowSessionsDraft$(showId)
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
                    response => {
                        this.showSessionsDraft = response;
                        // Draft slugs are now known; re-run the duplicate check against them.
                        this.slug.updateValueAndValidity({ emitEvent: false });
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Load session draft thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
            this.loading += 1;
            showService
                .getCities()
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
                    cities => {
                        if (!cities) return;
                        this.cityList = cities;
                        this.filteredCityList$.next(this.cityList);
                    },
                    error => {
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Load city thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }

        this.manageTicket.valueChanges.subscribe(value => {
            if (!value) {
                this.qrcodeEmail.setValue(false);
            }
        });

        // Vé theo lịch phát vé qua ticketslots (soát theo ngày) nên backend bắt buộc bật quản lý vé: khóa ô ở trạng thái bật.
        this.sessionType.valueChanges.subscribe((type: SessionType) => {
            if (type === 'CALENDAR') {
                this.manageTicket.setValue(true);
                this.manageTicket.disable({ emitEvent: false });
            } else {
                this.manageTicket.enable({ emitEvent: false });
            }
        });

        // Default the Session_Slug from the entered session name until the organizer edits the slug
        // field directly or an existing slug is shown as-is. Mirrors the show
        // form, which tracks the event name.
        this.sessionName.valueChanges.subscribe(value => {
            if (this.slugHasStored || this.slugManuallyEdited) return;
            const generated = generateDefaultSlug(value, this.collectSessionSlugs());
            this.slug.setValue(generated, { emitEvent: false });
        });

        // React to manual slug edits. Programmatic updates (default generation, loading a stored
        // slug) use emitEvent:false and never reach here, so this only fires on real typing.
        this.slug.valueChanges.subscribe(value => {
            this.onSlugValueChanged(value);
        });

        // Live within-show uniqueness check so a duplicate slug surfaces inline as the organizer
        // types, in addition to the format validator. The reactive validators run on every value
        // change, so the `slugDuplicate` error stays in sync with the field.
        this.slug.addValidators(control => this.slugDuplicateValidator(control));
        this.slug.updateValueAndValidity({ emitEvent: false });
    }

    /**
     * Reactive validator flagging a slug that is already used by another session within this show.
     * Absent or format-invalid slugs are skipped here (the format validator owns those), so the
     * duplicate message only appears for an otherwise-valid, conflicting slug.
     */
    private slugDuplicateValidator(control: AbstractControl): ValidationErrors | null {
        const raw = control.value?.trim() ?? '';
        if (raw.length === 0 || isValidSlug(raw)) {
            return null;
        }
        const canonical = normalizeSlug(raw);
        return this.collectSessionSlugs().has(canonical) ? { slugDuplicate: true } : null;
    }

    /**
     * React to a *manual* slug change. Three outcomes mirroring {@link slugState}:
     *  - cleared (empty/whitespace) → drop back to auto mode so editing the session name fills the
     *    slug again on the next name change;
     *  - reverted to the original stored slug → restore the pristine "manual (stored)" state, as if
     *    the field had never been touched (only applies when loaded with a stored slug);
     *  - anything else → mark the field as manually edited so the default stops overwriting it.
     */
    private onSlugValueChanged(value: string | null | undefined): void {
        const raw = (value ?? '').trim();

        if (raw.length === 0) {
            // Cleared: revert to auto so the next session-name edit regenerates a default.
            this.slugManuallyEdited = false;
            this.slugHasStored = false;
            return;
        }

        if (this.originalSlug.length > 0 && normalizeSlug(raw) === this.originalSlug) {
            // Re-typed the original stored slug: treat it as never edited.
            this.slugManuallyEdited = false;
            this.slugHasStored = true;
            return;
        }

        this.slugManuallyEdited = true;
    }

    /**
     * The set of canonical Session_Slugs already used by *other* sessions within this show
     * (active sessions and drafts), used for default suffixing and the within-show uniqueness
     * check. Excludes the session currently being edited so its own slug is not a conflict.
     */
    private collectSessionSlugs(): Set<string> {
        const taken = new Set<string>();
        const currentSessionId = this.showSession?.id;
        const currentDraftId = this.showSessionDraft?.id;
        for (const session of this.showSessions ?? []) {
            if (session.id === currentSessionId) continue;
            const canonical = normalizeSlug(session.slug);
            if (canonical) taken.add(canonical);
        }
        for (const draft of this.showSessionsDraft ?? []) {
            if (draft.id === currentDraftId) continue;
            const canonical = normalizeSlug(draft.slug);
            if (canonical) taken.add(canonical);
        }
        return taken;
    }

    /**
     * Client-side gate mirroring the server for the slug field: an absent slug is allowed; a
     * present slug must be format-valid and unique within this show. Surfaces a blocking toast and
     * returns false on violation.
     */
    private validateSlugBeforeSave(): boolean {
        const raw = this.slug.value?.trim() ?? '';
        if (raw.length === 0) {
            // Absent slug: submit without a slug, no format/duplicate error.
            return true;
        }

        if (isValidSlug(raw)) {
            // Format errors are already attached by the reactive validator and shown inline.
            this.slug.markAsDirty();
            this.toastrService.show('Slug không hợp lệ, vui lòng kiểm tra lại định dạng', 'Slug không hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return false;
        }

        const canonical = normalizeSlug(raw);
        if (this.collectSessionSlugs().has(canonical)) {
            this.slug.setErrors({ slugDuplicate: true });
            this.slug.markAsDirty();
            this.toastrService.show(
                `Slug "${canonical}" đã được dùng cho một suất diễn khác trong show này`,
                'Slug trùng lặp',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return false;
        }

        return true;
    }

    /** Builds a server-error message that includes the rejected slug when present. */
    private buildSaveErrorMessage(error: HttpErrorResponse): string {
        const base = getErrorMessageFromResponse(error);
        const rejected = error?.error?.slug as string | undefined;
        return rejected ? `${base} (slug: ${rejected})` : base;
    }

    onButtonCreateSessionPressed() {
        this.openEditShowCard = true;
        this.showSessionDraft = null;
        // Clear any session being edited so this is treated as a brand-new draft: the submit takes
        // the create branch and the new session's slug is checked against *all* existing sessions.
        this.showSession = null;
        this.step = 'basic-info';
        this.slugManuallyEdited = false;
        this.slugHasStored = false;
        this.originalSlug = '';
        this.editSessionForm.patchValue({
            sessionName: '',
            code: '',
            address: '',
            startTime: new Date(),
            endTime: new Date(),
            requireDelivery: false,
            orderNote: '',
            city: null,
            isFree: false,
            showInHome: false,
            ledgendImage: undefined,
            eventSquareImage: undefined,
            eventNameImage: undefined,
            manageTicket: false,
            qrcodeEmail: false,
        });
        this.patchCalendarFields(undefined, true);
        this.inputCity.nativeElement.value = '';
        this.ledgendImageUrl = undefined;
        this.eventSquareImageUrl = undefined;
        this.eventNameImageUrl = undefined;
    }
    onButtonUpdateSessionDraftPressed(item: ShowSessionDraftModel) {
        this.openEditShowCard = true;
        this.showSessionDraft = item;
        this.step = 'basic-info';

        // A stored slug is shown as-is; otherwise let it default from the session name.
        this.slugHasStored = !!item.slug;
        this.slugManuallyEdited = false;
        this.originalSlug = item.slug ? normalizeSlug(item.slug) : '';
        this.editSessionForm.patchValue({
            sessionName: item.sessionName,
            code: item.code?.trim(),
            address: item.address,
            startTime: new Date(item.startTime),
            endTime: new Date(item.endTime),
            requireDelivery: item.requireDelivery,
            orderNote: item.orderNote,
            city: item.city?.id,
            isFree: item.isFree,
            showInHome: item.showInHome,
            ledgendImage: item.ledgendImage,
            eventSquareImage: item.eventSquareImage,
            eventNameImage: item.eventNameImage,
            manageTicket: item.manageTicket,
            qrcodeEmail: item.qrcodeEmail,
        });
        this.patchCalendarFields(item, true);
        if (item.slug) {
            this.slug.setValue(item.slug, { emitEvent: false });
        }
        this.inputCity.nativeElement.value = item.city?.name ?? '';
        this.ledgendImageUrl = item.ledgendImageUrl;
        this.eventSquareImageUrl = item.eventSquareImageUrl;
        this.eventNameImageUrl = item.eventNameImageUrl;
    }

    /** Soft-deletes a session draft after confirm; removes related draft data and temp images on BE. */
    onButtonDeleteSessionDraftPressed(item: ShowSessionDraftModel) {
        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        const loading$ = new ReplaySubject<boolean>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: item.sessionName || item.code || 'Bản nháp',
                content: 'Bạn có chắc muốn xóa bản nháp này? Ảnh temp và dữ liệu nháp liên quan sẽ bị xóa.',
                loading$: loading$,
                onAction$: onAction$,
            },
        });

        onAction$
            .pipe(filter(act => act === 'confirm'))
            .pipe(tap(_ => loading$.next(true)))
            .pipe(mergeMap(_ => this.adminService.deleteShowSessionDraft$(item.id)))
            .subscribe(
                () => {
                    this.showSessionsDraft = this.showSessionsDraft.filter(d => d.id !== item.id);
                    if (this.showSessionDraft?.id === item.id) {
                        this.showSessionDraft = undefined;
                        this.openEditShowCard = false;
                        this.step = 'sessions';
                    }
                    loading$.next(false);
                    this.toastrService.show('Đã xóa bản nháp', 'Xóa bản nháp thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                error => {
                    loading$.next(false);
                    this.toastrService.show(getErrorMessageFromResponse(error), 'Xóa bản nháp thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            );
    }
    onButtonUpdateSessionPressed(item: ShowSessionModel) {
        this.openEditShowCard = true;
        this.showSessionDraft = null;
        this.showSession = item;
        this.step = 'basic-info';

        // A stored slug is shown as-is; otherwise let it default from the session name.
        this.slugHasStored = !!item.slug;
        this.slugManuallyEdited = false;
        this.originalSlug = item.slug ? normalizeSlug(item.slug) : '';
        this.editSessionForm.patchValue({
            sessionName: item.sessionName,
            code: item.code,
            address: item.venue,
            startTime: new Date(item.startTime),
            endTime: new Date(item.endTime),
            requireDelivery: item.requireDelivery,
            orderNote: item.orderNote ?? '',
            city: item.city?.id,
            isFree: item.isFree,
            showInHome: item.showInHome,
            ledgendImage: null,
            eventSquareImage: null,
            eventNameImage: null,
            manageTicket: item.manageTicket,
            qrcodeEmail: item.qrcodeEmail,
        });
        this.patchCalendarFields(item, TYPE_CHANGEABLE_STATUSES.includes(item.status));
        if (item.slug) {
            this.slug.setValue(item.slug, { emitEvent: false });
        }
        if (item.city?.name?.trim() && this.inputCity) {
            this.inputCity.nativeElement.value = item.city?.name;
        }
        this.ledgendImageUrl = item.ledgendImageUrl;
        this.eventSquareImageUrl = item.eventSquareImageUrl;
        this.eventNameImageUrl = item.eventNameImageUrl;
    }

    /**
     * Đổ loại suất + cấu hình lịch của suất/nháp đang sửa (undefined = tạo mới) vào form. Khoảng ngày lấy theo giờ VN
     * từ startTime/endTime để không lệch ngày khi máy admin ở múi giờ khác.
     */
    private patchCalendarFields(
        source: Pick<ShowSessionModel, 'sessionType' | 'calendar' | 'startTime' | 'endTime'> | undefined,
        typeEditable: boolean,
    ) {
        const type: SessionType = source?.sessionType ?? 'SINGLE';
        const calendar = source?.calendar;
        const range =
            type === 'CALENDAR' && source
                ? {
                      start: ymdToLocalDate(epochToVnYmd(source.startTime)),
                      end: ymdToLocalDate(epochToVnYmd(source.endTime)),
                  }
                : null;
        // patchValue luôn phát valueChanges của sessionType nên ô Quản lý vé được khóa/mở theo loại suất.
        this.editSessionForm.patchValue({
            sessionType: type,
            calendarRange: range,
            openTime: calendar?.openTime ?? DEFAULT_OPEN_TIME,
            closeTime: calendar?.closeTime ?? DEFAULT_CLOSE_TIME,
            bookingWindowDays: calendar?.bookingWindowDays ?? DEFAULT_BOOKING_WINDOW_DAYS,
            allowSameDaySale: calendar?.sameDayCutoffMinutes !== null && calendar?.sameDayCutoffMinutes !== undefined,
            sameDayCutoffMinutes: calendar?.sameDayCutoffMinutes ?? DEFAULT_SAME_DAY_CUTOFF_MINUTES,
        });
        if (typeEditable) {
            this.sessionType.enable({ emitEvent: false });
        } else {
            this.sessionType.disable({ emitEvent: false });
        }
    }

    /**
     * Thời gian + loại suất gửi lên backend. Vé theo lịch: startTime/endTime là 00:00 ngày đầu và 23:59 ngày cuối theo
     * giờ VN (dựng từ chuỗi ngày + offset +07:00, không phụ thuộc múi giờ máy). Trả null (kèm toast) khi cấu hình lịch
     * không hợp lệ — kiểm tra giống CalendarRules.validateConfig của backend.
     */
    private buildSessionTiming(formValue: { startTime: string; endTime: string }): SessionTiming | null {
        if (!this.isCalendar) {
            return {
                startTime: new Date(formValue.startTime).setSeconds(0),
                endTime: new Date(formValue.endTime).setSeconds(0),
                sessionType: 'SINGLE',
            };
        }

        const range: NbCalendarRange<Date> | null = this.calendarRange.value;
        const openTime: string = this.openTime.value ?? '';
        const closeTime: string = this.closeTime.value ?? '';
        const bookingWindowDays = Number(this.bookingWindowDays.value);
        const cutoff = this.allowSameDaySale.value ? Number(this.sameDayCutoffMinutes.value) : null;

        let error: string | undefined;
        if (!range?.start || !range?.end) {
            error = 'Vui lòng chọn khoảng ngày bán vé';
        } else if (!TIME_PATTERN.test(openTime) || !TIME_PATTERN.test(closeTime)) {
            error = 'Giờ mở/đóng cửa không hợp lệ (HH:mm)';
        } else if (closeTime <= openTime) {
            error = 'Giờ đóng cửa phải sau giờ mở cửa';
        } else if (
            !Number.isInteger(bookingWindowDays) ||
            bookingWindowDays < 1 ||
            bookingWindowDays > MAX_BOOKING_WINDOW_DAYS
        ) {
            error = `Số ngày bán trước phải từ 1 đến ${MAX_BOOKING_WINDOW_DAYS}`;
        } else if (cutoff !== null && (!Number.isInteger(cutoff) || cutoff < 0)) {
            error = 'Số phút ngừng bán trong ngày phải là số nguyên không âm';
        }
        if (error) {
            this.toastrService.show(error, 'Cấu hình vé theo lịch chưa hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return null;
        }

        return {
            startTime: vnEpoch(toYmd(range.start)),
            endTime: vnEpoch(toYmd(range.end), '23:59'),
            sessionType: 'CALENDAR',
            calendar: { openTime, closeTime, bookingWindowDays, sameDayCutoffMinutes: cutoff },
        };
    }

    onStepPressed(step: string) {
        if (step === 'sessions') {
            this.showSessionDraft = null;
            this.showSession = null;
        }
        this.step = step;
    }

    onLedgendImagePicked(event: Event) {
        if ((event.target as HTMLInputElement).files && (event.target as HTMLInputElement).files.length) {
            const formulario = new FormData();
            formulario.append('imageFile', (event.target as HTMLInputElement).files[0]);
            this.loading += 1;
            this.showService
                .uploadImage$(formulario)
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
                        this.editSessionForm.patchValue({ ledgendImage: data.fileName });
                        this.ledgendImageUrl = data.fileUrl;
                        this.toastrService.show(
                            `Upload hình ảnh thành công show ${this.show.showName || this.show.code}, ${data.fileName}`,
                            'Upload hình ảnh thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        console.log(error);
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Upload hình ảnh thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }

    onEventSquareImagePicked(event: Event) {
        if ((event.target as HTMLInputElement).files && (event.target as HTMLInputElement).files.length) {
            const formulario = new FormData();
            formulario.append('imageFile', (event.target as HTMLInputElement).files[0]);
            this.loading += 1;
            this.showService
                .uploadImage$(formulario)
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
                        this.editSessionForm.patchValue({ eventSquareImage: data.fileName });
                        this.eventSquareImageUrl = data.fileUrl;
                        this.toastrService.show(
                            `Upload hình ảnh thành công show ${this.show.showName || this.show.code}, ${data.fileName}`,
                            'Upload hình ảnh thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        console.log(error);
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Upload hình ảnh thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }

    onEventNameImagePicked(event: Event) {
        if ((event.target as HTMLInputElement).files && (event.target as HTMLInputElement).files.length) {
            const formulario = new FormData();
            formulario.append('imageFile', (event.target as HTMLInputElement).files[0]);
            this.loading += 1;
            this.showService
                .uploadImage$(formulario)
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
                        this.editSessionForm.patchValue({ eventNameImage: data.fileName });
                        this.eventNameImageUrl = data.fileUrl;
                        this.toastrService.show(
                            `Upload hình ảnh thành công show ${this.show.showName || this.show.code}, ${data.fileName}`,
                            'Upload hình ảnh thành công',
                            { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                        );
                    },
                    error => {
                        console.log(error);
                        this.toastrService.show(getErrorMessageFromResponse(error), 'Upload hình ảnh thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }

    async onInputCityChange(value: string) {
        this.filteredCityList$.next(this.filterCity(value));
    }

    private filterCity(input: string): CityModel[] {
        const filterValue = removeAccents(input.toLowerCase());
        return this.cityList.filter(
            optionValue =>
                !input || input === '' || removeAccents(optionValue.name.toLowerCase()).indexOf(filterValue) > -1,
        );
    }

    onSelectionCityChange($event: string) {
        if (!this.inputCity) return;

        this.filteredCityList$.next(this.cityList.filter(s => s.id === $event));
        this.selectedCity = this.cityList.filter(s => s.id === $event)[0];
        this.inputCity.nativeElement.value = this.selectedCity?.name;
        // this.editSessionForm.patchValue({'city': this.selectedCity});
    }

    updateSessionsDraft(model: ShowSessionDraftModel) {
        for (let i = 0; i < this.showSessionsDraft.length; i++) {
            if (this.showSessionsDraft[i].id === model.id) {
                this.showSessionsDraft[i] = model;
                this.gotoNextStep();
            }
        }
        if (this.showSessionDraft.id === model.id) {
            this.showSessionDraft = model;
        }
    }

    updateSession(model: ShowSessionModel) {
        for (let i = 0; i < this.showSessions.length; i++) {
            if (this.showSessions[i].id === model.id) {
                this.showSessions[i] = model;
                this.gotoNextStep();
            }
        }
        if (this.showSession.id === model.id) {
            this.showSession = model;
        }
    }
    gotoNextStep() {
        switch (this.step) {
            case 'basic-info':
                this.step = 'update-seat-plan';
                break;
            case 'update-seat-plan':
                this.step = 'update-payment';
                break;
            case 'update-payment':
                this.step = 'update-order-custom-fields';
                break;
            case 'update-order-custom-fields':
                this.step = 'update-promotions';
                break;
            case 'update-promotions':
                this.step = 'email-template';
                break;
            case 'email-template':
                this.step = 'summary';
                break;
        }
    }

    createSessionSuccess() {
        this.step = 'sessions';
        this.showSessionDraft = null;

        this.loading += 1;
        this.showService
            .getShowSessions$(this.show.id)
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
            .subscribe(sessions => {
                this.showSessions = sessions;
                this.sessions$.next(sessions);
            });
        this.loading += 1;
        this.adminService
            .getShowSessionsDraft$(this.show.id)
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
            .subscribe(response => {
                this.showSessionsDraft = response;
            });
    }

    onSubmit() {
        if (this.editSessionForm.invalid) {
            this.editSessionForm.markAsDirty();
            this.toastrService.show('Vui lòng kiểm tra lại', `Dữ liệu nhập không đúng`, {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }

        if (this.qrcodeEmail.value === true && this.manageTicket.value !== true) {
            this.toastrService.show(
                'Vui lòng kiểm tra lại',
                `Gửi kèm qrcode trong email chỉ được bật khi Quản lý vé được bật`,
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        // Apply slug format + within-show uniqueness before saving.
        if (!this.validateSlugBeforeSave()) {
            return;
        }
        const slug = this.slug.value?.trim() || undefined;

        const data = JSON.parse(JSON.stringify(this.editSessionForm.value));
        const timing = this.buildSessionTiming(data);
        if (!timing) {
            return;
        }

        if (this.showSession) {
            this.loading += 1;
            this.showService
                .updateShowSession$(
                    this.show.id,
                    this.showSession.id,
                    this.sessionName.value,
                    this.code.value?.trim(),
                    this.address.value,
                    timing.startTime,
                    timing.endTime,
                    this.requireDelivery.value,
                    this.orderNote.value,
                    this.city.value,
                    this.isFree.value,
                    this.showInHome.value,
                    this.ledgendImage.value,
                    this.eventSquareImage.value,
                    this.eventNameImage.value,
                    this.manageTicket.value,
                    this.qrcodeEmail.value,
                    slug,
                    timing.sessionType,
                    timing.calendar,
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
                            this.updateSession(model);
                            this.toastrService.show('Cập nhật thông tin cơ bản thành công', 'Cập nhật thành công', {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: 'success',
                            });
                        }
                    },
                    error => {
                        this.toastrService.show(this.buildSaveErrorMessage(error), 'Cập nhật thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                    () => {},
                );
        } else if (!this.showSessionDraft) {
            this.loading += 1;
            this.adminService
                .createShowSessionDraft$(
                    this.show?.id ?? '',
                    this.sessionName.value,
                    this.code.value?.trim(),
                    this.address.value,
                    timing.startTime,
                    timing.endTime,
                    this.requireDelivery.value,
                    this.orderNote.value,
                    this.city.value,
                    this.isFree.value,
                    this.showInHome.value,
                    this.ledgendImage.value,
                    this.ledgendImageUrl,
                    this.eventSquareImage.value,
                    this.eventSquareImageUrl,
                    this.eventNameImage.value,
                    this.eventNameImageUrl,
                    this.manageTicket.value,
                    this.qrcodeEmail.value,
                    slug,
                    timing.sessionType,
                    timing.calendar,
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
                            this.showSessionsDraft.unshift(model);
                            this.toastrService.show('Tạo session nháp thành công', 'Tạo session nháp thành công', {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: 'success',
                            });
                        } else {
                            this.toastrService.show('Tạo session nháp thất bại', 'Tạo session nháp thất bại', {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: 'danger',
                                duration: 30000,
                            });
                        }
                    },
                    error => {
                        this.toastrService.show(this.buildSaveErrorMessage(error), 'Cập nhật session draft thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        } else {
            this.loading += 1;
            this.adminService
                .updateShowSessionDraft$(
                    this.showSessionDraft.id,
                    this.sessionName.value,
                    this.code.value?.trim(),
                    this.address.value,
                    timing.startTime,
                    timing.endTime,
                    this.requireDelivery.value,
                    this.orderNote.value,
                    this.city.value,
                    this.isFree.value,
                    this.showInHome.value,
                    this.ledgendImage.value,
                    this.ledgendImageUrl,
                    this.eventSquareImage.value,
                    this.eventSquareImageUrl,
                    this.eventNameImage.value,
                    this.eventNameImageUrl,
                    this.manageTicket.value,
                    this.qrcodeEmail.value,
                    slug,
                    timing.sessionType,
                    timing.calendar,
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
                            this.updateSessionsDraft(model);
                            this.toastrService.show('Cập nhật thông tin cơ bản thành công', 'Cập nhật thành công', {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: 'success',
                            });
                        } else {
                            this.toastrService.show('Cập nhật thất bại', 'Cập nhật thất bại', {
                                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                                status: 'danger',
                                duration: 30000,
                            });
                        }
                    },
                    error => {
                        this.toastrService.show(this.buildSaveErrorMessage(error), 'Cập nhật session draft thất bại', {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'danger',
                            duration: 30000,
                        });
                    },
                );
        }
    }

    async onActionPressed(event: { action: SessionActionType; data: ShowSessionModel }) {
        const data = this.showSessions.find(s => s.id === event.data.id);
        switch (event.action) {
            case 'edit': {
                this.onButtonUpdateSessionPressed(data);
                break;
            }
            case 'market-link': {
                await this.onButtonGetMarketTicketLinkPressed(data);
                break;
            }
            case 'scanner-link': {
                await this.onButtonGetScannerLinkPressed(data);
                break;
            }
            case 'request-approve': {
                await this.onButtonRequestApproveShowSession(data);
                break;
            }
            case 'reject': {
                await this.onButtonRejectShowSession(data);
                break;
            }
            case 'approve': {
                await this.onButtonApproveShowSession(data);
                break;
            }
            case 'open': {
                await this.onButtonStartSaleShowSession(data);
                break;
            }
            case 'stop': {
                await this.onButtonStopSaleShowSession(data);
                break;
            }
            case 'pause': {
                await this.onButtonPauseSaleShowSession(data);
                break;
            }
            case 'terminate': {
                await this.onButtonTerminateShowSession(data);
                break;
            }
            default: {
                break;
            }
        }
    }

    async onButtonGetMarketTicketLinkPressed(item: ShowSessionModel) {
        // Unsaved-draft guard (Requirement 5.5): a session without an id has not been saved yet,
        // so there is no Booking_Page_Link to retrieve. Surface an error toast, send no request,
        // and leave the clipboard untouched.
        if (!item?.id) {
            this.toastrService.show(
                `${item?.sessionName || item?.code || ''} - Vui lòng lưu suất diễn trước khi lấy link`,
                'Lấy link thất bại',
                {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'danger',
                    duration: 30000,
                },
            );
            return;
        }

        this.loading += 1;
        this.showService
            .getMarketTicketLink$(this.show.id, item.id)
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
                link => {
                    this.clipboard.copy(link);

                    this.toastrService.show(`${item.sessionName || item.code}`, 'Lấy link thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                error => {
                    console.log(error);
                    this.toastrService.show(`${item.sessionName || item.code} - ${error.error}`, 'Lấy link thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
                () => {},
            );
    }

    async onButtonGetScannerLinkPressed(item: ShowSessionModel) {
        this.loading += 1;
        this.qrcodeService
            .getQRCodeScannerLink(item.id)
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
                link => {
                    this.clipboard.copy(link);

                    this.toastrService.show(`${item.sessionName || item.code}`, 'Lấy link thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                error => {
                    console.log(error);
                    this.toastrService.show(`${item.sessionName || item.code} - ${error.error}`, 'Lấy link thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
                () => {},
            );
    }

    async onButtonRequestApproveShowSession(item: ShowSessionModel) {
        if (!['Created'].includes(item.status)) {
            this.toastrService.show(
                `${item.sessionName || item.code} - ${item.status}`,
                'Gửi yêu cầu phê duyệt suất diễn thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        const loading$ = new ReplaySubject<boolean>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: `${item.sessionName || item.code}`,
                content: 'Gửi yêu cầu phê duyệt suất diễn',
                loading$: loading$,
                onAction$: onAction$,
            },
        });

        onAction$
            .pipe(filter(act => act === 'confirm'))
            .pipe(tap(_ => loading$.next(true)))
            .pipe(mergeMap(_ => this.showService.requestApproveShowSession$(this.show.id, item.id)))
            .subscribe(
                ss => {
                    const idx = (this.showSessions = this.showSessions.map(
                        obj => this.showSessions.find(o => o.id === obj.id && o.id !== ss.id) || ss,
                    ));
                    this.sessions$.next(this.showSessions);
                    loading$.next(false);
                    this.toastrService.show(
                        `${item.sessionName || item.code}`,
                        'Gửi yêu cầu phê duyệt suất diễn thành công',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                    );
                },
                error => {
                    loading$.next(false);
                    this.toastrService.show(
                        `${item.sessionName || item.code} - ${error.error}`,
                        'Gửi yêu cầu phê duyệt suất diễn thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
                () => {},
            );
    }

    async onButtonRejectShowSession(item: ShowSessionModel) {
        if (!['ApprovalWait'].includes(item.status)) {
            this.toastrService.show(
                `${item.sessionName || item.code} - ${item.status}`,
                'Từ chối yêu cầu phê duyệt suất diễn thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        const loading$ = new ReplaySubject<boolean>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: `${item.sessionName || item.code}`,
                content: 'Bạn có chắc muốn từ chối yêu phê duyệt suất diễn?',
                loading$: loading$,
                onAction$: onAction$,
            },
        });

        onAction$
            .pipe(filter(act => act === 'confirm'))
            .pipe(tap(_ => loading$.next(true)))
            .pipe(mergeMap(_ => this.showService.rejectShowSession$(this.show.id, item.id)))
            .subscribe(
                ss => {
                    this.showSessions = this.showSessions.map(
                        obj => this.showSessions.find(o => o.id === obj.id && o.id !== ss.id) || ss,
                    );
                    this.sessions$.next(this.showSessions);
                    loading$.next(false);
                    this.toastrService.show(
                        `${item.sessionName || item.code}`,
                        'Từ chối yêu cầu phê duyệt suất diễn thành công',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'success' },
                    );
                },
                error => {
                    loading$.next(false);
                    this.toastrService.show(
                        `${item.sessionName || item.code} - ${error.error}`,
                        'Từ chối yêu cầu phê duyệt suất diễn thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
                () => {},
            );
    }

    async onButtonApproveShowSession(item: ShowSessionModel) {
        if (!['ApprovalWait'].includes(item.status)) {
            this.toastrService.show(
                `${item.sessionName || item.code} - ${item.status}`,
                'Phê duyệt suất diễn thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        const loading$ = new ReplaySubject<boolean>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: `${item.sessionName || item.code}`,
                content: 'Bạn có muốn phê duyệt suất diễn?',
                loading$: loading$,
                onAction$: onAction$,
            },
        });

        onAction$
            .pipe(filter(act => act === 'confirm'))
            .pipe(tap(_ => loading$.next(true)))
            .pipe(mergeMap(_ => this.showService.approveShowSession$(this.show.id, item.id)))
            .subscribe(
                ss => {
                    this.showSessions = this.showSessions.map(
                        obj => this.showSessions.find(o => o.id === obj.id && o.id !== ss.id) || ss,
                    );
                    this.sessions$.next(this.showSessions);
                    this.changeDetectorRef.detectChanges();
                    loading$.next(false);
                    this.toastrService.show(`${item.sessionName || item.code}`, 'Phê duyệt suất diễn thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                error => {
                    loading$.next(false);
                    this.toastrService.show(
                        `${item.sessionName || item.code} - ${error.error}`,
                        'Phê duyệt suất diễn thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
                () => {},
            );
    }

    async onButtonStartSaleShowSession(item: ShowSessionModel) {
        if (!['Paused', 'OpeningWait'].includes(item.status)) {
            this.toastrService.show(`${item.sessionName || item.code} - ${item.status}`, 'Mở bán suất diễn thất bại', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
                duration: 30000,
            });
            return;
        }

        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        const loading$ = new ReplaySubject<boolean>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: `${item.sessionName || item.code}`,
                content: 'Vé sẽ được mở bán ngay?',
                loading$: loading$,
                onAction$: onAction$,
            },
        });

        onAction$
            .pipe(filter(act => act === 'confirm'))
            .pipe(tap(_ => loading$.next(true)))
            .pipe(mergeMap(_ => this.showService.startShowSession$(this.show.id, item.id)))
            .subscribe(
                ss => {
                    this.showSessions = this.showSessions.map(
                        obj => this.showSessions.find(o => o.id === obj.id && o.id !== ss.id) || ss,
                    );
                    this.sessions$.next(this.showSessions);
                    loading$.next(false);
                    this.toastrService.show(`${item.sessionName || item.code}`, 'Mở bán vé thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                error => {
                    loading$.next(false);
                    this.toastrService.show(`${item.sessionName || item.code} - ${error.error}`, 'Mở bán vé thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
                () => {},
            );
    }

    async onButtonStopSaleShowSession(item: ShowSessionModel) {
        if (item.status !== 'Open') {
            this.toastrService.show(
                `${item.sessionName || item.code} - ${item.status}`,
                'Dừng bán suất diễn thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        const loading$ = new ReplaySubject<boolean>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: `${item.sessionName || item.code}`,
                content: 'Vé sẽ được dừng bán ngay?',
                onAction$: onAction$,
            },
        });

        onAction$
            .pipe(filter(act => act === 'confirm'))
            .pipe(tap(_ => loading$.next(true)))
            .pipe(mergeMap(_ => this.showService.stopShowSession$(this.show.id, item.id)))
            .subscribe(
                ss => {
                    this.showSessions = this.showSessions.map(
                        obj => this.showSessions.find(o => o.id === obj.id && o.id !== ss.id) || ss,
                    );
                    this.sessions$.next(this.showSessions);
                    loading$.next(false);
                    this.toastrService.show(`${item.sessionName || item.code}`, 'Dừng bán vé thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                error => {
                    loading$.next(false);
                    this.toastrService.show(
                        `${item.sessionName || item.code} - ${error.error}`,
                        'Dừng bán vé thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
                () => {},
            );
    }

    async onButtonPauseSaleShowSession(item: ShowSessionModel) {
        if (item.status !== 'Open') {
            this.toastrService.show(
                `${item.sessionName || item.code} - ${item.status}`,
                'Tạm dừng suất diễn thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        const loading$ = new ReplaySubject<boolean>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: `${item.sessionName || item.code}`,
                content: 'Vé sẽ tạm dừng bán ngay?',
                onAction$: onAction$,
            },
        });

        onAction$
            .pipe(filter(act => act === 'confirm'))
            .pipe(tap(_ => loading$.next(true)))
            .pipe(mergeMap(_ => this.showService.pauseShowSession$(this.show.id, item.id)))
            .subscribe(
                ss => {
                    this.showSessions = this.showSessions.map(
                        obj => this.showSessions.find(o => o.id === obj.id && o.id !== ss.id) || ss,
                    );
                    this.sessions$.next(this.showSessions);
                    loading$.next(false);
                    this.toastrService.show(`${item.sessionName || item.code}`, 'Tạm dừng bán vé thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                error => {
                    loading$.next(false);
                    this.toastrService.show(
                        `${item.sessionName || item.code} - ${error.error}`,
                        'Tạm dừng bán vé thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
                () => {},
            );
    }

    async onButtonTerminateShowSession(item: ShowSessionModel) {
        if (!['Rejected', 'Stopped'].includes(item.status)) {
            this.toastrService.show(
                `${item.sessionName || item.code} - ${item.status}`,
                'Kết thúc suất diễn thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        const onAction$ = new ReplaySubject<'confirm' | 'close' | undefined>();
        const loading$ = new ReplaySubject<boolean>();

        this.dialogService.open(DialogConfirmComponent, {
            closeOnBackdropClick: false,
            context: {
                title: `${item.sessionName || item.code}`,
                content: 'Suất diễn sẽ kêt thúc ngay?',
                onAction$: onAction$,
            },
        });

        onAction$
            .pipe(filter(act => act === 'confirm'))
            .pipe(tap(_ => loading$.next(true)))
            .pipe(mergeMap(_ => this.showService.terminateShowSession$(this.show.id, item.id)))
            .subscribe(
                ss => {
                    item.status = ss.status;
                    this.showSessions = this.showSessions.map(
                        obj => this.showSessions.find(o => o.id === obj.id && o.id !== ss.id) || ss,
                    );
                    loading$.next(false);
                    this.toastrService.show(`${item.sessionName || item.code}`, 'Kết thúc suất diễn thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                },
                error => {
                    loading$.next(false);
                    this.toastrService.show(
                        `${item.sessionName || item.code} - ${error.error}`,
                        'Kết thúc suất diễn thất bại',
                        { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
                    );
                },
                () => {},
            );
    }

    url(value: string): string {
        if (!value) return '';
        return `url(${value})`;
    }
}
