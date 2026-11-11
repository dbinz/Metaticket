import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { NgForOf, NgIf } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable, Subscription, tap } from 'rxjs';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { NzInputModule } from 'ng-zorro-antd/input';
import {
  ButtonComponent,
  ImageComponent,
  PromotionOfferPanelComponent,
  PromotionOfferSource,
} from 'src/app/lib/components';
import { routeName } from 'src/app/lib/constants';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { AuthService, NotificationService, AccessoryService } from 'src/app/lib/services';
import {
  AccessoryCartQuery,
  AccessoryCartService,
  AccessoryCheckoutQuery,
  AccessoryCheckoutService,
  ACCESSORY_CART_MAX_QUANTITY,
  CartLine,
} from 'src/app/lib/stores';
import {
  AccessoryCheckoutLineRequest,
  AccessoryResponse,
  AccessoryShowResponse,
  PromotionOfferResponse,
  ValidateDiscountRequest,
} from 'src/app/lib/entities';
import { appImages } from 'src/app/lib/theme';
import { explainAccessoryRejection, normalizePromotionCode, sanitizePromotionOffers } from 'src/app/lib/utils';

/**
 * Step 1 "Chọn" of the accessory checkout — review/adjust cart lines and preview
 * a discount code, mirroring the seat/policy step of the booking flow.
 *
 * All promotions (Percentage / FixedAmount / Tiered / EarlyBird) require the customer
 * to type a code and press "Áp dụng". Nothing is auto-applied on cart open or quantity change.
 *
 * When the cart belongs to a different show than `[show]`, those lines are hidden here
 * (empty cart for this show) instead of being marked unavailable — the other show's cart
 * stays intact in the store.
 */
@Component({
  selector: 'accessory-cart',
  standalone: true,
  imports: [
    NgIf,
    NgForOf,
    ReactiveFormsModule,
    NzTypographyModule,
    NzDividerComponent,
    NzInputModule,
    ButtonComponent,
    ImageComponent,
    FormatPricePipe,
    PromotionOfferPanelComponent,
  ],
  templateUrl: './accessory-cart.component.html',
})
export class AccessoryCartComponent implements OnInit, OnChanges, OnDestroy {
  protected readonly appImages = appImages;

  /**
   * Emitted when the customer confirms the cart (after reviewing lines and any
   * discount). The host — the cart popup on the product-list page — closes the
   * popup and navigates to the information step. The previewed discount and
   * subtotal are already persisted on the checkout store at this point.
   */
  @Output() continue = new EventEmitter<void>();

  /** Emitted by "+ Chọn thêm sản phẩm" so the host can close the popup and return to the grid. */
  @Output() addMore = new EventEmitter<void>();

  /** Emitted by the header's "X" button so the host can close the popup. */
  @Output() closed = new EventEmitter<void>();

  /**
   * The currently-viewed show with its active accessories (from the product page). Used to validate
   * that every cart line still refers to a real, active catalog product/variant; invalid lines are
   * dimmed and block checkout. When the cart is scoped to a different show, those lines are hidden
   * (empty cart here) instead of marked unavailable. When absent, validation is skipped.
   */
  @Input() show?: AccessoryShowResponse;

  /**
   * Pickup label from the host (e.g. "Pickup tại show 11/07 (HCM)"), reused from the show banner /
   * product popup. Rendered as a "Pre-Order · <pickup>" badge so the cart shows the same fulfillment
   * hint as the product-detail popup.
   */
  @Input() pickupLabel = 'Pickup tại show';

  lines: CartLine[] = [];
  subtotal = 0;

  discountControl = new FormControl<string>('', { nonNullable: true });
  discountApplied = false;
  discountAmount = 0;
  discountType = '';
  discountValue = 0;
  discountLoading = false;
  /** Inline error shown in the popup when a code can't be applied (empty otherwise). */
  discountError = '';

  promotionOfferSource: PromotionOfferSource | null = null;
  private _cachedOffers: PromotionOfferResponse[] = [];
  private _lastSourceShowId: string | null = null;
  /** Bumped by every validate request and every clear, so only the latest request may write the discount state. */
  private _discountRequestSeq = 0;

