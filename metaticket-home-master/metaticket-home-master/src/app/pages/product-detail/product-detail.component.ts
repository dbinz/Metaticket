import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, Inject, OnDestroy, OnInit, PLATFORM_ID } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, combineLatest, map, Observable } from 'rxjs';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import {
  ButtonComponent,
  ImageComponent,
  LayoutComponent,
  LoadingComponent,
  NavbarComponent,
} from 'src/app/lib/components';
import { routeName } from 'src/app/lib/constants';
import { AccessoryResponse, AccessoryShowResponse } from 'src/app/lib/entities';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { AccessoryService, AccessoryPendingOrderPromptService } from 'src/app/lib/services';
import { AccessoryCartQuery, AccessoryCartService } from 'src/app/lib/stores';
import { appImages } from 'src/app/lib/theme';
import { AccessoryCartComponent } from 'src/app/pages/accessory-checkout/components';
import { AccessoryPendingOrderModalComponent } from '../product-list/components/accessory-pending-order-modal/accessory-pending-order-modal.component';
import { ProductModalComponent } from './components/product-modal/product-modal.component';

/**
 * Per-show products page ("Sản phẩm" of one accessory show) reached from the
 * accessory-show overview by selecting a show (`san-pham/:slug` or legacy encoded id). It fetches
 * that single open show together with its active accessories, then renders the
 * products as a grid. Selecting a product opens a popup (variant selector +
 * "Add to cart"); a cart popup lets the customer review lines and preview a
 * discount before moving on to checkout (information → fulfillment → payment).
 * Session/fulfillment choice happens on the fulfillment checkout step, not here.
 *
 * If the detail cannot be retrieved, an error state is shown (Req 2.5).
 */
@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    CommonModule,
    LayoutComponent,
    NavbarComponent,
    ImageComponent,
    LoadingComponent,
    ButtonComponent,
    NzTypographyComponent,
    FormatPricePipe,
    ProductModalComponent,
    AccessoryCartComponent,
    AccessoryPendingOrderModalComponent,
  ],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss',
})
export class ProductDetailComponent implements OnInit, OnDestroy {
  /** Canonical slug path segment (`a-z0-9` + hyphens); otherwise treat as PublicId. */
  private static readonly slugFormat = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

  show?: AccessoryShowResponse;
  isLoading = false;
  error = false;

  /** The product shown in the picker popup (null = closed). */
  selectedProduct: AccessoryResponse | null = null;
  /** Whether the cart review/discount popup is open. */
  isCartModalOpen = false;

  /** Units in the cart for the currently viewed show only (0 when cart is for another show). */
  cartCount$: Observable<number>;

  /** Remaining time until saleOpenAt (Waiting shows). */
  saleRemain = { days: 0, hours: 0, minutes: 0, seconds: 0 };

  private readonly _isBrowser: boolean;
  private readonly _showId$ = new BehaviorSubject<string | undefined>(undefined);
  private _saleTimer?: ReturnType<typeof setInterval>;
  private _saleDueHandled = false;

  constructor(
    private _route: ActivatedRoute,
    private _router: Router,
    private _accessoryService: AccessoryService,
    private _pendingPrompt: AccessoryPendingOrderPromptService,
    private _cartService: AccessoryCartService,
    private _cartQuery: AccessoryCartQuery,
    @Inject(PLATFORM_ID) platformId: object,
  ) {
    this._isBrowser = isPlatformBrowser(platformId);
    // Badge only counts the cart that belongs to this page's show — other-show carts stay hidden.
    this.cartCount$ = combineLatest([
      this._cartQuery.totalQuantity$,
      this._cartQuery.accessoryShowId$,
      this._showId$,
    ]).pipe(map(([qty, cartShowId, showId]) => (showId && cartShowId && cartShowId === showId ? qty : 0)));
  }

  ngOnInit(): void {
    if (!this._isBrowser) {
      return;
    }
    // Keep any previously added items visible in the cart badge across reloads.
    this._cartService.restore();
    this._route.params.subscribe((params) => {
      const pathKey = params['showCode'] as string;
      this.fetchShow(pathKey);
    });
  }

  ngOnDestroy(): void {
    this._clearSaleTimer();
  }

