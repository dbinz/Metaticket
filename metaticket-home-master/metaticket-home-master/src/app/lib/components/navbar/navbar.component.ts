import { CommonModule, isPlatformBrowser, NgTemplateOutlet } from '@angular/common';
import {
  Component,
  ElementRef,
  HostBinding,
  Inject,
  Injector,
  Input,
  NgZone,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  Renderer2,
  ViewChild,
  afterNextRender,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationExtras } from '@angular/router';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { Observable, Subject, of } from 'rxjs';
import { debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { apiUrl, routeName } from 'src/app/lib/constants';
import { NEWS_ALL_CATEGORY_CODE, NEWS_CATEGORY_PATH } from 'src/app/lib/constants/news.constants';
import { DropdownMenuItem } from 'src/app/lib/dto';
import { SearchShowResponse } from 'src/app/lib/entities';
import { AuthService, NavigationService, NewsService, ShowService } from 'src/app/lib/services';
import { SearchService, SearchSuggestGroups } from 'src/app/lib/services/search/search.service';
import { appIcons } from 'src/app/lib/theme';
import { CollapseMenuComponent } from 'src/app/lib/components/collapse-menu/collapse-menu.component';
import { DropdownMenuComponent } from 'src/app/lib/components/dropdown-menu/dropdown-menu.component';
import { ImageComponent } from 'src/app/lib/components/image/image.component';
import { InputSearchMobileComponent } from 'src/app/lib/components/input-search-mobile/input-search-mobile.component';
import { InputSearchComponent } from 'src/app/lib/components/input-search/input-search.component';
import { HomeSearchQuery, HomeSearchService } from '../../stores';
import { UserProfileModel } from '../../entities';
import { ButtonComponent } from '../button/button.component';

/** Below this length the edge-ngram (min_gram 1) analyzer would match almost the whole catalog. */
const MIN_SEARCH_KEYWORD_LENGTH = 2;

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    DropdownMenuComponent,
    ImageComponent,
    NzDrawerModule,
    NzTypographyModule,
    NzCollapseModule,
    CommonModule,
    InputSearchComponent,
    InputSearchMobileComponent,
    CollapseMenuComponent,
    ButtonComponent,
  ],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css'],
})
export class NavbarComponent implements OnInit, OnDestroy {
  // Auth/localStorage differ between SSR HTML and the first client pass — skip matching this host.
  @HostBinding('attr.ngSkipHydration') readonly ngSkipHydration = 'true';

  /** Reserves document flow space equal to the fixed navbar bar. */
  @HostBinding('style.height.px') hostHeightPx = 64;

  @ViewChild('surface', { static: true }) surfaceRef?: ElementRef<HTMLElement>;
  @ViewChild('desktopSearchWrapper') desktopSearchWrapperRef?: ElementRef<HTMLElement>;

  @Input() backgroundColor: 'primary' | 'transparent' = 'primary';

  protected readonly appIcons = appIcons;
  private readonly _isBrowser: boolean;
  private _searchSubject = new Subject<string>();
  private _resizeObserver?: ResizeObserver;
  loading = false;
  error = null;
  highlightResults: SearchShowResponse[] = [];
  suggestGroups: SearchSuggestGroups = SearchService.emptySuggestGroups();
  showResults = false;
  visibleDrawer = false;
  getCategoryLoading = false;
  listEventCategory: DropdownMenuItem[] = [];
  getListCityLoading = false;
  listCity: DropdownMenuItem[] = [];
  searchValue = '';
  visibleSearch = false;
  /** Laptop-only: search bar collapses to just the icon until clicked. */
  isDesktopSearchExpanded = false;
  choseCity: DropdownMenuItem | null | undefined;
  choseEvent: DropdownMenuItem | null | undefined;
  getNewsCategoryLoading = false;
  listNewsCategory: DropdownMenuItem[] = [];
  choseNewsCategory: DropdownMenuItem | null | undefined;