  private _linesSubscription?: Subscription;

  constructor(
    private _cartQuery: AccessoryCartQuery,
    private _cartService: AccessoryCartService,
    private _checkoutQuery: AccessoryCheckoutQuery,
    private _checkoutService: AccessoryCheckoutService,
    private _accessoryService: AccessoryService,
    private _notification: NotificationService,
    private _authService: AuthService,
    private _router: Router,
  ) {}

  ngOnInit(): void {
    // Restore a discount previewed earlier in this session so reopening the cart
    // popup shows the same applied discount that will be submitted (the store is
    // the source of truth; cart mutations elsewhere already clear it).
    const state = this._checkoutQuery.getValue();
    if (state.discountCode) {
      this.discountControl.setValue(state.discountCode);
      this.discountApplied = true;
      this.discountAmount = state.discountAmount;
      this.discountType = state.discountType;
      this.discountValue = state.discountValue;
    }

    this._updatePromotionSource();

    // `lines$` emits synchronously on subscribe; that initial emission must not
    // wipe the restored discount — only subsequent (real) cart changes should.
    let isFirstEmission = true;
    this._linesSubscription = this._cartQuery.lines$.subscribe((lines) => {
      this.lines = lines;
      this._updatePromotionSource();
      this._syncDisplayedTotals({ clearDiscountOnChange: !isFirstEmission });
      isFirstEmission = false;
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    // `[show]` often arrives after the first cart emission — re-filter without clearing a
    // discount that belongs to another show's cart still held in the store.
    if (changes['show']) {
      this._updatePromotionSource();
      this._syncDisplayedTotals({ clearDiscountOnChange: false });
    }
  }

  private _updatePromotionSource(): void {
    const showId = this._cartQuery.getValue().accessoryShowId || this.show?.id;
    if (!showId) {
      if (this.promotionOfferSource !== null) {
        this.promotionOfferSource = null;
        this._lastSourceShowId = null;
        this._cachedOffers = [];
      }
      return;
    }
    if (this._lastSourceShowId === showId && this.promotionOfferSource !== null) {
      return;
    }
    this._lastSourceShowId = showId;
    this._cachedOffers = [];
    this.promotionOfferSource = (): Observable<PromotionOfferResponse[]> =>
      this._accessoryService.getDiscountOffers(showId).pipe(
        tap((offers) => {
          // Same cleaning as the panel: a null entry would otherwise throw when explaining a rejected code.
          this._cachedOffers = sanitizePromotionOffers(offers);
        }),
      );
  }

  ngOnDestroy(): void {
    this._linesSubscription?.unsubscribe();
  }

  get total(): number {
    return Math.max(0, this.subtotal - this.discountAmount);
  }

  /**
   * Lines to render for the currently viewed show. A cart scoped to another show is hidden
   * here (not listed as unavailable) so this page reads as an empty cart.
   */
  get displayLines(): CartLine[] {
    return this._cartMatchesCurrentShow() ? this.lines : [];
  }

  get isEmpty(): boolean {
    return this.displayLines.length === 0;
  }

  /** Lookup of the show's active accessories by (encoded) id, for cart-line validation. */
  private get _accessoryById(): Map<string, AccessoryResponse> {
    const map = new Map<string, AccessoryResponse>();
    (this.show?.accessories ?? []).forEach((a) => map.set(a.id, a));
    return map;
  }

  /** True when there is no show context, or the persisted cart is for this show. */
  private _cartMatchesCurrentShow(): boolean {
    if (!this.show) {
      return true;
    }
    const cartShowId = this._cartQuery.getValue().accessoryShowId;
    if (!cartShowId) {
      return true;
    }
    return cartShowId === this.show.id;
  }

  /**
   * A cart line is invalid when it no longer maps to a real, active catalog entry: the accessory is
   * missing/inactive, or the selected variant no longer exists. Validation is skipped when no
   * [show] is bound. Other-show lines are excluded via [displayLines], not flagged here.
   */
  isLineInvalid(line: CartLine): boolean {
    if (!this.show) {
      return false;
    }
    const accessory = this._accessoryById.get(line.accessoryId);
    if (!accessory || accessory.active === false) {
      return true;
    }
    if (line.variantKey) {
      // The variant must still exist AND be active; an inactive combination is withdrawn from sale.
      const variant = (accessory.variants ?? []).find((v) => v.key === line.variantKey);
      return !variant || variant.active === false;
    }
    return false;
  }

  /** True when at least one displayed cart line is no longer valid against the catalog. */
  get hasInvalidLines(): boolean {
    return this.displayLines.some((line) => this.isLineInvalid(line));
  }

  /**
   * The current catalog stock for a cart line's cell (the variant's stock for a variant line, else
   * the accessory's top-level stock). Returns `null` when the stock is unknown (no [show] bound or
   * the product/variant is gone) or unlimited (`-1`) — i.e. when there is no finite limit to enforce.
   */
  stockFor(line: CartLine): number | null {
    if (!this.show) {
      return null;
    }
    const accessory = this._accessoryById.get(line.accessoryId);
    if (!accessory) {
      return null;
    }
    const stock = line.variantKey
      ? (accessory.variants ?? []).find((v) => v.key === line.variantKey)?.stock
      : accessory.stock;
    if (stock === undefined || stock === null || stock < 0) {
      return null; // unknown or unlimited → no finite limit
    }
    return stock;
  }

  /** A line whose quantity exceeds the current stock of its cell (variant/accessory). */
  lineExceedsStock(line: CartLine): boolean {
    const stock = this.stockFor(line);
    return stock !== null && line.quantity > stock;
  }

  /** Inline "chỉ còn X" / "hết hàng" hint for an over-stock line (empty when the line is fine). */
  stockHint(line: CartLine): string {
    if (!this.lineExceedsStock(line)) {
      return '';
    }
    const stock = this.stockFor(line) ?? 0;
    return stock <= 0 ? 'Sản phẩm đã hết hàng' : `Chỉ còn ${stock} sản phẩm`;
  }

  /** True when at least one displayed cart line exceeds its available stock. */
  get hasStockIssues(): boolean {
    return this.displayLines.some((line) => this.lineExceedsStock(line));
  }

  /** Checkout is blocked while any line is invalid (gone/inactive) or over its available stock. */
  get blockCheckout(): boolean {
    return this.hasInvalidLines || this.hasStockIssues;
  }

  /** Total units across displayed lines, shown in the header badge ("N sản phẩm"). */
  get totalQuantity(): number {
    return this.displayLines.reduce((sum, line) => sum + line.quantity, 0);
  }

  /** Line subtitle: "Size M · 350.000đ" for variants, or just the unit price for single-SKU. */
  lineSubtitle(line: CartLine): string {
    const price = `${(line.unitPrice ?? 0).toLocaleString('vi-VN')}đ`;
    return line.variantLabel ? `${line.variantLabel} · ${price}` : price;
  }

  /** Totals label suffix, e.g. " (10%)" for a percentage discount; empty otherwise. */
  get discountLabelSuffix(): string {
    return this.discountType === 'Percentage' ? ` (${this.discountValue}%)` : '';
  }

  /** Green banner text after the customer applies a code. */
  get discountBannerText(): string {
    const amountText = `giảm ${this.discountAmount.toLocaleString('vi-VN')}đ`;
    if (this.discountType === 'Tiered') {
      return `Đã áp mã ${this.discountControl.value} — ưu đãi theo đơn (${amountText})`;
    }
    if (this.discountType === 'EarlyBird') {
      return `Đã áp mã ${this.discountControl.value} — Early Bird (${amountText})`;
    }
    if (this.discountType === 'Percentage') {
      return `Đã áp mã ${this.discountControl.value} — giảm ${this.discountValue}%`;
    }
    return `Đã áp mã ${this.discountControl.value} — ${amountText}`;
  }

  lineTotal(line: CartLine): number {
    return (line.unitPrice ?? 0) * line.quantity;
  }

  onIncrease(line: CartLine): void {
    // Never let the cart quantity climb past the available stock for this cell.
    const stock = this.stockFor(line);
    if (stock !== null && line.quantity + 1 > stock) {
      this._notification.showErrorNotification(stock <= 0 ? 'Sản phẩm đã hết hàng.' : `Chỉ còn ${stock} sản phẩm.`);
      return;
    }
    const result = this._cartService.updateQuantity(line.accessoryId, line.variantKey, line.quantity + 1);
    if (!result.ok && result.reason === 'max-quantity') {
      this._notification.showErrorNotification(`Số lượng tối đa mỗi đơn là ${result.max}.`);
      return;
    }
    if (!result.ok && result.reason === 'max-user') {
      this._notification.showErrorNotification(
        result.remaining > 0
          ? `Bạn chỉ còn có thể mua thêm ${result.remaining} (tối đa ${result.max} / người).`
          : `Bạn đã đạt giới hạn ${result.max} sản phẩm / người.`,
      );
      return;
    }
    if (!result.ok && result.reason === 'min-quantity') {
      this._notification.showErrorNotification(`Sản phẩm này yêu cầu mua tối thiểu ${result.min}.`);
    }
  }

  /** Whether the quantity "+" should be disabled for a line (at or above stock / product caps). */
  isIncreaseDisabled(line: CartLine): boolean {
    const stock = this.stockFor(line);
    if (stock !== null && line.quantity >= stock) {
      return true;
    }
    const caps = [line.maxOrderQty != null && line.maxOrderQty >= 1 ? line.maxOrderQty : ACCESSORY_CART_MAX_QUANTITY];
    if (line.maxPerUserQty != null && line.maxPerUserQty >= 1) {
      caps.push(Math.max(0, line.maxPerUserQty - (line.myPurchasedQty ?? 0)));
    }
    const productTotal = this.lines
      .filter((l) => l.accessoryId === line.accessoryId)
      .reduce((sum, l) => sum + l.quantity, 0);
    return productTotal >= Math.min(...caps);
  }

  onDecrease(line: CartLine): void {
    // A resulting quantity below 1 removes the line (Req 3.5).
    const result = this._cartService.updateQuantity(line.accessoryId, line.variantKey, line.quantity - 1);
    if (!result.ok && result.reason === 'min-quantity') {
      this._notification.showErrorNotification(
        `Sản phẩm này yêu cầu mua tối thiểu ${result.min}. Xóa hẳn dòng nếu không mua.`,
      );
    }
  }

  onRemove(line: CartLine): void {
    this._cartService.removeLine(line.accessoryId, line.variantKey);
  }

  onPickPromotion(code: string): void {
    this.discountControl.setValue(code);
    this.onApplyDiscount();
  }

  /** Preview a typed code only after the customer presses Apply. */
  onApplyDiscount(): void {
    const code = this.discountControl.value.trim();
    if (!code) {
      this.discountError = 'Vui lòng nhập mã giảm giá.';
      return;
    }
    if (this.isEmpty) {
      this.discountError = 'Giỏ hàng đang trống.';
      return;
    }

    if (this.discountApplied) {
      this._clearDiscount();
    }

    const body: ValidateDiscountRequest = { discountCode: code, lines: this._toCheckoutLines() };
    this.discountLoading = true;
    this.discountError = '';
    // Picking a second code from the panel before the first answer arrives must not let the first answer win.
    const seq = ++this._discountRequestSeq;
    this._accessoryService.validateDiscount(body).subscribe({
      next: (res) => {
        if (seq !== this._discountRequestSeq) {
          return;
        }
        this.discountApplied = true;
        this.discountError = '';
        this.discountAmount = res.amount;
        this.discountType = res.type;
        this.discountValue = res.value;
        this.subtotal = res.subtotal;
        this.discountControl.setValue(res.code || code);
        this._checkoutService.applyDiscount(res.code, res.amount, res.subtotal, res.total, res.type, res.value);
        this.discountLoading = false;
      },
      error: () => {
        if (seq !== this._discountRequestSeq) {
          return;
        }
        this.discountApplied = false;
        this.discountAmount = 0;
        this._checkoutService.clearDiscount();
        this.discountLoading = false;
        const key = normalizePromotionCode(code);
        const matchedOffer = this._cachedOffers.find((o) => normalizePromotionCode(o.code) === key);
        this.discountError = explainAccessoryRejection(matchedOffer, this.subtotal);
      },
    });
  }

  onRemoveDiscount(): void {
    this.discountControl.setValue('');
    this._clearDiscount();
  }

  onAddMore(): void {
    this.addMore.emit();
  }

  onClose(): void {
    this.closed.emit();
  }

  onContinue(): void {
    if (this.isEmpty) {
      this._notification.showErrorNotification('Giỏ hàng đang trống. Vui lòng thêm sản phẩm.');
      return;
    }
    if (this.hasInvalidLines) {
      this._notification.showErrorNotification(
        'Một số sản phẩm trong giỏ không còn khả dụng. Vui lòng xóa chúng trước khi tiếp tục.',
      );
      return;
    }
    if (this.hasStockIssues) {
      this._notification.showErrorNotification(
        'Một số sản phẩm vượt quá số lượng còn lại. Vui lòng giảm số lượng trước khi tiếp tục.',
      );
      return;
    }
    if (!this._ensureLoggedIn(`/${routeName.productCheckout}`)) {
      return;
    }
    this._checkoutService.setSubtotal(this.subtotal);
    this.continue.emit();
  }

  /**
   * Require an authenticated customer before a purchase action. Returns `true` when logged in;
   * otherwise notifies the customer, redirects to the login page with a `returnUrl`, and returns
   * `false`. [returnUrl] defaults to the current page so the customer comes back where they were.
   */
  private _ensureLoggedIn(returnUrl?: string): boolean {
    if (this._authService.isAuthenticated()) {
      return true;
    }
    this._notification.showErrorNotification('Vui lòng đăng nhập để tiếp tục mua hàng.');
    this._router.navigate(['/', routeName.login], {
      queryParams: { returnUrl: returnUrl ?? this._router.url },
    });
    return false;
  }

  private _toCheckoutLines(): AccessoryCheckoutLineRequest[] {
    return this.displayLines.map((line) => ({
      accessoryId: line.accessoryId,
      variantKey: line.variantKey,
      quantity: line.quantity,
    }));
  }

  /**
   * Refresh UI totals for the current show. When the cart belongs to another show, show empty
   * totals locally without mutating the checkout store (that cart/discount must stay intact).
   */
  private _syncDisplayedTotals(options: { clearDiscountOnChange: boolean }): void {
    if (!this._cartMatchesCurrentShow()) {
      this.subtotal = 0;
      // Hide another show's discount in this UI only — leave checkout store untouched.
      this.discountApplied = false;
      this.discountAmount = 0;
      this.discountType = '';
      this.discountValue = 0;
      this.discountError = '';
      this.discountControl.setValue('');
      return;
    }
    this._recomputeSubtotal();
    if (options.clearDiscountOnChange && (this.discountApplied || this.discountLoading)) {
      this._clearDiscount();
      this.discountControl.setValue('');
      this._notification.showErrorNotification(
        'Mã giảm giá đã được gỡ do giỏ hàng thay đổi. Vui lòng nhập mã và bấm Áp dụng lại.',
      );
    }
  }

  private _recomputeSubtotal(): void {
    this.subtotal = this.displayLines.reduce((sum, line) => sum + this.lineTotal(line), 0);
    this._checkoutService.setSubtotal(this.subtotal);
  }

  private _clearDiscount(): void {
    // A validate still in flight was for the code/cart being cleared: drop its answer and stop its spinner.
    this._discountRequestSeq++;
    this.discountLoading = false;
    this.discountApplied = false;
    this.discountAmount = 0;
    this.discountType = '';
    this.discountValue = 0;
    this.discountError = '';
    this._checkoutService.clearDiscount();
  }
}
