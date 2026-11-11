import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { tap } from 'rxjs/operators';
import { AccessoryShowService } from '../../@core/metaticket/data.service';
import {
    AccessoryDiscountTierModel,
    AccessoryDiscountType,
    AccessoryDiscountUpsertInput,
    AccessoryShowModel,
} from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';

/** Editable tier row for Tiered campaigns. */
interface TierRow {
    minOrderAmount: number;
    type: 'Percentage' | 'FixedAmount';
    value: number;
    maxDiscountAmount: number | null;
}

@Component({
    selector: 'create-accessory-promotion',
    templateUrl: './create-accessory-promotion.component.html',
    styleUrls: ['./create-accessory-promotion.component.scss'],
})
export class CreateAccessoryPromotionComponent implements OnInit {
    loading = 0;
    isEdit = false;
    discountId: string | null = null;
    show: AccessoryShowModel | null = null;
    showId: string | null = null;
    submitPressed = false;
    usedCount = 0;
    tiers: TierRow[] = [];

    readonly typeOptions: AccessoryDiscountType[] = [
        'Percentage',
        'FixedAmount',
        'Tiered',
        'EarlyBird',
    ];

    form: FormGroup = this.fb.group({
        code: ['', Validators.required],
        type: ['Percentage', Validators.required],
        value: [10],
        rewardType: ['Percentage'],
        maxDiscountAmount: [null as number | null],
        minOrderAmount: [0],
        unlimitedUsage: [true],
        usageLimit: [null as number | null],
        startAtLocal: [''],
        endAtLocal: [''],
        active: [true],
    });

    constructor(
        private fb: FormBuilder,
        private accessoryShowService: AccessoryShowService,
        private route: ActivatedRoute,
        private router: Router,
        private toastr: NbToastrService,
    ) {}

    get pageTitle(): string {
        return this.isEdit ? 'Sửa khuyến mãi' : 'Tạo khuyến mãi';
    }

    get type(): AccessoryDiscountType {
        return this.form.get('type')?.value as AccessoryDiscountType;
    }

    get isAutoType(): boolean {
        return this.type === 'Tiered' || this.type === 'EarlyBird';
    }

    get showRewardFields(): boolean {
        return this.type === 'Percentage' || this.type === 'FixedAmount' || this.type === 'EarlyBird';
    }