  protected profileMenuItem: DropdownMenuItem[] = [
    <DropdownMenuItem>{
      title: 'Thông tin tài khoản',
      value: routeName.accountInfo,
    },
    <DropdownMenuItem>{
      title: 'Quản lý vé',
      value: routeName.ticketManager,
    },
    <DropdownMenuItem>{
      title: 'Quản lý đơn hàng',
      value: routeName.orderManager,
    },
    <DropdownMenuItem>{
      title: 'Đăng xuất',
      value: apiUrl.logout,
    },
  ];

  constructor(
    private _navigationService: NavigationService,
    private _showService: ShowService,
    private _newsService: NewsService,
    private _searchService: SearchService,
    private _homeSearchService: HomeSearchService,
    private _homeSearchQuery: HomeSearchQuery,
    private _authService: AuthService,
    private _ngZone: NgZone,
    private _injector: Injector,
    private _renderer: Renderer2,
    @Inject(PLATFORM_ID) platformId: object,
  ) {
    this._isBrowser = isPlatformBrowser(platformId);
    afterNextRender(() => this._bindHostHeight(), { injector: this._injector });
    // Collapse the desktop search bar back to just the icon on any click outside it.
    this._renderer.listen('window', 'click', (e: Event) => {
      if (!this.isDesktopSearchExpanded) {
        return;
      }
      // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
      if (!this.desktopSearchWrapperRef?.nativeElement.contains(e.target as Node)) {
        this.isDesktopSearchExpanded = false;
      }
    });
    this._searchSubject
      .pipe(
        map((keyword) => keyword.trim()),
        debounceTime(300), // delay in milliseconds
        distinctUntilChanged(),
        switchMap((keyword) =>
          keyword.length >= MIN_SEARCH_KEYWORD_LENGTH
            ? this._searchService.suggest(keyword)
            : of(SearchService.emptySuggestGroups()),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((groups) => {
        this.suggestGroups = groups;
      });
    this._homeSearchQuery
      .select('citySelected')
      .pipe(takeUntilDestroyed())
      .subscribe((citySelected) => {
        this.choseCity = citySelected;
      });
    this._homeSearchQuery
      .select('eventSelected')
      .pipe(takeUntilDestroyed())
      .subscribe((eventSelected) => {
        this.choseEvent = eventSelected;
      });
  }

  get userProfile$(): Observable<UserProfileModel | null> {
    return this._authService.userProfile$;
  }

  get loggedIn$(): Observable<boolean> {
    return this._authService.authStatus$;
  }

  ngOnDestroy(): void {
    this._resizeObserver?.disconnect();
  }

  /** Observe the fixed bar after paint so spacer height does not change mid-check. */
  private _bindHostHeight(): void {
    if (!this._isBrowser) {
      return;
    }
    const el = this.surfaceRef?.nativeElement;
    if (el && typeof ResizeObserver !== 'undefined') {
      this._resizeObserver = new ResizeObserver(() => {
        const h = el.offsetHeight;
        if (!h || h === this.hostHeightPx) {
          return;
        }
        this._ngZone.run(() => {
          this.hostHeightPx = h;
        });
      });
      this._resizeObserver.observe(el);
    }
    this.syncHostHeight();
  }

  /** Keep in-flow spacer height matched to the fixed bar (incl. mobile search). */
  syncHostHeight(): void {
    if (!this._isBrowser) {
      return;
    }
    const h = this.surfaceRef?.nativeElement?.offsetHeight;
    if (h && h > 0 && h !== this.hostHeightPx) {
      this.hostHeightPx = h;
    }
  }

  private _getEventCategory(): void {
    this.getCategoryLoading = true;
    this._showService.getEventCategory().subscribe({
      next: (response) => {
        this.listEventCategory = response.map((item) => ({
          title: item.name,
          value: item.id,
        }));
        this.getCategoryLoading = false;
      },
      error: (err) => {
        this.getCategoryLoading = false;
        console.log('get event category error', err);
      },
    });
  }

  private _getListCity(): void {
    this.getListCityLoading = true;
    this._showService.getListCity().subscribe({
      next: (response) => {
        this.listCity = response.map((item) => ({
          title: item.name,
          value: item.code,
        }));
        this.getListCityLoading = false;
      },
      error: (err) => {
        this.getListCityLoading = false;
        console.log('get list home-search error', err);
      },
    });
  }

  /** Loads news categories for the navbar dropdown (Tất cả + API list). */
  private _getNewsCategoryList(): void {
    this.getNewsCategoryLoading = true;
    this._newsService.getNewsCategoryList().subscribe({
      next: (response) => {
        this.listNewsCategory = [
          {
            title: 'Tất cả tin tức',
            value: NEWS_ALL_CATEGORY_CODE,
            routerLink: '/news',
            queryParams: {},
          },
          ...response.map((item) => ({
            title: item.name,
            value: item.code,
            routerLink: `/news/${NEWS_CATEGORY_PATH}/${item.code}`,
            queryParams: {},
          })),
        ];
        this.getNewsCategoryLoading = false;
      },
      error: (err) => {
        this.getNewsCategoryLoading = false;
        console.log('get news category error', err);
      },
    });
  }

  ngOnInit(): void {
    this._getEventCategory();
    this._getListCity();
    this._getNewsCategoryList();
    this._loadHighlightResults();
  }

  /** Loads once at page load so "Top sự kiện nổi bật" is ready the moment the user focuses the input. */
  private _loadHighlightResults(): void {
    this._showService.getHighlightShow({ page: 0, size: 6, cities: [] }).subscribe({
      next: (res) => {
        this.highlightResults = res.content;
      },
      error: (err) => console.log('get highlight show error', err),
    });
  }

  onOpenDrawer(): void {
    this.visibleDrawer = true;
  }

  onOpenSearch(): void {
    this.visibleSearch = true;
  }

  onExpandDesktopSearch(): void {
    this.isDesktopSearchExpanded = true;
  }

  onCloseDrawer(): void {
    this.visibleDrawer = false;
  }

  selectItemUser(item: DropdownMenuItem): void {
    if (item.value == apiUrl.logout) {
      this._authService.logout();
      return;
    }
    this._navigationService.navigateTo(item.value);
    this.visibleDrawer = false;
  }

  navigateToProduct(): void {
    this._navigationService.navigateTo(routeName.product);
    this.visibleDrawer = false;
  }

  /** Opens /news or /news/category/:code — path, not query. */
  selectNewsCategory(item: DropdownMenuItem): void {
    this.choseNewsCategory = item;
    if (item.value === NEWS_ALL_CATEGORY_CODE) {
      this._navigationService.navigateTo(routeName.news);
    } else {
      this._navigationService.navigateByUrl(`/${routeName.news}/${NEWS_CATEGORY_PATH}/${item.value}`);
    }
    this.visibleDrawer = false;
  }

  selectItemEvent(item: DropdownMenuItem): void {
    // this._homeSearchService.onSelectEvent(item);
    const extras: NavigationExtras = {
      queryParams: {
        id: item.value,
        name: item.title,
      },
    };
    this._navigationService.navigateTo(routeName.event, extras);
    this.visibleDrawer = false;
  }

  selectCity(item: DropdownMenuItem): void {
    this.onCloseDrawer();
    this._homeSearchService.onSelectCity(item);
  }

  navigateToHome(): void {
    this._navigationService.navigateToHome();
  }

  navigateToLogin(): void {
    this._navigationService.navigateTo(routeName.login);
  }

  onSearch(event: string): void {
    this.searchValue = event;
    this._searchSubject.next(event);
  }

  onClickSearch(): void {
    this.showResults = true;
  }

  clearSearchValue(): void {
    this.searchValue = '';
  }
}
