import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NbGlobalPhysicalPosition, NbToastrService } from '@nebular/theme';
import { tap } from 'rxjs/operators';
import { AccessoryShowService } from '../../@core/metaticket/data.service';
import {
    AccessoryProductModel,
    AccessoryProductStockAdjustInput,
    AccessoryProductUpsertInput,
    AccessoryShowModel,
} from '../../@core/metaticket/model';
import { getErrorMessageFromResponse } from '../../@core/metaticket/utils/error.response.utils';

/** Editable variant row — plain object (no FormGroup) to keep FE light. */
interface VariantRow {
    key: string;
    label: string;
    stock: number;
    attr1?: string;
    attr2?: string;
    /** Required when product has variants. */
    imageUrl?: string;
    active: boolean;
    /** Edit-mode draft for atomic ± adjust (not sent with catalog save). */
    deltaDraft?: number | null;
    /** True if added in this edit session and not yet persisted. */
    isNew?: boolean;
}

/** Soft cap — cartesian attr1×attr2 above this should be blocked, not rendered. */
const MAX_VARIANTS = 200;

@Component({
    selector: 'create-accessory-product',
    templateUrl: './create-accessory-product.component.html',
    styleUrls: ['./create-accessory-product.component.scss'],
})
export class CreateAccessoryProductComponent implements OnInit {
    loading = 0;
    isEdit = false;
    product: AccessoryProductModel | null = null;
    show: AccessoryShowModel | null = null;
    submitPressed = false;

    /**
     * Attribute value list text.
     * Create: full matrix source. Edit: **new values only** (existing shown as locked chips).
     */
    attr1ValuesText = '';
    attr2ValuesText = '';

    /** Edit mode: attr1 values already on the product — display-only, never edited via input. */
    lockedAttr1: string[] = [];
    /** Edit mode: attr2 values already on the product — display-only. */
    lockedAttr2: string[] = [];

    /**
     * Variant rows live outside reactive FormArray — fewer subscriptions / CD nodes.
     * Create: label / stock / active editable.
     * Edit: label / active via catalog save; stock via adjust API (persisted rows) or initial stock (isNew).
     */
    variantRows: VariantRow[] = [];

    /** Keys persisted on the server when the product was loaded (edit mode). */
    private persistedVariantKeys = new Set<string>();

    /** Edit-mode delta draft for single-SKU sellable. */
    singleSkuDeltaDraft: number | null = null;

