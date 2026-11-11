import { Component, OnDestroy } from '@angular/core';
import { AbstractControl, FormBuilder, FormControl, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { ShowService } from '../../@core/metaticket/data.service';
import { EventCategoryModel, ShowModel } from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { generateDefaultSlug, isValidSlug, normalizeSlug, SlugError } from '../../@core/metaticket/utils/slug.util';

@Component({
    templateUrl: './create_show.component.html',
    styleUrls: ['./create_show.component.scss'],
    selector: 'create-show',
})
export class CreateShowComponent implements OnDestroy {
    show: ShowModel | null = null;
    eventCats: EventCategoryModel[] = [];
    submitButtonPressed = false;
    bannerImageUrl: string | null = null;
    detailImageUrl: string | null = null;
    thumbnailUrl: string | null = null;
    logoShowImageUrl: string | null = null;
    logoHostImageUrl: string;

    loading: number = 0;

    /** Expose the SlugError enum to the template for inline messaging. */
    readonly SlugError = SlugError;

    /**
     * Canonical slugs already in use by *other* shows, used for both default-slug
     * suffixing and the inline duplicate check. Populated from `getShows$()`
     * (i.e. `fetchAllShow()`) and excludes the currently edited show's own slug.
     */
    private allShowSlugs: Set<string> = new Set<string>();

    /** A show being edited already carries a stored slug, so no default is generated. */
    private hasStoredSlug = false;

    /** The organizer has manually edited the slug field, so stop auto-generating a default. */
    private slugManuallyEdited = false;

    /**
     * Canonical form of the slug stored on the loaded show ('' when the show has none). Used to
     * detect when an edited slug has been reverted back to its original value, in which case the
     * field is treated as if it was never touched.
     */
    private originalSlug = '';

    /** The slug rejected by the Show_Service on the last submit. */
    serverSlugError: string | null = null;

    private readonly subscriptions = new Subscription();

    /** Format validator mirroring the server `SlugValidator`. */
    private readonly slugFormatValidator = (control: AbstractControl): ValidationErrors | null => {
        const error = isValidSlug(control.value);
        return error ? { slugFormat: error } : null;
    };

    /** Duplicate validator against other shows' canonical slugs. */
    private readonly slugDuplicateValidator = (control: AbstractControl): ValidationErrors | null => {
        const raw = control.value;
        if (raw == null || `${raw}`.trim().length === 0) {
            return null;
        }
        const canonical = normalizeSlug(raw);
        if (canonical.length === 0) {
            return null;
        }
        return this.allShowSlugs.has(canonical) ? { slugDuplicate: true } : null;
    };

    createShowForm = this.formBuilder.group({
        name: new FormControl('', Validators.required),
        code: new FormControl('', Validators.required),
        slug: new FormControl('', [this.slugFormatValidator, this.slugDuplicateValidator]),
        eventCategory: new FormControl('', Validators.required),
        bannerImage: new FormControl(null, Validators.required),
        detailImageUrl: new FormControl(null, Validators.required),
        thumbnailUrl: new FormControl(null, Validators.required),
        logoShowImage: new FormControl(null),
        logoHostImage: new FormControl(null),
        description: new FormControl(''),
        ticketDescription: new FormControl(''),
        policy: new FormControl(''),
        faq: new FormControl(''),
        hideSessions: new FormControl(false),
    });

    get nameFormControl() {
        return <FormControl>this.createShowForm.get('name');
    }

    get codeFormControl() {
        return <FormControl>this.createShowForm.get('code');
    }

    get slugFormControl() {
        return <FormControl>this.createShowForm.get('slug');
    }

    /**
     * Current provenance of the slug, surfaced as a small status label next to the field:
     *  - 'auto'    → auto-generated from the show name (no stored slug, not edited by hand)
     *  - 'manual'  → a previously saved slug shown unchanged
     *  - 'editing' → the organizer has edited the slug by hand on this form
     */
    get slugState(): 'auto' | 'manual' | 'editing' {
        if (this.slugManuallyEdited) return 'editing';
        if (this.hasStoredSlug) return 'manual';
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
                return 'Tự động theo tên sự kiện';
        }
    }

    get hideSessionsFormControl(): FormControl {
        return <FormControl>this.createShowForm.get('hideSessions');
    }

    get bannerImageFormControl() {
        return <FormControl>this.createShowForm.get('bannerImage');
    }

    get detailImageUrlFormControl() {
        return <FormControl>this.createShowForm.get('detailImageUrl');
    }

    get thumbnailUrlFormControl() {
        return <FormControl>this.createShowForm.get('thumbnailUrl');
    }

    get logoShowImageFormControl() {
        return <FormControl>this.createShowForm.get('logoShowImage');
    }

    get logoHostImageFormControl() {
        return <FormControl>this.createShowForm.get('logoHostImage');
    }

    get descriptionFormControl() {
        return <FormControl>this.createShowForm.get('description');
    }

    get ticketDescriptionFormControl() {
        return <FormControl>this.createShowForm.get('ticketDescription');
    }

    get policyFormControl() {
        return <FormControl>this.createShowForm.get('policy');
    }

    get faqFormControl() {
        return <FormControl>this.createShowForm.get('faq');
    }

    get eventCategoryFormControl() {
        return <FormControl>this.createShowForm.get('eventCategory');
    }

    constructor(
        private formBuilder: FormBuilder,
        private showService: ShowService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private toastrService: NbToastrService,
    ) {
        const showId = activatedRoute.snapshot.queryParams['id'];

        // Track manual slug edits so we stop overwriting the field with a generated default.
        this.subscriptions.add(
            this.slugFormControl.valueChanges.subscribe(value => {
                this.onSlugValueChanged(value);
            }),
        );

        // Auto-generate a default slug from the show name until the organizer edits it
        // by hand or the show already carries a stored slug.
        this.subscriptions.add(
            this.nameFormControl.valueChanges.subscribe(name => {
                this.applyDefaultSlug(name);
            }),
        );

        // Gather the canonical slugs of all other shows for default suffixing and the
        // inline duplicate check. Sourced from fetchAllShow().
        this.subscriptions.add(
            this.showService.getShows$().subscribe(
                shows => {
                    this.allShowSlugs = new Set(
                        (shows ?? [])
                            .filter(s => !showId || s.id !== showId)
                            .map(s => normalizeSlug(s.slug))
                            .filter(slug => slug.length > 0),
                    );
                    // Re-run the duplicate validator and refresh any pending default now
                    // that the taken-slug set is known. emitEvent:false so re-validating does NOT
                    // fire slugFormControl.valueChanges (which would falsely flag a manual edit).
                    this.applyDefaultSlug(this.nameFormControl.value);
                    this.slugFormControl.updateValueAndValidity({ emitEvent: false });
                },
                () => {
                    // A failed lookup must not block show creation; defaults/validation
                    // simply proceed against an empty taken-set and the server stays the
                    // authority on uniqueness.
                },
            ),
        );

        this.loading += 1;
        this.showService
            .getEventCategory$()
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
                cat => {
                    this.eventCats = cat;
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
                                    // Decide slug behavior before touching the name control so
                                    // its valueChanges does not auto-generate over a stored slug.
                                    this.hasStoredSlug = !!(show.slug && show.slug.trim().length > 0);
                                    this.originalSlug = this.hasStoredSlug ? normalizeSlug(show.slug) : '';
                                    this.createShowForm.controls['name'].setValue(show.showName);
                                    this.createShowForm.controls['code'].setValue(show.code);
                                    this.createShowForm.controls['description'].setValue(show.description);
                                    this.createShowForm.controls['ticketDescription'].setValue(
                                        show.ticketDescription ?? '',
                                    );
                                    this.createShowForm.controls['policy'].setValue(show.policy ?? '');
                                    this.createShowForm.controls['faq'].setValue(show.faq ?? '');
                                    this.createShowForm.controls['bannerImage'].setValue(show.bannerImageUrl);
                                    this.createShowForm.controls['detailImageUrl'].setValue(show.detailImageUrl);
                                    this.createShowForm.controls['thumbnailUrl'].setValue(show.thumbnailUrl);
                                    this.createShowForm.controls['logoShowImage'].setValue(show.logoShowUrl);
                                    this.createShowForm.controls['logoHostImage'].setValue(show.logoHostUrl);
                                    // Coerce to boolean so Nebular checkbox reflects API true/false correctly
                                    this.createShowForm.controls['hideSessions'].setValue(!!show.hideSessions);
                                    if (this.hasStoredSlug) {
                                        // Display the stored slug as-is; no generated default.
                                        this.slugFormControl.setValue(show.slug, { emitEvent: false });
                                    }
                                    this.bannerImageUrl = show.bannerImageUrl;
                                    this.detailImageUrl = show.detailImageUrl;
                                    this.thumbnailUrl = show.thumbnailUrl;
                                    this.logoHostImageUrl = show.logoHostUrl;
                                    this.logoShowImageUrl = show.logoShowUrl;
                                    this.eventCategoryFormControl.setValue(this.show.eventCategoryCode);
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

    /**
     * React to a *manual* slug change. Programmatic updates use `emitEvent:false` and never reach
     * here, so this only fires when the organizer types in the field. Three outcomes mirroring
     * {@link slugState}:
     *  - cleared (empty/whitespace) → drop back to auto mode so editing the show name fills the
     *    slug again on the next name change;
     *  - reverted to the original stored slug → restore the pristine "manual (stored)" state, as if
     *    the field had never been touched (only applies when the show loaded with a stored slug);
     *  - anything else → mark the field as manually edited so the default stops overwriting it.
     */
    private onSlugValueChanged(value: string | null | undefined): void {
        this.serverSlugError = null;
        const raw = (value ?? '').trim();

        if (raw.length === 0) {
            // Cleared: revert to auto so the next show-name edit regenerates a default.
            this.slugManuallyEdited = false;
            this.hasStoredSlug = false;
            return;
        }

        if (this.originalSlug.length > 0 && normalizeSlug(raw) === this.originalSlug) {
            // Re-typed the original stored slug: treat it as never edited.
            this.slugManuallyEdited = false;
            this.hasStoredSlug = true;
            return;
        }

        this.slugManuallyEdited = true;
    }

    /**
     * Set the slug field to a generated default derived from the show name, unless the
     * show already has a stored slug or the organizer has edited the slug by hand.
     * An empty canonical name leaves the field blank so submission is still allowed
     * without a slug.
     */
    private applyDefaultSlug(name: string | null | undefined): void {
        if (this.hasStoredSlug || this.slugManuallyEdited) {
            return;
        }
        const generated = generateDefaultSlug((name ?? '').trim(), this.allShowSlugs);
        // emitEvent:false so this programmatic change is not mistaken for a manual edit.
        this.slugFormControl.setValue(generated, { emitEvent: false });
    }

    ngOnDestroy(): void {
        this.subscriptions.unsubscribe();
    }

    onBannerImagePicked(event: Event) {
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
                        this.createShowForm.patchValue({ bannerImage: data.fileName });
                        this.bannerImageUrl = data.fileUrl;
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

    onDetailImageUrlPicked(event: Event) {
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
                        this.createShowForm.patchValue({ detailImageUrl: data.fileName });
                        this.detailImageUrl = data.fileUrl;
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

    onThumbnailUrlPicked(event: Event) {
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
                        this.createShowForm.patchValue({ thumbnailUrl: data.fileName });
                        this.thumbnailUrl = data.fileUrl;
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

    onLogoShowPicked(event: Event) {
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
                        this.createShowForm.patchValue({ logoShowImage: data.fileName });
                        this.logoShowImageUrl = data.fileUrl;
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

    onLogoHostPicked(event: Event) {
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
                        this.createShowForm.patchValue({ logoHostImage: data.fileName });
                        this.logoHostImageUrl = data.fileUrl;
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

    /**
     * The slug to submit. The value has already been validated as a canonical slug by the form, so
     * it is sent as entered (only trimmed); empty/whitespace yields '' so the show is saved without a
     * slug.
     */
    private slugToSubmit(): string {
        return (this.slugFormControl.value ?? '').trim();
    }

    /** Surface a slug rejected by the Show_Service while keeping entered form values. */
    private handleSubmitError(error: any, title: string): void {
        const rejectedSlug: string | undefined = error?.error?.slug;
        if (rejectedSlug) {
            this.serverSlugError = rejectedSlug;
        }
        this.toastrService.show(getErrorMessageFromResponse(error), title, {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'danger',
            duration: 30000,
        });
    }

    onSubmit() {
        this.submitButtonPressed = true;
        this.serverSlugError = null;
        if (this.createShowForm.invalid) {
            // markAllAsTouched (not markAsDirty) so the slug control's inline format/duplicate errors
            // actually render: markAsDirty only marks the group and its ancestors, never the children.
            this.createShowForm.markAllAsTouched();
            this.toastrService.show(
                `Tạo/Cập nhật show ${this.show.showName || this.show.code} thất bại`,
                'Tạo/Cập nhật show thất bại',
                { position: NbGlobalPhysicalPosition.TOP_RIGHT, status: 'danger', duration: 30000 },
            );
            return;
        }

        const formulario = new FormData();
        const formData = this.createShowForm.value;

        Object.keys(formData).forEach(key => {
            formulario.append(key, formData[key]);
        });
        if (!this.show) {
            this.loading += 1;
            this.showService
                .createShows$(
                    this.nameFormControl.value,
                    this.codeFormControl.value.trim(),
                    this.bannerImageFormControl.value,
                    this.detailImageUrlFormControl.value,
                    this.thumbnailUrlFormControl.value,
                    this.descriptionFormControl.value,
                    this.ticketDescriptionFormControl.value,
                    this.policyFormControl.value,
                    this.faqFormControl.value,
                    this.eventCategoryFormControl.value,
                    this.eventCats.find(c => c.id === this.eventCategoryFormControl.value)?.name,
                    false,
                    this.hideSessionsFormControl.value,
                    this.logoShowImageFormControl.value,
                    this.logoHostImageFormControl.value,
                    this.slugToSubmit(),
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
                    showModel => {
                        if (showModel) {
                            this.router.navigateByUrl('/portal/show/session/create?showId=' + showModel.id);
                        }
                    },
                    error => {
                        this.handleSubmitError(error, 'Cập nhật thất bại');
                    },
                );
        } else {
            formulario.append('id', this.show.id);
            this.loading += 1;
            this.showService
                .updateShows$(
                    this.show.id,
                    this.nameFormControl.value,
                    this.codeFormControl.value.trim(),
                    this.descriptionFormControl.value,
                    this.ticketDescriptionFormControl.value,
                    this.policyFormControl.value,
                    this.faqFormControl.value,
                    this.eventCategoryFormControl.value,
                    this.eventCats.find(c => c.id === this.eventCategoryFormControl.value)?.name,
                    false,
                    this.hideSessionsFormControl.value,
                    this.bannerImageFormControl.value,
                    this.detailImageUrlFormControl.value,
                    this.thumbnailUrlFormControl.value,
                    this.logoShowImageFormControl.value,
                    this.logoHostImageFormControl.value,
                    this.slugToSubmit(),
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
                    showModel => {
                        if (showModel) {
                            this.router.navigateByUrl('/portal/show/session/create?showId=' + showModel.id);
                        }
                    },
                    error => {
                        this.handleSubmitError(error, 'Cập nhật thất bại');
                    },
                );
        }
    }

    url(value: string | null | undefined): string {
        if (!value) return '';
        return `url(${value})`;
    }
}