  /**
   * Load show detail: Canonical_Slug path → fetch/slug; otherwise PublicId → fetch?id=.
   * When opened by id and the show has a slug, rewrite URL to the readable form.
   */
  fetchShow(pathKey: string): void {
    this.isLoading = true;
    this.error = false;
    this.show = undefined;
    this._showId$.next(undefined);
    const isSlugPath = ProductDetailComponent.slugFormat.test(pathKey || '');
    const request$ = isSlugPath
      ? this._accessoryService.getAccessoryShowBySlug(pathKey)
      : this._accessoryService.getAccessoryShow(pathKey);
    request$.subscribe({
      next: (res) => {
        this.show = res;
        this._showId$.next(res.id);
        this.isLoading = false;
        this._afterShowLoaded(res, pathKey);
      },
      error: (err) => {
        // Req 2.5: detail unavailable -> error state, no partial render.
        this.show = undefined;
        this._showId$.next(undefined);
        this.error = true;
        this.isLoading = false;
        console.log('fetch accessory show error', err);
      },
    });
  }

  get accessories(): AccessoryResponse[] {
    return this.show?.accessories ?? [];
  }

  /** True only while the show is Open — product modal, cart, and checkout stay locked otherwise. */
  get canPurchase(): boolean {
    return (this.show?.status || '').trim() === 'Open';
  }

  get isWaiting(): boolean {
    return (this.show?.status || '').trim() === 'Waiting';
  }

  get isClosed(): boolean {
    return (this.show?.status || '').trim() === 'Closed';
  }

  get hasSaleCountdown(): boolean {
    return this.isWaiting && (this.show?.saleOpenAt || 0) > 0;
  }

