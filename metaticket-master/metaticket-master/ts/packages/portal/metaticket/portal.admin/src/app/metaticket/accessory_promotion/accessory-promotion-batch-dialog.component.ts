import { Component, Input } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NbDialogRef, NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import {
    AccessoryDiscountTierModel,
    AccessoryDiscountType,
    AccessoryDiscountUpsertInput,
} from '../../@core/metaticket/model';
import { generateDiscountCodes } from '../../@core/metaticket/utils/accessory-discount-excel.utils';

/** Payload returned to the list page when the operator confirms batch create. */
export interface AccessoryPromotionBatchDialogResult {
    inputs: AccessoryDiscountUpsertInput[];
}

/**
 * Dialog: clone one discount config and preview many unique codes.
 * Does not inject module-scoped services — NbDialog runs under AppModule injector.
 * The list page calls `createDiscountsBatch$` after close.
 */
@Component({
    selector: 'accessory-promotion-batch-dialog',
    templateUrl: './accessory-promotion-batch-dialog.component.html',
    styleUrls: ['./accessory-promotion-batch-dialog.component.scss'],
})
export class AccessoryPromotionBatchDialogComponent {
    @Input() showId!: string;

    previewCodes: string[] = [];
    readonly typeOptions: AccessoryDiscountType[] = ['Percentage', 'FixedAmount', 'Tiered', 'EarlyBird'];
    tiers: AccessoryDiscountTierModel[] = [
        { minOrderAmount: 200000, type: 'FixedAmount', value: 20000, maxDiscountAmount: null },
    ];

    form: FormGroup = this.fb.group({
        prefix: ['PROMO-', Validators.required],
        count: [10, [Validators.required, Validators.min(1), Validators.max(200)]],
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
        private toastr: NbToastrService,
        public ref: NbDialogRef<AccessoryPromotionBatchDialogComponent>,
    ) {}

    get type(): AccessoryDiscountType {
        return this.form.get('type')?.value as AccessoryDiscountType;
    }

    get showRewardFields(): boolean {
        return this.type === 'Percentage' || this.type === 'FixedAmount' || this.type === 'EarlyBird';
    }

    /** Sync Tiered default rows when type changes. */
    onTypeChange(): void {
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

    /** Removes one tier. */
    removeTier(index: number): void {
        this.tiers = this.tiers.filter((_, i) => i !== index);
    }

    /** Regenerates preview codes from prefix + count. */
    generatePreview(): void {
        try {
            const prefix = String(this.form.get('prefix')?.value || '');
            const count = Number(this.form.get('count')?.value) || 0;
            this.previewCodes = generateDiscountCodes(prefix, count);
        } catch (e) {
            this.toastr.show((e as Error).message || 'Generate thất bại', 'Không hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
        }
    }

    /** Closes without saving. */
    cancel(): void {
        this.ref.close(null);
    }

    /** Validates form and returns upsert inputs to the caller (no HTTP here). */
    submit(): void {
        if (!this.showId) {
            return;
        }
        if (!this.previewCodes.length) {
            this.generatePreview();
        }
        if (!this.previewCodes.length) {
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
        if (type === 'EarlyBird') {
            const startAt = this.fromLocalInput(this.form.get('startAtLocal')?.value);
            const endAt = this.fromLocalInput(this.form.get('endAtLocal')?.value);
            if (!startAt || !endAt) {
                this.toastr.show('Early Bird cần startAt và endAt', 'Không hợp lệ', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                });
                return;
            }
        }
        if (type === 'Tiered' && this.tiers.length === 0) {
            this.toastr.show('Tiered cần ít nhất 1 mốc', 'Không hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }

        const base: Omit<AccessoryDiscountUpsertInput, 'code'> = {
            accessoryShowId: this.showId,
            type,
            value: Number(this.form.get('value')?.value) || 0,
            rewardType: type === 'EarlyBird' ? this.form.get('rewardType')?.value : null,
            maxDiscountAmount: this.normalizeNullableNumber(this.form.get('maxDiscountAmount')?.value),
            minOrderAmount: Number(this.form.get('minOrderAmount')?.value) || 0,
            tiers: type === 'Tiered' ? this.tiers.map(t => ({ ...t })) : [],
            applyMode: 'Manual',
            usageLimit,
            startAt: this.fromLocalInput(this.form.get('startAtLocal')?.value),
            endAt: this.fromLocalInput(this.form.get('endAtLocal')?.value),
            active: !!this.form.get('active')?.value,
        };
        const inputs: AccessoryDiscountUpsertInput[] = this.previewCodes.map(code => ({
            ...base,
            code,
        }));
        this.ref.close({ inputs } as AccessoryPromotionBatchDialogResult);
    }

    private normalizeNullableNumber(raw: unknown): number | null {
        if (raw === '' || raw == null) {
            return null;
        }
        const n = Number(raw);
        return Number.isFinite(n) ? n : null;
    }

    private fromLocalInput(raw: string): number | null {
        if (!raw) {
            return null;
        }
        const ms = new Date(raw).getTime();
        return Number.isNaN(ms) ? null : ms;
    }
}
