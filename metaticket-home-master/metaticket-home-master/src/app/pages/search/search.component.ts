import { CommonModule, DOCUMENT } from '@angular/common';
import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, Subject, combineLatest } from 'rxjs';
import { switchMap, takeUntil, tap } from 'rxjs/operators';
import { NzInputDirective, NzInputGroupComponent } from 'ng-zorro-antd/input';
import {
  GridEventTicketComponent,
  ImageComponent,
  LayoutComponent,
  NavbarComponent,
  NewsCardComponent,
} from 'src/app/lib/components';
import { routeName } from 'src/app/lib/constants';
import { emptySearchGroup, SearchGroup, SearchTab } from 'src/app/lib/dto';
import { AccessoryShowResponse, SearchShowResponse } from 'src/app/lib/entities';
import { NewsItem } from 'src/app/lib/entities/news.response';
import { SearchService } from 'src/app/lib/services';
import { appImages } from 'src/app/lib/theme';
import { productDetailPath } from 'src/app/lib/utils';

/** "Tất cả" tab preview size per group; also doubles as the count-only size for inactive tabs. */
const ALL_PREVIEW_SIZE = 6;
const EVENT_PAGE_SIZE = 12;
const PRODUCT_PAGE_SIZE = 12;
const NEWS_PAGE_SIZE = 10;
/** Cheap request just to keep an inactive tab's count in the tab bar accurate. */
const COUNT_ONLY_SIZE = 1;

interface ParsedSearchParams {
  key: string;
  tab: SearchTab;
  page: number;
}

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    LayoutComponent,
    NavbarComponent,
    ImageComponent,
    GridEventTicketComponent,
    NewsCardComponent,
    NzInputDirective,
    NzInputGroupComponent,
  ],
  templateUrl: './search.component.html',
  styleUrl: './search.component.scss',
})
export class SearchComponent implements OnInit, OnDestroy {
  protected readonly routeName = routeName;
  protected readonly eventPageSize = EVENT_PAGE_SIZE;
  protected readonly productPageSize = PRODUCT_PAGE_SIZE;
  protected readonly newsPageSize = NEWS_PAGE_SIZE;

  key = '';
  tab: SearchTab = 'all';
  page = 1;
  /** Local draft for the in-page search box — only becomes `key` on submit. */
  searchBoxValue = '';

  eventsGroup: SearchGroup<SearchShowResponse> = emptySearchGroup();
  productsGroup: SearchGroup<AccessoryShowResponse> = emptySearchGroup();
  newsGroup: SearchGroup<NewsItem> = emptySearchGroup();

  private readonly _destroy$ = new Subject<void>();

  constructor(
    @Inject(DOCUMENT) private readonly _document: Document,
    private readonly _route: ActivatedRoute,
    private readonly _router: Router,
    private readonly _searchService: SearchService,
    private readonly _titleService: Title,
    private readonly _metaService: Meta,
  ) {}

  ngOnInit(): void {
    this._route.queryParamMap
      .pipe(
        tap((params) => {
          const parsed = this._parseParams(params.get('key'), params.get('tab'), params.get('page'));
          this.key = parsed.key;
          this.tab = parsed.tab;
          this.page = parsed.page;
          this.searchBoxValue = parsed.key;
          this._updateSeo();
        }),
        switchMap(() => (this.key ? this._loadActiveTab$() : this._resetGroups$())),
        takeUntil(this._destroy$),
      )
      .subscribe();
  }

  ngOnDestroy(): void {
    this._destroy$.next();
    this._destroy$.complete();
    this._metaService.removeTag("name='robots'");
  }

  onSubmitSearchBox(): void {
    const trimmed = this.searchBoxValue.trim();
    if (!trimmed) {
      return;
    }
    void this._router.navigate(['/', routeName.search], { queryParams: { key: trimmed } });
  }

  /** Page numbers 1..totalPages for the pager (same unwindowed convention as /news). */
  pageList(total: number, size: number): number[] {
    const totalPages = Math.max(1, Math.ceil(total / size));
    return Array.from({ length: totalPages }, (_, i) => i + 1);
  }

  /** Full query-param set for a tab+page link — `key` always kept, `tab`/`page` omitted at their default. */
  tabAndPageQuery(tab: SearchTab, page: number): Record<string, string | number> {
    const query: Record<string, string | number> = { key: this.key };
    if (tab !== 'all') {
      query['tab'] = tab;
    }
    if (page > 1) {
      query['page'] = page;
    }
    return query;
  }

  productDetailPath(show: AccessoryShowResponse): string {
    return productDetailPath(show);
  }

  productImage(show: AccessoryShowResponse): string {
    return show.bannerImageUrl || appImages.imgMetaTicketThumbnail;
  }