    form: FormGroup = this.fb.group({
        sku: ['', Validators.required],
        name: ['', Validators.required],
        description: [''],
        imageUrl: [''],
        imageUrlsText: [''],
        price: [0, [Validators.required, Validators.min(1)]],
        currency: ['VND', Validators.required],
        stock: [0, [Validators.min(0)]],
        hasVariants: [false],
        attr1Name: ['Thuộc tính 1'],
        attr2Name: ['Thuộc tính 2'],
        minOrderQty: [null as number | null],
        maxOrderQty: [null as number | null],
        maxPerUserQty: [null as number | null],
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
        return this.isEdit ? 'Sửa sản phẩm' : 'Tạo sản phẩm';
    }

    get hasVariants(): boolean {
        return !!this.form.get('hasVariants')?.value;
    }

    /** Display label for attribute slot 1. */
    get attr1Label(): string {
        return (this.form.get('attr1Name')?.value || '').trim() || 'Thuộc tính 1';
    }

    /** Display label for attribute slot 2. */
    get attr2Label(): string {
        return (this.form.get('attr2Name')?.value || '').trim() || 'Thuộc tính 2';
    }

    /** Stable identity for *ngFor — avoids re-creating DOM for unchanged keys. */
    trackVariant(_index: number, row: VariantRow): string {
        return row.key;
    }

    /** Display helper: -1 = unlimited. */
    formatStock(stock: number): string {
        return stock === -1 ? '∞ (không giới hạn)' : String(stock);
    }

    /** Loads create (showId) or update (id) context. */
    ngOnInit(): void {
        const id = this.route.snapshot.queryParamMap.get('id');
        const showId = this.route.snapshot.queryParamMap.get('showId');
        this.isEdit = !!id;
        if (this.isEdit && id) {
            this.loadProduct(id);
            return;
        }
        if (!showId) {
            this.toastr.show('Thiếu showId', 'Không mở được form tạo', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            this.router.navigateByUrl('/portal/accessory/products');
            return;
        }
        this.loadShow(showId);
    }

    /** Syncs hasVariants checkbox; create mode clears list-driven rows when off. */
    onHasVariantsChange(enabled: boolean): void {
        if (this.isEdit) {
            this.form.patchValue({ hasVariants: this.variantRows.length > 0 });
            return;
        }
        if (!enabled) {
            this.attr1ValuesText = '';
            this.attr2ValuesText = '';
            this.variantRows = [];
        }
    }

    /**
     * Enter in attribute value list → create: full matrix; edit: only append missing combos.
     */
    onVariantListEnter(event: Event): void {
        event.preventDefault();
        event.stopPropagation();
        if (this.isEdit) {
            this.addVariantsFromLists();
        } else {
            this.regenerateVariantsFromLists();
        }
    }

    /** Builds variants from attribute value lists (create mode) into a single array assign. */
    regenerateVariantsFromLists(): void {
        if (this.isEdit) {
            this.addVariantsFromLists();
            return;
        }
        const attr1Values = this.parseList(this.attr1ValuesText);
        const attr2Values = this.parseList(this.attr2ValuesText);
        if (!attr1Values.length && !attr2Values.length) {
            this.toastr.show(`Nhập ít nhất một giá trị cho ${this.attr1Label} hoặc ${this.attr2Label}`, 'Chưa có danh sách', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }

        const estimated =
            attr1Values.length && attr2Values.length
                ? attr1Values.length * attr2Values.length
                : attr1Values.length || attr2Values.length;
        if (estimated > MAX_VARIANTS) {
            this.toastr.show(
                `Tổ hợp quá lớn (${estimated}). Giới hạn ${MAX_VARIANTS} biến thể — rút gọn danh sách.`,
                'Quá nhiều biến thể',
                {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                    duration: 8000,
                },
            );
            return;
        }

        const prev = new Map<string, Pick<VariantRow, 'label' | 'stock' | 'active' | 'imageUrl'>>();
        for (let i = 0; i < this.variantRows.length; i++) {
            const v = this.variantRows[i];
            prev.set(v.key, { label: v.label, stock: v.stock, active: v.active, imageUrl: v.imageUrl });
        }

        const combos = this.buildCombos(attr1Values, attr2Values);
        const next: VariantRow[] = new Array(combos.length);
        for (let i = 0; i < combos.length; i++) {
            const c = combos[i];
            const kept = prev.get(c.key);
            next[i] = {
                key: c.key,
                label: kept?.label || c.label,
                stock: kept?.stock ?? 0,
                attr1: c.attr1,
                attr2: c.attr2,
                imageUrl: kept?.imageUrl || '',
                active: kept?.active !== false,
                deltaDraft: null,
                isNew: false,
            };
        }
        this.variantRows = next;
        this.form.patchValue({ hasVariants: true });
        this.toastr.show(`Đã tạo ${next.length} biến thể`, 'Thành công', {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'success',
            duration: 2000,
        });
    }

    /**
     * Edit mode: input chỉ chứa giá trị mới; merge với locked chips → cartesian, chỉ append key còn thiếu.
     * Không cho sửa/xóa giá trị thuộc tính đã có; bỏ qua trùng lặp với locked.
     */
    addVariantsFromLists(): void {
        if (!this.isEdit) {
            return;
        }
        const typedAttr1 = this.parseList(this.attr1ValuesText);
        const typedAttr2 = this.parseList(this.attr2ValuesText);
        if (!typedAttr1.length && !typedAttr2.length) {
            this.toastr.show(
                `Nhập giá trị ${this.attr1Label} hoặc ${this.attr2Label} mới vào ô trống (ngăn cách , hoặc ;)`,
                'Chưa có giá trị mới',
                {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }

        const lockedAttr1Keys = new Set(this.lockedAttr1.map(s => s.toLowerCase()));
        const lockedAttr2Keys = new Set(this.lockedAttr2.map(c => c.toLowerCase()));
        const newAttr1 = typedAttr1.filter(s => !lockedAttr1Keys.has(s.toLowerCase()));
        const newAttr2 = typedAttr2.filter(c => !lockedAttr2Keys.has(c.toLowerCase()));
        const dupAttr1 = typedAttr1.filter(s => lockedAttr1Keys.has(s.toLowerCase()));
        const dupAttr2 = typedAttr2.filter(c => lockedAttr2Keys.has(c.toLowerCase()));
        if (dupAttr1.length || dupAttr2.length) {
            const bits: string[] = [];
            if (dupAttr1.length) {
                bits.push(`${this.attr1Label}: ${dupAttr1.join(', ')}`);
            }
            if (dupAttr2.length) {
                bits.push(`${this.attr2Label}: ${dupAttr2.join(', ')}`);
            }
            this.toastr.show(`Đã có sẵn — bỏ qua trùng (${bits.join('; ')})`, 'Trùng lặp', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
                duration: 4000,
            });
        }
        if (!newAttr1.length && !newAttr2.length) {
            this.toastr.show('Không còn giá trị mới sau khi loại trùng', 'Biến thể', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'info',
                duration: 3000,
            });
            return;
        }

        // Full matrix from (locked ∪ new) so new attr1 × every existing attr2 (and vice versa).
        const attr1Basis = this.mergeUnique(this.lockedAttr1, newAttr1);
        const attr2Basis = this.mergeUnique(this.lockedAttr2, newAttr2);
        const estimated =
            attr1Basis.length && attr2Basis.length
                ? attr1Basis.length * attr2Basis.length
                : attr1Basis.length || attr2Basis.length;
        if (estimated > MAX_VARIANTS) {
            this.toastr.show(
                `Tổ hợp quá lớn (${estimated}). Giới hạn ${MAX_VARIANTS} biến thể — rút gọn danh sách mới.`,
                'Quá nhiều biến thể',
                {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                    duration: 8000,
                },
            );
            return;
        }

        const combos = this.buildCombos(attr1Basis, attr2Basis);
        const have = new Set(this.variantRows.map(v => v.key));
        const added: VariantRow[] = [];
        for (let i = 0; i < combos.length; i++) {
            const c = combos[i];
            if (have.has(c.key)) {
                continue;
            }
            have.add(c.key);
            added.push({
                key: c.key,
                label: c.label,
                stock: 0,
                attr1: c.attr1,
                attr2: c.attr2,
                imageUrl: '',
                active: true,
                deltaDraft: null,
                isNew: true,
            });
        }

        if (!added.length) {
            this.toastr.show('Không có tổ hợp mới — mọi tổ hợp thuộc tính đã tồn tại', 'Biến thể', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'info',
                duration: 3000,
            });
            return;
        }
        if (this.variantRows.length + added.length > MAX_VARIANTS) {
            this.toastr.show(
                `Thêm ${added.length} sẽ vượt giới hạn ${MAX_VARIANTS} biến thể`,
                'Quá nhiều biến thể',
                {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                    duration: 8000,
                },
            );
            return;
        }
        this.variantRows = this.variantRows.concat(added);
        this.refreshLockedDimensionLists();
        this.attr1ValuesText = '';
        this.attr2ValuesText = '';
        this.toastr.show(`Đã thêm ${added.length} biến thể mới (chưa lưu)`, 'Thành công', {
            position: NbGlobalPhysicalPosition.TOP_RIGHT,
            status: 'success',
            duration: 2500,
        });
    }

    /** Reloads live sellable from server (edit mode). */
    refreshStock(): void {
        if (!this.product) {
            return;
        }
        this.loading += 1;
        this.accessoryShowService
            .getProduct$(this.product.id)
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
                next: product => {
                    this.applyStockFromProduct(product);
                    this.toastr.show('Đã làm mới tồn bán được', 'Tồn kho', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                        duration: 2000,
                    });
                },
                error: error => {
                    this.toastr.show(getErrorMessageFromResponse(error), 'Không làm mới được tồn', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                    });
                },
            });
    }

    /** Applies ±delta to one sellable cell via stock/adjust API. */
    applyStockDelta(variantKey?: string): void {
        if (!this.product) {
            return;
        }
        if (variantKey) {
            const row = this.variantRows.find(v => v.key === variantKey);
            if (row?.isNew) {
                this.toastr.show('Biến thể mới: nhập tồn ban đầu rồi bấm Lưu thông tin', 'Chưa lưu', {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                });
                return;
            }
        }
        let delta: number;
        if (variantKey) {
            const row = this.variantRows.find(v => v.key === variantKey);
            delta = Number(row?.deltaDraft);
        } else {
            delta = Number(this.singleSkuDeltaDraft);
        }
        if (!delta || Number.isNaN(delta)) {
            this.toastr.show('Nhập số ± khác 0 (vd: 10 hoặc -3)', 'Điều chỉnh tồn', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        this.runStockAdjust({ variantKey, delta });
    }

    /** Leaves unlimited and sets an absolute finite sellable. */
    setStockAbsolute(variantKey: string | undefined, value: number): void {
        if (!this.product) {
            return;
        }
        if (value < 0 || Number.isNaN(value)) {
            this.toastr.show('setTo phải >= 0', 'Điều chỉnh tồn', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        this.runStockAdjust({ variantKey, setTo: value });
    }

    /** Submits create/update catalog against private product APIs (stock ignored on update). */
    onSubmit(): void {
        this.submitPressed = true;
        this.form.markAllAsTouched();
        if (!this.show && !this.product) {
            return;
        }
        const hasVariants = !!this.form.get('hasVariants')?.value;
        const primaryImage = (this.form.get('imageUrl')?.value || '').trim();
        if (!primaryImage) {
            this.toastr.show('Nhập URL ảnh chính (thumbnail listing)', 'Form chưa hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        if (hasVariants && this.variantRows.length === 0) {
            this.toastr.show(
                this.isEdit
                    ? 'Sản phẩm chưa có biến thể'
                    : 'Nhập giá trị thuộc tính rồi Enter để tạo biến thể, hoặc tắt “Có biến thể”',
                'Form chưa hợp lệ',
                {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                },
            );
            return;
        }
        if (hasVariants && !this.variantsValid()) {
            this.toastr.show(
                this.isEdit
                    ? 'Mỗi biến thể cần label và URL ảnh'
                    : 'Mỗi biến thể cần label, URL ảnh và tồn kho hợp lệ',
                'Form chưa hợp lệ',
                {
                    position: NbGlobalPhysicalPosition.TOP_RIGHT,
                    status: 'warning',
                },
            );
            return;
        }
        const limitErr = this.purchaseLimitFormError();
        if (limitErr) {
            this.toastr.show(limitErr, 'Form chưa hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }
        if (this.form.invalid) {
            this.toastr.show('Vui lòng kiểm tra lại form', 'Form chưa hợp lệ', {
                position: NbGlobalPhysicalPosition.TOP_RIGHT,
                status: 'warning',
            });
            return;
        }

        const raw = this.form.getRawValue();
        const showId = this.product?.accessoryShowId || this.show!.id;
        // Product gallery only for single-SKU; variant products build gallery from variant images.
        const imageUrls = hasVariants
            ? []
            : String(raw.imageUrlsText || '')
                  .split('\n')
                  .map((s: string) => s.trim())
                  .filter((s: string) => !!s);
        // On edit, stock in payload is ignored server-side; keep placeholders for type shape.
        const input: AccessoryProductUpsertInput = {
            accessoryShowId: showId,
            sku: raw.sku,
            name: raw.name,
            description: (raw.description || '').trim() || undefined,
            imageUrl: primaryImage,
            imageUrls,
            price: Number(raw.price),
            currency: raw.currency || 'VND',
            stock: hasVariants ? 0 : this.isEdit ? this.product?.stock ?? 0 : Math.max(0, Number(raw.stock) || 0),
            active: !!raw.active,
            attr1Name: (raw.attr1Name || '').trim() || undefined,
            attr2Name: (raw.attr2Name || '').trim() || undefined,
            minOrderQty: this.parseOptionalPositiveInt(raw.minOrderQty),
            maxOrderQty: this.parseOptionalPositiveInt(raw.maxOrderQty),
            maxPerUserQty: this.parseOptionalPositiveInt(raw.maxPerUserQty),
            variants: hasVariants
                ? this.variantRows.map(v => ({
                      key: (v.key || '').trim(),
                      label: (v.label || '').trim(),
                      stock: this.isEdit ? v.stock : Math.max(0, Number(v.stock) || 0),
                      attr1: (v.attr1 || '').trim() || undefined,
                      attr2: (v.attr2 || '').trim() || undefined,
                      imageUrl: (v.imageUrl || '').trim() || undefined,
                      active: v.active !== false,
                  }))
                : [],
        };

        this.loading += 1;
        const req$ =
            this.isEdit && this.product
                ? this.accessoryShowService.updateProduct$(this.product.id, input)
                : this.accessoryShowService.createProduct$(input);

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
                next: saved => {
                    this.toastr.show(
                        this.isEdit ? 'Đã lưu thông tin sản phẩm (tồn không đổi)' : 'Đã lưu sản phẩm',
                        'Thành công',
                        {
                            position: NbGlobalPhysicalPosition.TOP_RIGHT,
                            status: 'success',
                        },
                    );
                    this.router.navigateByUrl(
                        '/portal/accessory/products?showId=' + encodeURIComponent(saved.accessoryShowId),
                    );
                },
                error: error => {
                    this.toastr.show(getErrorMessageFromResponse(error), 'Thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 12000,
                    });
                },
            });
    }

    /** Back to product list for the current show. */
    goBack(): void {
        const showId = this.product?.accessoryShowId || this.show?.id;
        this.router.navigateByUrl(
            showId
                ? '/portal/accessory/products?showId=' + encodeURIComponent(showId)
                : '/portal/accessory/products',
        );
    }

    /** Calls stock/adjust then refreshes local sellable display. */
    private runStockAdjust(body: AccessoryProductStockAdjustInput): void {
        if (!this.product) {
            return;
        }
        this.loading += 1;
        this.accessoryShowService
            .adjustProductStock$(this.product.id, body)
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
                next: product => {
                    this.applyStockFromProduct(product);
                    this.toastr.show('Đã cập nhật tồn bán được', 'Tồn kho', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'success',
                        duration: 2500,
                    });
                },
                error: error => {
                    this.toastr.show(getErrorMessageFromResponse(error), 'Điều chỉnh tồn thất bại', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                        duration: 10000,
                    });
                },
            });
    }

    /** Syncs sellable numbers after adjust/refresh without resetting catalog form fields. */
    private applyStockFromProduct(product: AccessoryProductModel): void {
        this.product = product;
        this.persistedVariantKeys = new Set((product.variants || []).map(v => v.key));
        if (product.variants?.length) {
            const byKey = new Map((product.variants || []).map(v => [v.key, v]));
            // Keep local isNew rows not yet on server; refresh stock for persisted keys.
            const stillNew = this.variantRows.filter(r => !!r.isNew && !byKey.has(r.key));
            const refreshed: VariantRow[] = (product.variants || []).map(
                (v): VariantRow => ({
                    key: v.key,
                    label: v.label,
                    stock: v.stock,
                    attr1: v.attr1 || undefined,
                    attr2: v.attr2 || undefined,
                    imageUrl: v.imageUrl || '',
                    active: v.active !== false,
                    deltaDraft: null,
                    isNew: false,
                }),
            );
            this.variantRows = refreshed.concat(stillNew);
            this.refreshLockedDimensionLists();
        } else {
            this.form.patchValue({ stock: product.stock });
            this.singleSkuDeltaDraft = null;
        }
    }

    /** Loads owning show for create mode. */
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
                    this.router.navigateByUrl('/portal/accessory/products');
                },
            });
    }

    /** Loads product + owning show for edit mode. */
    private loadProduct(id: string): void {
        this.loading += 1;
        this.accessoryShowService
            .getProduct$(id)
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
                next: product => {
                    this.product = product;
                    this.patchForm(product);
                    this.loadShow(product.accessoryShowId);
                    this.form.get('sku')?.disable();
                    if (product.variants?.length) {
                        this.form.get('hasVariants')?.disable();
                    }
                },
                error: error => {
                    this.toastr.show(getErrorMessageFromResponse(error), 'Không tải được sản phẩm', {
                        position: NbGlobalPhysicalPosition.TOP_RIGHT,
                        status: 'danger',
                    });
                    this.router.navigateByUrl('/portal/accessory/products');
                },
            });
    }

    /** Fills product fields + plain variantRows from a loaded product. */
    private patchForm(product: AccessoryProductModel): void {
        const hasVariants = !!(product.variants && product.variants.length);
        this.persistedVariantKeys = new Set((product.variants || []).map(v => v.key));
        this.variantRows = (product.variants || []).map(v => ({
            key: v.key,
            label: v.label,
            stock: v.stock,
            attr1: v.attr1 || undefined,
            attr2: v.attr2 || undefined,
            imageUrl: v.imageUrl || '',
            active: v.active !== false,
            deltaDraft: null,
            isNew: false,
        }));
        this.singleSkuDeltaDraft = null;
        // Edit: keep existing dimensions as locked chips; leave inputs empty for new values only.
        this.refreshLockedDimensionLists();
        this.attr1ValuesText = '';
        this.attr2ValuesText = '';
        this.form.patchValue({
            sku: product.sku,
            name: product.name,
            description: product.description || '',
            imageUrl: product.imageUrl || '',
            imageUrlsText: (product.imageUrls || []).join('\n'),
            price: product.price,
            currency: product.currency || 'VND',
            stock: product.stock,
            hasVariants,
            attr1Name: product.attr1Name || product.attributes?.[0]?.name || 'Thuộc tính 1',
            attr2Name: product.attr2Name || product.attributes?.[1]?.name || 'Thuộc tính 2',
            minOrderQty: product.minOrderQty ?? null,
            maxOrderQty: product.maxOrderQty ?? null,
            maxPerUserQty: product.maxPerUserQty ?? null,
            active: product.active,
        });
    }

    /** Parses blank/invalid optional qty fields as `undefined` (no limit). */
    private parseOptionalPositiveInt(value: unknown): number | undefined {
        if (value === null || value === undefined || value === '') {
            return undefined;
        }
        const n = Number(value);
        if (!Number.isFinite(n) || n < 1) {
            return undefined;
        }
        return Math.trunc(n);
    }

    /** Cross-check min/max/per-user fields; empty means unlimited. */
    private purchaseLimitFormError(): string | null {
        const raw = this.form.getRawValue();
        const min = this.parseOptionalPositiveInt(raw.minOrderQty);
        const max = this.parseOptionalPositiveInt(raw.maxOrderQty);
        const perUser = this.parseOptionalPositiveInt(raw.maxPerUserQty);
        const rawMin = raw.minOrderQty;
        const rawMax = raw.maxOrderQty;
        const rawUser = raw.maxPerUserQty;
        if (rawMin !== null && rawMin !== undefined && rawMin !== '' && min === undefined) {
            return 'Mua tối thiểu / 1 đơn phải ≥ 1 hoặc để trống';
        }
        if (rawMax !== null && rawMax !== undefined && rawMax !== '' && max === undefined) {
            return 'Mua tối đa / 1 đơn phải ≥ 1 hoặc để trống';
        }
        if (rawUser !== null && rawUser !== undefined && rawUser !== '' && perUser === undefined) {
            return 'Mua tối đa / user phải ≥ 1 hoặc để trống';
        }
        if (min != null && max != null && min > max) {
            return 'Mua tối thiểu không được lớn hơn mua tối đa / 1 đơn';
        }
        if (max != null && perUser != null && max > perUser) {
            return 'Mua tối đa / 1 đơn không được lớn hơn mua tối đa / user';
        }
        if (min != null && perUser != null && min > perUser) {
            return 'Mua tối thiểu không được lớn hơn mua tối đa / user';
        }
        return null;
    }

    /** Validates variant rows before create/update catalog save. */
    private variantsValid(): boolean {
        for (let i = 0; i < this.variantRows.length; i++) {
            const v = this.variantRows[i];
            if (!(v.key || '').trim() || !(v.label || '').trim() || !(v.imageUrl || '').trim()) {
                return false;
            }
            if (!this.isEdit || v.isNew) {
                const n = Number(v.stock);
                if (v.stock === null || v.stock === undefined || Number.isNaN(n) || n < 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Rebuilds display-only attribute chips from current variant rows (edit mode). */
    private refreshLockedDimensionLists(): void {
        this.lockedAttr1 = Array.from(
            new Set(this.variantRows.map(v => (v.attr1 || '').trim()).filter(Boolean)),
        );
        this.lockedAttr2 = Array.from(
            new Set(this.variantRows.map(v => (v.attr2 || '').trim()).filter(Boolean)),
        );
    }

    /** Concat + case-insensitive de-dupe, preserving first-seen casing/order. */
    private mergeUnique(base: string[], extra: string[]): string[] {
        const seen = new Set<string>();
        const out: string[] = [];
        const pushAll = (items: string[]) => {
            for (let i = 0; i < items.length; i++) {
                const item = (items[i] || '').trim();
                if (!item) {
                    continue;
                }
                const key = item.toLowerCase();
                if (!seen.has(key)) {
                    seen.add(key);
                    out.push(item);
                }
            }
        };
        pushAll(base);
        pushAll(extra);
        return out;
    }

    /** Splits a `,` / `;` list, trims, de-dupes (order preserved). */
    private parseList(text: string): string[] {
        const seen = new Set<string>();
        const out: string[] = [];
        const parts = String(text || '').split(/[,;]/);
        for (let i = 0; i < parts.length; i++) {
            const item = parts[i].trim();
            if (!item) {
                continue;
            }
            const key = item.toLowerCase();
            if (!seen.has(key)) {
                seen.add(key);
                out.push(item);
            }
        }
        return out;
    }

    /** Cartesian (or single-dimension) combinations from attr1/attr2 value lists. */
    private buildCombos(
        values1: string[],
        values2: string[],
    ): Array<{ key: string; label: string; attr1?: string; attr2?: string }> {
        if (values1.length && values2.length) {
            const out: Array<{ key: string; label: string; attr1: string; attr2: string }> = [];
            for (const a1 of values1) {
                for (const a2 of values2) {
                    out.push({
                        key: `${a1}-${a2}`,
                        label: `${a1} · ${a2}`,
                        attr1: a1,
                        attr2: a2,
                    });
                }
            }
            return out;
        }
        if (values1.length) {
            return values1.map(a1 => ({ key: a1, label: a1, attr1: a1 }));
        }
        return values2.map(a2 => ({ key: a2, label: a2, attr2: a2 }));
    }
}