  /** Human-readable sale-open instant for the Waiting banner. */
  get saleOpenAtLabel(): string {
    const at = this.show?.saleOpenAt;
    if (!at) {
      return '';
    }
    return new Date(at).toLocaleString('vi-VN', {
      weekday: 'short',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  /** Short display names for the show's allowed payment methods (backend PaymentType names). */
  private readonly _paymentLabels = new Map<string, string>([
    ['QrCode', 'QR/Chuyển khoản/Ví điện tử'],
    ['VietQr', 'VietQR'],
    ['DomesticCard', 'Thẻ ATM nội địa'],
    ['InternationalCard', 'Thẻ quốc tế'],
  ]);

  /** Up to 3 product images shown as icons in the show-info banner. */
  get bannerThumbs(): AccessoryResponse[] {
    return this.accessories.slice(0, 6);
  }

  /** Generic pickup copy for banner/order info; session is chosen later at fulfillment checkout. */
  get pickupLabel(): string {
    const fulfillment = this.show?.fulfillment;
    let pickupLabel = '';
    if (fulfillment?.enablePickupAtShow) {
      pickupLabel += 'Pickup tại show';
    }

    if (fulfillment?.enableMetaTicketPickup) {
      pickupLabel += (pickupLabel ? ' · ' : '') + 'Pickup tại META Ticket';
    }

    if (fulfillment?.enableDelivery) {
      pickupLabel += (pickupLabel ? ' · ' : '') + 'Giao hàng tận nơi';
    }

    return pickupLabel || '';
  }

  get subtitleLine(): string {
    return `${this.show?.description}`;
  }

  /** "Trả trước VietQR / Thẻ quốc tế / ..." from the show's allowed methods. */
  get paymentLabel(): string {
    const methods = this.show?.allowedPaymentMethods ?? [];
    const labels = methods.map((m) => this._paymentLabels.get(m) ?? m);
    return labels.length > 0 ? `Trả trước ${labels.join(' / ')}` : 'Trả trước';
  }

  /** Open the product picker popup for the chosen product (Req 2.1–2.7). */
  onOpenProduct(product: AccessoryResponse): void {
    if (!this.canPurchase) {
      return;
    }
    this.selectedProduct = product;
  }

  onCloseProductModal(): void {
    this.selectedProduct = null;
  }

  /** Open the cart review/discount popup (from the page or the product popup). */
  onOpenCart(): void {
    if (!this.canPurchase) {
      return;
    }
    this.onCloseProductModal();
    this.isCartModalOpen = true;
    // Refresh the show's catalog (incl. live per-accessory/per-variant stock) so the cart validates
    // against current availability rather than the snapshot taken when the page first loaded.
    this._refreshShowStock();
  }

  /**
   * Silently re-fetch the current show (with its active accessories + live stock) and swap it into
   * [show] so the cart popup's stock validation is up to date. Runs in the background: it does not
   * toggle the page loading/error state, and a failed refresh keeps the existing data (the backend
   * still re-checks stock at checkout).
   */
  private _refreshShowStock(): void {
    const showId = this.show?.id;
    if (!showId) {
      return;
    }
    this._accessoryService.getAccessoryShow(showId).subscribe({
      next: (res) => {
        this.show = res;
        this._showId$.next(res.id);
        this._afterShowLoaded(res, undefined, false);
      },
      error: (err) => {
        console.log('refresh accessory show stock error', err);
      },
    });
  }

  onCloseCartModal(): void {
    this.isCartModalOpen = false;
  }

  /** After reviewing the cart + discount, move to the information step (a page). */
  onCartContinue(): void {
    this.isCartModalOpen = false;
    this._router.navigateByUrl(`/${routeName.productCheckout}`).then(() => {
      setTimeout(() => window.scrollTo(0, 0), 100);
    });
  }

  onBackToList(): void {
    this._router.navigate(['/', routeName.product]).then(() => {
      setTimeout(() => window.scrollTo(0, 0), 100);
    });
  }

  productImage(product: AccessoryResponse): string {
    return product.imageUrl || appImages.imgMetaTicketThumbnail;
  }

  trackByProductId(_index: number, product: AccessoryResponse): string {
    return product.id;
  }

  /** After a show payload lands: lock purchase UI, countdown, canonical URL, optional reopen-cart. */
  private _afterShowLoaded(res: AccessoryShowResponse, pathKey?: string, promptPending = true): void {
    this._syncSaleCountdown(!!pathKey);
    if (!this.canPurchase) {
      this.selectedProduct = null;
      this.isCartModalOpen = false;
    } else if (promptPending) {
      this._pendingPrompt.checkOnProductPage();
      const openCart = this._route.snapshot.queryParamMap.get('openCart');
      if (openCart && this._cartQuery.getValue().lines.length > 0) {
        this.isCartModalOpen = true;
      }
    }
    if (!pathKey) {
      return;
    }
    const canonical = (res.slug || '').trim();
    if (canonical && pathKey !== canonical) {
      void this._router.navigate(['/', routeName.productDetail, canonical], {
        replaceUrl: true,
        queryParamsHandling: 'preserve',
      });
    }
  }

  /** Tick D-H-M-S until saleOpenAt; refetch once the deadline passes in case the sweep opened the show. */
  private _syncSaleCountdown(resetDue: boolean): void {
    this._clearSaleTimer();
    if (resetDue) {
      this._saleDueHandled = false;
    }
    if (!this.hasSaleCountdown) {
      return;
    }
    this._tickSaleCountdown();
    if (this._saleDueHandled) {
      return;
    }
    this._saleTimer = setInterval(() => this._tickSaleCountdown(), 1000);
  }

  private _tickSaleCountdown(): void {
    const at = this.show?.saleOpenAt || 0;
    const ms = at - Date.now();
    if (ms <= 0) {
      this.saleRemain = { days: 0, hours: 0, minutes: 0, seconds: 0 };
      if (!this._saleDueHandled) {
        this._saleDueHandled = true;
        this._clearSaleTimer();
        this._refreshShowStock();
      }
      return;
    }
    const totalSeconds = Math.floor(ms / 1000);
    this.saleRemain = {
      days: Math.floor(totalSeconds / 86400),
      hours: Math.floor((totalSeconds % 86400) / 3600),
      minutes: Math.floor((totalSeconds % 3600) / 60),
      seconds: totalSeconds % 60,
    };
  }

  private _clearSaleTimer(): void {
    if (this._saleTimer) {
      clearInterval(this._saleTimer);
      this._saleTimer = undefined;
    }
  }

  /** Two-digit pad for countdown cells. */
  pad2(value: number): string {
    return value < 10 ? `0${value}` : String(value);
  }
}
