import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Router } from '@angular/router';
import { fromEvent, merge, of, Subscription, timer } from 'rxjs';
import { catchError, filter, switchMap } from 'rxjs/operators';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { ImageComponent } from 'src/app/lib/components';
import { routeName } from 'src/app/lib/constants';
import { AccessoryResponse, AccessoryShowResponse, AccessoryVariantResponse } from 'src/app/lib/entities';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { AccessoryService, AuthService, NotificationService } from 'src/app/lib/services';
import {
  ACCESSORY_CART_MAX_QUANTITY,
  AccessoryCartQuery,
  AccessoryCartService,
  AccessoryCheckoutService,
} from 'src/app/lib/stores';
import { appImages } from 'src/app/lib/theme';

/** Sentinel from the backend: a stock cell holding -1 is unlimited (never blocks). */
const UNLIMITED_STOCK = -1;

/** How often the popup refreshes live stock from the server while open and visible (ms). */
const STOCK_POLL_INTERVAL_MS = 20_000;

/**
 * Product picker popup shown from the per-show products page instead of
 * navigating to a dedicated detail page. Renders a single product with:
 *  - a variant selector for variant products (Req 2.1); "Thêm vào giỏ" stays
 *    disabled until a variant is chosen (Req 2.3/2.4) and records the line WITH
 *    the selected `variantKey` (Req 2.6);
 *  - no selector for single-SKU products (Req 2.2); the line is recorded WITHOUT
 *    a `variantKey` (Req 2.7).
 *
 * Live stock: on open (and on a short poll while open) the popup refreshes this
 * product's stock from the server so availability is accurate at the moment of
 * purchase. Sold-out products / variants cannot be added, and the quantity
 * stepper is capped at the remaining stock, taking into account what is already
 * in the cart for the same accessory/variant.
 *
 * The cart itself lives in `AccessoryCartService`; this popup only records lines
 * and lets the customer jump to the cart popup via `viewCart`.
 */
@Component({
  selector: 'app-product-modal',
  standalone: true,
  imports: [CommonModule, ImageComponent, NzTypographyComponent, FormatPricePipe],
  templateUrl: './product-modal.component.html',
})
export class ProductModalComponent implements OnInit, OnDestroy {
  @Input({ required: true }) show!: AccessoryShowResponse;
  @Input({ required: true }) product!: AccessoryResponse;
  /** Pickup label from the parent (e.g. "Pickup tại show 11/07 (HCM)"), reused from the show banner. */
  @Input() pickupLabel = 'Pickup tại show';

  @Output() closed = new EventEmitter<void>();
  @Output() viewCart = new EventEmitter<void>();

  protected readonly appImages = appImages;

  /**
   * The live product used for rendering. Starts as the snapshot passed in by the parent, then is
   * replaced by fresh server data on open and on each poll tick so stock stays accurate.
   */
  liveProduct!: AccessoryResponse;

  /** Label-only selection when variants have no attribute dimensions. */
  selectedVariantKey?: string;
  /** Selected value per attribute key (`attr1` / `attr2`). */
  selectedOptions: Record<string, string> = {};
  quantity = 1;
  /** Index into [images] currently shown as the large preview. */
  selectedImageIndex = 0;

  private _pollSub?: Subscription;

  constructor(
    private _cartService: AccessoryCartService,
    private _cartQuery: AccessoryCartQuery,
    private _checkoutService: AccessoryCheckoutService,
    private _accessoryService: AccessoryService,
    private _notification: NotificationService,
    private _authService: AuthService,
    private _router: Router,
  ) {}