  /** Grid badge copy: Waiting / Open / Closed. Empty when status is unknown. */
  productStatusLabel(show: AccessoryShowResponse): string {
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

  productStatusChipClass(show: AccessoryShowResponse): string {
    switch ((show.status || '').trim()) {
      case 'Waiting':
        return 'pl-chip--waiting';
      case 'Closed':
        return 'pl-chip--closed';
      default:
        return 'pl-chip--open';
    }
  }

  trackById(_index: number, item: { id: string }): string {
    return item.id;
  }

  get hasNoResultAtAll(): boolean {
    const isSettled = !this.eventsGroup.loading && !this.productsGroup.loading && !this.newsGroup.loading;
    return isSettled && this.eventsGroup.total === 0 && this.productsGroup.total === 0 && this.newsGroup.total === 0;
  }

  private _parseParams(rawKey: string | null, rawTab: string | null, rawPage: string | null): ParsedSearchParams {
    const key = (rawKey || '').trim();
    const tab: SearchTab = rawTab === 'event' || rawTab === 'product' || rawTab === 'news' ? rawTab : 'all';
    const parsedPage = Number(rawPage || '1');
    const page = Number.isFinite(parsedPage) && parsedPage > 0 ? Math.floor(parsedPage) : 1;
    return { key, tab, page };
  }

  private _resetGroups$(): Observable<never> {
    this.eventsGroup = emptySearchGroup();
    this.productsGroup = emptySearchGroup();
    this.newsGroup = emptySearchGroup();
    return new Observable<never>((subscriber) => subscriber.complete());
  }

  /**
   * Loads all 3 groups every time: the active tab (or all 3 in "Tất cả") at its real page size, the
   * inactive tabs at `COUNT_ONLY_SIZE` just so the tab bar's counts stay accurate. `switchMap` at the
   * call site cancels the previous set of requests if params change again before they resolve.
   */
  private _loadActiveTab$(): Observable<unknown> {
    const eventSize = this.tab === 'event' ? EVENT_PAGE_SIZE : this.tab === 'all' ? ALL_PREVIEW_SIZE : COUNT_ONLY_SIZE;
    const eventPage = this.tab === 'event' ? this.page - 1 : 0;
    const productSize =
      this.tab === 'product' ? PRODUCT_PAGE_SIZE : this.tab === 'all' ? ALL_PREVIEW_SIZE : COUNT_ONLY_SIZE;
    const productPage = this.tab === 'product' ? this.page - 1 : 0;
    const newsSize = this.tab === 'news' ? NEWS_PAGE_SIZE : this.tab === 'all' ? ALL_PREVIEW_SIZE : COUNT_ONLY_SIZE;
    const newsPage = this.tab === 'news' ? this.page - 1 : 0;

    const events$ = this._searchService.searchEvents(this.key, eventPage, eventSize).pipe(
      tap((g) => {
        this.eventsGroup = g;
        if (this.tab === 'event') {
          this._clampPageIfNeeded(g, EVENT_PAGE_SIZE);
        }
      }),
    );
    const products$ = this._searchService.searchProducts(this.key, productPage, productSize).pipe(
      tap((g) => {
        this.productsGroup = g;
        if (this.tab === 'product') {
          this._clampPageIfNeeded(g, PRODUCT_PAGE_SIZE);
        }
      }),
    );
    const news$ = this._searchService.searchNews(this.key, newsPage, newsSize).pipe(
      tap((g) => {
        this.newsGroup = g;
        if (this.tab === 'news') {
          this._clampPageIfNeeded(g, NEWS_PAGE_SIZE);
        }
      }),
    );

    return combineLatest([events$, products$, news$]);
  }

  /** Redirects to the last valid page if the URL's `page` is past the end, mirroring /news. */
  private _clampPageIfNeeded(group: SearchGroup<unknown>, size: number): void {
    if (group.loading) {
      return;
    }
    const totalPages = Math.max(1, Math.ceil(group.total / size));
    if (this.page > totalPages) {
      void this._router.navigate(['/', routeName.search], {
        queryParams: this.tabAndPageQuery(this.tab, totalPages),
        replaceUrl: true,
      });
    }
  }

  private _updateSeo(): void {
    const title = this.key ? `Kết quả tìm kiếm cho "${this.key}" | META Ticket` : 'Tìm kiếm | META Ticket';
    const desc = this.key
      ? `Kết quả tìm kiếm cho "${this.key}" trên META Ticket.`
      : 'Tìm kiếm sự kiện, sản phẩm và tin tức trên META Ticket.';
    this._titleService.setTitle(title);
    this._metaService.updateTag({ name: 'description', content: desc });
    // Thin/duplicate content — never let search result pages get indexed.
    this._metaService.updateTag({ name: 'robots', content: 'noindex, follow' });
  }
}