    /** Loads show (create) or discount (edit). */
    ngOnInit(): void {
        const id = this.route.snapshot.queryParamMap.get('id');
        const showId = this.route.snapshot.queryParamMap.get('showId');
        if (id) {
            this.isEdit = true;
            this.discountId = id;
            this.loadDiscount(id);
            return;
        }
        if (!showId) {
            this.toastr.show('Thiếu showId', 'Thất bại', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'danger',
            });
            this.router.navigateByUrl('/portal/accessory/promotions');
            return;
        }
        this.showId = showId;
        this.loadShow(showId);
        this.tiers = [
            { minOrderAmount: 200000, type: 'FixedAmount', value: 20000, maxDiscountAmount: null },
            { minOrderAmount: 500000, type: 'Percentage', value: 10, maxDiscountAmount: null },
        ];
    }

    /** Sync force Auto + validators when campaign type changes. */
    onTypeChange(): void {
        if (this.type === 'EarlyBird' && !this.form.get('rewardType')?.value) {
            this.form.patchValue({ rewardType: 'Percentage' });
        }
        if (this.type === 'Tiered' && this.tiers.length === 0) {
            this.tiers = [
                { minOrderAmount: 200000, type: 'FixedAmount', value: 20000, maxDiscountAmount: null },
            ];
        }
    }

    /** Adds an empty tier row. */
    addTier(): void {
        this.tiers = [
            ...this.tiers,
            { minOrderAmount: 0, type: 'FixedAmount', value: 0, maxDiscountAmount: null },
        ];
    }

    /** Removes one tier row. */
    removeTier(index: number): void {
        this.tiers = this.tiers.filter((_, i) => i !== index);
    }

    /** Saves create/update payload. */
    submit(): void {
        this.submitPressed = true;
        if (!this.showId) {
            return;
        }
        if (!this.isEdit && this.form.get('code')?.invalid) {
            return;
        }
        const unlimited = !!this.form.get('unlimitedUsage')?.value;
        const usageLimit = unlimited ? null : Number(this.form.get('usageLimit')?.value);
        if (!unlimited && (!usageLimit || usageLimit <= 0)) {
            this.toastr.show('usageLimit phải > 0 hoặc chọn không giới hạn', 'Không hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }

        const type = this.type;
        const input: AccessoryDiscountUpsertInput = {
            code: (this.form.get('code')?.value || '').toString().trim().toUpperCase(),
            accessoryShowId: this.showId,
            type,
            value: Number(this.form.get('value')?.value) || 0,
            rewardType: type === 'EarlyBird' ? this.form.get('rewardType')?.value : null,
            maxDiscountAmount: this.normalizeNullableNumber(this.form.get('maxDiscountAmount')?.value),
            minOrderAmount: Number(this.form.get('minOrderAmount')?.value) || 0,
            tiers: type === 'Tiered' ? this.tiers.map(t => this.toTierModel(t)) : [],
            applyMode: 'Manual',
            usageLimit,
            startAt: this.fromLocalInput(this.form.get('startAtLocal')?.value),
            endAt: this.fromLocalInput(this.form.get('endAtLocal')?.value),
            active: !!this.form.get('active')?.value,
        };

        if (type === 'EarlyBird' && (!input.startAt || !input.endAt)) {
            this.toastr.show('Early Bird cần startAt và endAt', 'Không hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        if (type === 'Tiered' && input.tiers.length === 0) {
            this.toastr.show('Tiered cần ít nhất 1 mốc', 'Không hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }

        this.loading += 1;
        const req$ =
            this.isEdit && this.discountId
                ? this.accessoryShowService.updateDiscount$(this.discountId, input)
                : this.accessoryShowService.createDiscount$(input);

        req$
            .pipe(
                tap({
                    next: () => {
                        this.loading -= 1;
                    },
                    error: () => {
                        this.loading -= 1;
                    },
                }),
            )
            .subscribe({
                next: () => {
                    this.toastr.show(this.isEdit ? 'Đã cập nhật' : 'Đã tạo khuyến mãi', 'Thành công', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                    });
                    this.router.navigateByUrl(
                        '/portal/accessory/promotions?showId=' + encodeURIComponent(this.showId!),
                    );
                },
                error: error => {
                    this.toastr.show(getErrorMessageFromResponse(error), 'Thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 30000,
                    });
                },
            });
    }

    /** Loads show metadata for the breadcrumbs. */
    private loadShow(showId: string): void {
        this.loading += 1;
        this.accessoryShowService
            .getShow$(showId)
            .pipe(
                tap({
                    next: () => {
                        this.loading -= 1;
                    },
                    error: () => {
                        this.loading -= 1;
                    },
                }),
            )
            .subscribe({
                next: show => {
                    this.show = show;
                },
                error: error => {
                    this.toastr.show(getErrorMessageFromResponse(error), 'Không tải được show', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                    });
                },
            });
    }

    /** Loads an existing discount into the form. */
    private loadDiscount(id: string): void {
        this.loading += 1;
        this.accessoryShowService
            .getDiscount$(id)
            .pipe(
                tap({
                    next: () => {
                        this.loading -= 1;
                    },
                    error: () => {
                        this.loading -= 1;
                    },
                }),
            )
            .subscribe({
                next: d => {
                    this.showId = d.accessoryShowId;
                    this.usedCount = d.usedCount || 0;
                    this.form.patchValue({
                        code: d.code,
                        type: d.type,
                        value: d.value,
                        rewardType: d.rewardType || 'Percentage',
                        maxDiscountAmount: d.maxDiscountAmount,
                        minOrderAmount: d.minOrderAmount,
                        unlimitedUsage: d.usageLimit == null,
                        usageLimit: d.usageLimit,
                        startAtLocal: this.toLocalInput(d.startAt),
                        endAtLocal: this.toLocalInput(d.endAt),
                        active: d.active,
                    });
                    this.tiers = (d.tiers || []).map(t => ({
                        minOrderAmount: t.minOrderAmount,
                        type: t.type,
                        value: t.value,
                        maxDiscountAmount: t.maxDiscountAmount ?? null,
                    }));
                    this.loadShow(d.accessoryShowId);
                },
                error: error => {
                    this.toastr.show(getErrorMessageFromResponse(error), 'Không tải được khuyến mãi', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                    });
                    this.router.navigateByUrl('/portal/accessory/promotions');
                },
            });
    }

    private toTierModel(row: TierRow): AccessoryDiscountTierModel {
        return {
            minOrderAmount: Number(row.minOrderAmount) || 0,
            type: row.type,
            value: Number(row.value) || 0,
            maxDiscountAmount: this.normalizeNullableNumber(row.maxDiscountAmount),
        };
    }

    private normalizeNullableNumber(v: unknown): number | null {
        if (v === null || v === undefined || v === '') return null;
        const n = Number(v);
        return Number.isFinite(n) ? n : null;
    }

    /** Formats epoch millis as Asia/Ho_Chi_Minh for datetime-local. */
    private toLocalInput(ms: number | null | undefined): string {
        if (!ms) return '';
        const parts = new Intl.DateTimeFormat('en-GB', {
            timeZone: 'Asia/Ho_Chi_Minh',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false,
        }).formatToParts(new Date(ms));
        const get = (type: string) => parts.find(p => p.type === type)?.value ?? '';
        return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
    }

    /** Parses datetime-local as Vietnam wall-clock (+07) into epoch millis. */
    private fromLocalInput(value: string | null | undefined): number | null {
        if (!value) return null;
        const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value.trim());
        if (!m) {
            const ms = new Date(value).getTime();
            return Number.isFinite(ms) ? ms : null;
        }
        const iso = `${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:00+07:00`;
        const ms = Date.parse(iso);
        return Number.isFinite(ms) ? ms : null;
    }
}