  ngOnInit(): void {
    this.liveProduct = this.product;
    // Refresh live stock immediately, then poll while the popup stays open. To keep server load low
    // under many concurrent users, polling is gated by the Page Visibility API: ticks that fire
    // while the tab is hidden are skipped, and returning to the tab triggers an immediate refresh.
    // A failed refresh keeps the last known data (the popup never breaks on a poll error).
    const becameVisible$ = fromEvent(document, 'visibilitychange').pipe(filter(() => !document.hidden));
    this._pollSub = merge(timer(0, STOCK_POLL_INTERVAL_MS), becameVisible$)
      .pipe(
        filter(() => !document.hidden), // don't poll a backgrounded tab
        switchMap(() => this._accessoryService.getAccessory(this.product.id).pipe(catchError(() => of(null)))),
      )
      .subscribe((fresh) => {
        if (fresh) {
          this.liveProduct = fresh;
          // If the current selection is now over the remaining stock, clamp it down.
          this._clampQuantity();
        }
      });
  }

  ngOnDestroy(): void {
    this._pollSub?.unsubscribe();
  }

  get variants(): AccessoryVariantResponse[] {
    return this.liveProduct.variants ?? [];
  }

  get hasVariants(): boolean {
    return this.variants.length > 0;
  }

  /**
   * Named attribute dimensions (max 2) from API `attributes`.
   */
  get attributeDefs(): { key: string; name: string }[] {
    return (this.liveProduct.attributes ?? []).slice(0, 2);
  }

  /** Variants expose structured attributes → render independent selectors. */
  get usesAttributes(): boolean {
    return this.attributeDefs.length > 0;
  }

  /** Distinct option values for one attribute key (order preserved). */
  optionValues(attrKey: string): string[] {
    return this._distinct(this.variants.map((v) => this._optionValue(v, attrKey)));
  }

  /** The variant matching the current selection, if any. */
  get resolvedVariant(): AccessoryVariantResponse | undefined {
    if (!this.hasVariants) {
      return undefined;
    }
    if (this.usesAttributes) {
      return this.variants.find((v) =>
        this.attributeDefs.every((def) => {
          const selected = this.selectedOptions[def.key];
          return !!selected && this._optionValue(v, def.key) === selected;
        }),
      );
    }
    return this.variants.find((v) => v.key === this.selectedVariantKey);
  }

  /** Reads one option value from the keyed `options` map. */
  private _optionValue(variant: AccessoryVariantResponse, attrKey: string): string | undefined {
    return variant.options?.[attrKey]?.trim() || undefined;
  }

  private _distinct(values: (string | undefined)[]): string[] {
    const seen: string[] = [];
    for (const value of values) {
      if (value && !seen.includes(value)) {
        seen.push(value);
      }
    }
    return seen;
  }

  // ---- Stock / availability -----------------------------------------------------------------

  /** How many units of the given cell (variant key, or the single-SKU product) are already in the cart. */
  private _inCart(variantKey?: string): number {
    return this._cartQuery
      .getValue()
      .lines.filter((l) => l.accessoryId === this.product.id && (l.variantKey ?? '') === (variantKey ?? ''))
      .reduce((sum, l) => sum + l.quantity, 0);
  }

  private _isUnlimited(stock: number): boolean {
    return stock < 0 || stock === UNLIMITED_STOCK;
  }

  /**
   * Units still addable for a cell, i.e. the catalog stock minus what the customer already put in
   * the cart. `Infinity` for an unlimited cell. Used to gate add-to-cart and cap the stepper.
   */
  private _remaining(stock: number, variantKey?: string): number {
    if (this._isUnlimited(stock)) {
      return Number.POSITIVE_INFINITY;
    }
    return Math.max(0, stock - this._inCart(variantKey));
  }

  /** Remaining units for a specific variant (cart-aware). */
  variantRemaining(variant: AccessoryVariantResponse): number {
    return this._remaining(variant.stock, variant.key);
  }

  /** A specific variant is active (available for sale). Absent flag ⇒ active (backward compatible). */
  isVariantActive(variant: AccessoryVariantResponse): boolean {
    return variant.active !== false;
  }

  /** A specific variant has no addable units left (stock only). */
  isVariantSoldOut(variant: AccessoryVariantResponse): boolean {
    return this.variantRemaining(variant) <= 0;
  }

