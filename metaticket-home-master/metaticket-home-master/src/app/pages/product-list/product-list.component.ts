import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { ImageComponent, LayoutComponent, LoadingComponent, NavbarComponent } from 'src/app/lib/components';
import { ButtonComponent } from 'src/app/lib/components/button/button.component';
import { routeName } from 'src/app/lib/constants';
import { AccessoryShowListPage, AccessoryShowResponse } from 'src/app/lib/entities';
import { AccessoryService, AccessoryPendingOrderPromptService } from 'src/app/lib/services';
import { MinPriceLabelPipe } from 'src/app/lib/pipes';
import { appImages } from 'src/app/lib/theme';
import { AccessoryShowSliderComponent } from './components/accessory-show-slider/accessory-show-slider.component';
import { AccessoryPendingOrderModalComponent } from './components/accessory-pending-order-modal/accessory-pending-order-modal.component';

/** Page size sent as `size` on `fetch/all`; change here (or the query) to load more per click. */
const LIST_PAGE_SIZE = 6;

/**
 * Accessory-show overview ("Sản phẩm"): pinned banners in pin order, then a paginated grid
 * (pins first, then Waiting / Open / Closed — ranking is server-side).
 */
@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [
    CommonModule,
    LayoutComponent,
    NavbarComponent,
    ImageComponent,
    LoadingComponent,
    ButtonComponent,
    NzTypographyComponent,
    AccessoryShowSliderComponent,
    AccessoryPendingOrderModalComponent,
    MinPriceLabelPipe,
  ],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.scss',
})
export class ProductListComponent implements OnInit {
  readonly pageSize = LIST_PAGE_SIZE;

  bannerShows: AccessoryShowResponse[] = [];
  shows: AccessoryShowResponse[] = [];
  isLoading = false;
  loadingMore = false;
  last = true;
  error = false;
  /** True only after a browser fetch settles — prevents SSR empty-state sticking next to the real list. */
  dataLoaded = false;

  private _page = 0;
  private readonly _isBrowser: boolean;

  constructor(
    private _accessoryService: AccessoryService,
    private _pendingPrompt: AccessoryPendingOrderPromptService,
    private _router: Router,
    @Inject(PLATFORM_ID) platformId: object,
  ) {
    this._isBrowser = isPlatformBrowser(platformId);
    if (this._isBrowser) {
      this.isLoading = true;
    }
  }

  ngOnInit(): void {
    if (this._isBrowser) {
      this.fetchShows();
      this._pendingPrompt.checkOnProductPage();
    }
  }

  /** First page of the grid plus pinned banner slides. */
  fetchShows(): void {
    this.isLoading = true;
    this.error = false;
    this.dataLoaded = false;
    this._page = 0;
    forkJoin({
      banner: this._accessoryService.getAccessoryShowBanner().pipe(catchError(() => of([] as AccessoryShowResponse[]))),
      page: this._accessoryService.getAccessoryShows(0, this.pageSize),
    }).subscribe({
      next: ({ banner, page }) => {
        this.bannerShows = banner ?? [];
        this._applyPage(page, false);
        this.isLoading = false;
        this.dataLoaded = true;
      },
      error: (err) => {
        this.bannerShows = [];
        this.shows = [];
        this.last = true;
        this.error = true;
        this.isLoading = false;
        this.dataLoaded = true;
        console.log('fetch accessory shows error', err);
      },
    });
  }

  /** Append the next `size` shows from `fetch/all`. */
  loadMore(): void {
    if (this.loadingMore || this.last) {
      return;
    }
    this.loadingMore = true;
    this._page += 1;
    this._accessoryService.getAccessoryShows(this._page, this.pageSize).subscribe({
      next: (page) => {
        this._applyPage(page, true);
        this.loadingMore = false;
      },
      error: () => {
        this._page -= 1;
        this.loadingMore = false;
      },
    });
  }

  get hasShows(): boolean {
    return this.shows.length > 0;
  }

  get hasBanner(): boolean {
    return this.bannerShows.length > 0;
  }

  /** Empty copy only after a successful/error settlement with zero shows. */
  get showEmpty(): boolean {
    return this.dataLoaded && !this.isLoading && !this.error && !this.hasShows;
  }

  /** Grid when fetch finished with at least one show. */
  get showList(): boolean {
    return this.dataLoaded && !this.isLoading && !this.error && this.hasShows;
  }

  onOpenShow(show: AccessoryShowResponse): void {
    const pathKey = (show.slug || '').trim() || show.id;
    this._router.navigate(['/', routeName.productDetail, pathKey]).then(() => {
      setTimeout(() => window.scrollTo(0, 0), 100);
    });
  }

  showImage(show: AccessoryShowResponse): string {
    return show.bannerImageUrl || appImages.imgMetaTicketThumbnail;
  }

  /** Grid badge copy: Waiting / Open / Closed. Empty when status is unknown. */
  statusLabel(show: AccessoryShowResponse): string {
    switch ((show.status || '').trim()) {
      case 'Waiting':
        return 'Sắp mở bán';
      case 'Open':
        return 'Đang bán';
      case 'Closed':
        return 'Đã đóng';
      default:
        return '';
    }
  }

  /** Color modifier matching homepage merch chips. */
  statusChipClass(show: AccessoryShowResponse): string {
    switch ((show.status || '').trim()) {
      case 'Waiting':
        return 'pl-chip--waiting';
      case 'Closed':
        return 'pl-chip--closed';
      default:
        return 'pl-chip--open';
    }
  }

  trackByShowId(_index: number, show: AccessoryShowResponse): string {
    return show.id;
  }

  /** Merge a listing page onto the grid and refresh `last`. */
  private _applyPage(page: AccessoryShowListPage, append: boolean): void {
    const mapped = page.content ?? [];
    this.shows = append ? [...this.shows, ...mapped] : mapped;
    this.last = page.last === true || (page.totalPages ?? 1) - 1 <= this._page || mapped.length === 0;
  }
}