  /**
   * A variant cannot be purchased right now — either it is inactive (withdrawn from sale) or has no
   * addable stock. Drives selector disabling and the add-to-cart gate.
   */
  isVariantUnavailable(variant: AccessoryVariantResponse): boolean {
    return !this.isVariantActive(variant) || this.isVariantSoldOut(variant);
  }

  /** All variants carrying the given attribute value are unavailable. */
  isOptionUnavailable(attrKey: string, value: string): boolean {
    const matching = this.variants.filter((v) => this._optionValue(v, attrKey) === value);
    return matching.length > 0 && matching.every((v) => this.isVariantUnavailable(v));
  }

  isOptionSelected(attrKey: string, value: string): boolean {
    return this.selectedOptions[attrKey] === value;
  }

  onSelectOption(attrKey: string, value: string): void {
    if (this.isOptionUnavailable(attrKey, value)) {
      return;
    }
    this.selectedOptions = { ...this.selectedOptions, [attrKey]: value };
    this._syncPreviewToResolvedVariant();
    this._clampQuantity();
  }

  isSelectedVariant(variant: AccessoryVariantResponse): boolean {
    return this.selectedVariantKey === variant.key;
  }

  onSelectVariant(variant: AccessoryVariantResponse): void {
    if (this.isVariantUnavailable(variant)) {
      return;
    }
    this.selectedVariantKey = variant.key;
    this._syncPreviewToVariant(variant);
    this._clampQuantity();
  }

  /**
   * Remaining units for the currently-selected cell: the single-SKU product, or the resolved
   * variant. `null` when a variant product has no complete selection yet (nothing to show/limit),
   * `Infinity` for an unlimited cell.
   */
  get resolvedRemaining(): number | null {
    if (!this.hasVariants) {
      return this._remaining(this.liveProduct.stock);
    }
    const variant = this.resolvedVariant;
    return variant ? this.variantRemaining(variant) : null;
  }

  /** Whether the currently-selected cell is sold out on stock (single-SKU, or the resolved variant). */
  get isResolvedSoldOut(): boolean {
    const rem = this.resolvedRemaining;
    return rem !== null && rem <= 0;
  }

  /** Whether the resolved variant is inactive (withdrawn from sale). Single-SKU is never "inactive". */
  get isResolvedInactive(): boolean {
    const variant = this.hasVariants ? this.resolvedVariant : undefined;
    return !!variant && !this.isVariantActive(variant);
  }

  /** Whether the currently-selected cell cannot be purchased (inactive or sold out). */
  get isResolvedUnavailable(): boolean {
    if (!this.hasVariants) {
      return this.isResolvedSoldOut;
    }
    const variant = this.resolvedVariant;
    return !!variant && this.isVariantUnavailable(variant);
  }

  /** Whether every purchasable cell of this product is unavailable (whole product unavailable). */
  get isProductUnavailable(): boolean {
    if (!this.hasVariants) {
      return this._remaining(this.liveProduct.stock) <= 0;
    }
    return this.variants.every((v) => this.isVariantUnavailable(v));
  }

  /**
   * Human-readable availability line shown near the quantity stepper. Prioritises the inactive
   * notice over the stock count so a withdrawn combination is never shown as "còn X".
   */
  get stockLabel(): string {
    if (this.isResolvedInactive) {
      return 'Phân loại này hiện không khả dụng';
    }
    const rem = this.resolvedRemaining;
    if (rem === null || rem === Number.POSITIVE_INFINITY) {
      return '';
    }
    return rem <= 0 ? 'Hết hàng' : ``;
  }

  /** Upper bound for the quantity stepper: stock, product max (or soft cap), and per-user remaining. */
  get maxQuantity(): number {
    const maxOrder = this.liveProduct.maxOrderQty;
    const caps = [maxOrder != null && maxOrder >= 1 ? maxOrder : ACCESSORY_CART_MAX_QUANTITY];
    const maxUser = this.liveProduct.maxPerUserQty;
    if (maxUser != null && maxUser >= 1) {
      caps.push(Math.max(0, maxUser - (this.liveProduct.myPurchasedQty ?? 0)));
    }
    const rem = this.resolvedRemaining;
    if (rem !== null && rem !== Number.POSITIVE_INFINITY) {
      caps.push(rem);
    }
    return Math.max(0, Math.min(...caps));
  }

  /** Lower bound for the quantity stepper when this is the first cart add for the product. */
  get minQuantity(): number {
    const min = this.liveProduct.minOrderQty;
    return min != null && min >= 1 ? min : 1;
  }

  /** Clamp the selected quantity into `minQuantity..maxQuantity` (called after selection/stock changes). */
  private _clampQuantity(): void {
    const max = this.maxQuantity;
    const min = Math.min(this.minQuantity, max || this.minQuantity);
    if (max >= 1 && this.quantity > max) {
      this.quantity = max;
    }
    if (this.quantity < min) {
      this.quantity = min;
    }
    if (this.quantity < 1) {
      this.quantity = 1;
    }
  }

  /**
   * Gallery sources: distinct variant images when the product has variants; otherwise product gallery.
   * Selecting a thumbnail only changes the preview — it does not select a variant.
   */
  get images(): string[] {
    if (this.hasVariants) {
      const fromVariants = this._distinct(this.variants.map((v) => v.imageUrl?.trim() || undefined));
      if (fromVariants.length > 0) {
        return fromVariants;
      }
      // Fallback until catalog has variant images populated.
      const gallery = this.liveProduct.imageUrls?.length ? this.liveProduct.imageUrls : [];
      if (gallery.length > 0) {
        return gallery;
      }
      return this.liveProduct.imageUrl ? [this.liveProduct.imageUrl] : [appImages.imgMetaTicketThumbnail];
    }
    const gallery = this.liveProduct.imageUrls?.length ? this.liveProduct.imageUrls : [];
    if (gallery.length > 0) {
      return gallery;
    }
    return this.liveProduct.imageUrl ? [this.liveProduct.imageUrl] : [appImages.imgMetaTicketThumbnail];
  }

  get mainImage(): string {
    return this.images[this.selectedImageIndex] ?? appImages.imgMetaTicketThumbnail;
  }

  selectImage(index: number): void {
    this.selectedImageIndex = index;
  }

  /** After attribute selection resolves to a variant, jump preview to that variant's image. */
  private _syncPreviewToResolvedVariant(): void {
    const variant = this.resolvedVariant;
    if (variant) {
      this._syncPreviewToVariant(variant);
    }
  }

  /** Jump gallery preview to [variant.imageUrl] when present in the gallery list. */
  private _syncPreviewToVariant(variant: AccessoryVariantResponse): void {
    const url = variant.imageUrl?.trim();
    if (!url) {
      return;
    }
    const idx = this.images.indexOf(url);
    if (idx >= 0) {
      this.selectedImageIndex = idx;
    }
  }

  /** Running line total (unit price × quantity) shown on the "Thêm vào giỏ" button. */
  get lineTotal(): number {
    return this.liveProduct.price * this.quantity;
  }

  /**
   * Enabled for single-SKU products; for variant products only once every applicable dimension is
   * chosen and the selection resolves to an existing variant. In all cases the selected cell must
   * still have addable stock (cart-aware) — a sold-out product/variant can never be added.
   */
  get canAddToCart(): boolean {
    if (this.isResolvedUnavailable) {
      return false;
    }
    if (!this.hasVariants) {
      return true;
    }
    if (this.usesAttributes) {
      const isAllSelected = this.attributeDefs.every((def) => !!this.selectedOptions[def.key]);
      return isAllSelected && !!this.resolvedVariant;
    }
    return !!this.selectedVariantKey;
  }

  changeQuantity(delta: number): void {
    const next = this.quantity + delta;
    if (next < 1 || next > this.maxQuantity) {
      return;
    }
    this.quantity = next;
  }

  onAddToCart(): void {
    // "Thêm vào giỏ": stay browsing — add the line, then close and return to the product grid.
    if (this._addSelectionToCart()) {
      this.onClose();
    }
  }

  /** "Mua ngay": add the line, then jump straight to the cart popup instead of closing. */
  onBuyNow(): void {
    if (this._addSelectionToCart()) {
      this.viewCart.emit();
    }
  }

  /**
   * Validates and records the currently selected cell (quantity, and variant if any) into the cart.
   * Shared by "Thêm vào giỏ" and "Mua ngay" — only what happens afterwards differs. Returns true
   * once the line has been recorded.
   */
  private _addSelectionToCart(): boolean {
    if (!this.canAddToCart) {
      return false;
    }

    // Adding to the cart requires a logged-in customer (the cart is per-user and the purchase flow
    // is authenticated). Send anonymous users to login first, returning to this show afterwards.
    if (!this._authService.isAuthenticated()) {
      this._notification.showErrorNotification('Vui lòng đăng nhập để thêm sản phẩm vào giỏ hàng.');
      this._router.navigate(['/', routeName.login], {
        queryParams: { returnUrl: `/${routeName.productDetail}/${this.show.id}` },
      });
      return false;
    }

    const variant = this.hasVariants ? this.resolvedVariant : undefined;
    const variantKey = variant?.key;

    // Final client-side stock guard: never let the cart total for this cell exceed the live stock.
    // (The backend re-checks at checkout — this is the UX pre-check.)
    const rem = this.resolvedRemaining;
    if (rem !== null && rem !== Number.POSITIVE_INFINITY && this.quantity > rem) {
      this._notification.showErrorNotification(
        rem <= 0 ? 'Sản phẩm đã hết hàng.' : `Số lượng sản phẩm không hợp lệ, vui lòng thay đổi số lượng.`,
      );
      return false;
    }

    const result = this._cartService.addLine(
      {
        accessoryId: this.liveProduct.id,
        variantKey, // Req 2.6 (variant) / Req 2.7 (single-SKU: undefined)
        quantity: this.quantity,
        name: this.liveProduct.name,
        variantLabel: variant?.label,
        unitPrice: this.liveProduct.price,
        imageUrl: variant?.imageUrl || this.liveProduct.imageUrl,
        minOrderQty: this.liveProduct.minOrderQty ?? undefined,
        maxOrderQty: this.liveProduct.maxOrderQty ?? undefined,
        maxPerUserQty: this.liveProduct.maxPerUserQty ?? undefined,
        myPurchasedQty: this.liveProduct.myPurchasedQty ?? undefined,
      },
      this.show.id,
    );

    if (!result.ok && result.reason === 'max-quantity') {
      this._notification.showErrorNotification(`Số lượng tối đa mỗi sản phẩm là ${result.max}.`);
      return false;
    }
    if (!result.ok && result.reason === 'min-quantity') {
      this._notification.showErrorNotification(`Sản phẩm này yêu cầu mua tối thiểu ${result.min}.`);
      return false;
    }
    if (!result.ok && result.reason === 'max-user') {
      this._notification.showErrorNotification(
        result.remaining > 0
          ? `Bạn chỉ còn có thể mua thêm ${result.remaining} (tối đa ${result.max} / người).`
          : `Bạn đã đạt giới hạn ${result.max} sản phẩm / người.`,
      );
      return false;
    }

    // Any cart change invalidates a previously previewed discount so it is never
    // applied to a cart it was not validated against.
    this._checkoutService.clearDiscount();

    const label = variant?.label ? `${this.liveProduct.name} · ${variant.label}` : this.liveProduct.name;
    this._notification.showSuccessNotification(`Đã thêm ${label} vào giỏ hàng.`);
    return true;
  }

  onClose(): void {
    this.closed.emit();
  }

  trackByVariantKey(_index: number, variant: AccessoryVariantResponse): string {
    return variant.key;
  }

  trackByAttrKey(_index: number, attr: { key: string }): string {
    return attr.key;
  }

  trackByValue(_index: number, value: string): string {
    return value;
  }

  trackByImage(index: number): number {
    return index;
  }
}
