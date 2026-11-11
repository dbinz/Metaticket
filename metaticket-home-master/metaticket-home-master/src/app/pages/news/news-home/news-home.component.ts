import { DOCUMENT } from '@angular/common';
import { Component, Inject, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { Subject, catchError, forkJoin, of, takeUntil } from 'rxjs';
import { NzCarouselModule } from 'ng-zorro-antd/carousel';
import { LayoutComponent, NavbarComponent } from 'src/app/lib/components';
import {
  NEWS_ALL_CATEGORY_CODE,
  NEWS_CATEGORY_PATH,
  newsArticleHref,
  newsShowCtaLabel,
} from 'src/app/lib/constants/news.constants';
import { routeName } from 'src/app/lib/constants';
import { NewsCategoryItem, NewsItem, NewsLinkedShowType } from 'src/app/lib/entities/news.response';
import { NewsService } from 'src/app/lib/services/news/news.service';
import { NewsCategorySectionUi, newsCategorySectionFromApi } from '../news.mock';

const PREVIEW_COUNT = 3;
const EXPAND_COUNT = 6;
const EMPTY_HIGHLIGHT = { content: [] as NewsItem[], totalElements: 0 };

@Component({
  selector: 'app-news-home',
  standalone: true,
  imports: [LayoutComponent, NavbarComponent, RouterLink, NzCarouselModule],
  templateUrl: './news-home.component.html',
  styleUrls: ['./news-home.component.scss'],
})
export class NewsHomeComponent implements OnInit, OnDestroy {
  protected readonly routeName = routeName;
  protected readonly categoryPath = NEWS_CATEGORY_PATH;
  protected readonly newsArticleHref = newsArticleHref;
  protected readonly previewCount = PREVIEW_COUNT;
  protected featured: NewsItem[] = [];
  protected sections: NewsCategorySectionUi[] = [];
  protected articlesByCode: Record<string, NewsItem[]> = {};
  protected currentFeaturedIndex = 0;

  /** True while the initial hub data (featured + categories) is loading. */
  protected loading = true;
  /** Placeholder counts for the skeleton — purely cosmetic, no data behind them. */
  protected readonly skeletonSections = [0, 1, 2];
  protected readonly skeletonTiles = [0, 1, 2];

  expandedCodes = new Set<string>();

  private _featuredLoaded = false;
  private _sectionsLoaded = false;

  private readonly _destroy$ = new Subject<void>();
  private readonly _titleService = inject(Title);
  private readonly _metaService = inject(Meta);
  private readonly _route = inject(ActivatedRoute);
  private readonly _router = inject(Router);
  private readonly _newsService = inject(NewsService);

  constructor(@Inject(DOCUMENT) private readonly _document: Document) {}

  /** Featured pin 1 — large hero. */
  get hero(): NewsItem | undefined {
    return this.featured[0];
  }

  /** Featured pins 2–3 — right column (replaces most-viewed). */
  get sideFeatured(): NewsItem[] {
    return this.featured.slice(1, 3);
  }

  protected onFeaturedChange(index: number): void {
    this.currentFeaturedIndex = index;
  }

  ngOnInit(): void {
    const code = this._route.snapshot.queryParamMap.get('categoryCode');
    if (code && code !== NEWS_ALL_CATEGORY_CODE) {
      void this._router.navigate(['/', routeName.news, NEWS_CATEGORY_PATH, code], { replaceUrl: true });
      return;
    }
    this._updatePageSeo();
    this._loadHub();
  }

  ngOnDestroy(): void {
    this._destroy$.next();
    this._destroy$.complete();
  }

  /** Articles belonging to one CMS category. */
  articlesOf(code: string): NewsItem[] {
    return this.articlesByCode[code] ?? [];
  }

  /** First row (3) or expanded preview (6). */
  visibleArticles(section: NewsCategorySectionUi): NewsItem[] {
    const all = this.articlesOf(section.code);
    const limit = this.isExpanded(section.code) ? EXPAND_COUNT : PREVIEW_COUNT;
    return all.slice(0, limit);
  }

  /** Toggles the 3→6 reveal. */
  toggleExpand(code: string): void {
    if (this.expandedCodes.has(code)) {
      this.expandedCodes.delete(code);
    } else {
      this.expandedCodes.add(code);
    }
  }

  isExpanded(code: string): boolean {
    return this.expandedCodes.has(code);
  }

  /** Event → ĐẶT VÉ NGAY, product → XEM SẢN PHẨM. */
  protected ctaLabel(type: NewsLinkedShowType): string {
    return newsShowCtaLabel(type);
  }

  private _loadHub(): void {
    this._newsService
      .getFeaturedNews()
      .pipe(
        catchError(() => of(EMPTY_HIGHLIGHT)),
        takeUntil(this._destroy$),
      )
      .subscribe((res) => {
        this.featured = res.content ?? [];
        this._featuredLoaded = true;
        this._maybeFinishLoading();
      });

    this._newsService
      .getNewsCategoryList()
      .pipe(
        catchError(() => of([] as NewsCategoryItem[])),
        takeUntil(this._destroy$),
      )
      .subscribe((cats) => {
        this.sections = cats.map((cat, i) => newsCategorySectionFromApi(cat, i));
        if (this.sections.length === 0) {
          this.articlesByCode = {};
          this._sectionsLoaded = true;
          this._maybeFinishLoading();
          return;
        }
        forkJoin(
          this.sections.map((section) =>
            this._newsService
              .getNewsList({ offset: 0, size: EXPAND_COUNT, categoryCode: section.code })
              .pipe(catchError(() => of({ content: [] as NewsItem[] }))),
          ),
        )
          .pipe(takeUntil(this._destroy$))
          .subscribe((pages) => {
            const next: Record<string, NewsItem[]> = {};
            this.sections.forEach((section, i) => {
              next[section.code] = pages[i]?.content ?? [];
            });
            this.articlesByCode = next;
            this._sectionsLoaded = true;
            this._maybeFinishLoading();
          });
      });
  }

  /** Flips off the skeleton once both the hero and every category's articles have resolved. */
  private _maybeFinishLoading(): void {
    if (this._featuredLoaded && this._sectionsLoaded) {
      this.loading = false;
    }
  }

  /** SEO tags for the news hub (not a category listing). */
  private _updatePageSeo(): void {
    const title = 'Tin tức | META Ticket';
    const desc = 'Cập nhật tin tức sự kiện, âm nhạc, giải trí mới nhất tại META Ticket.';
    const url = `${this._document.location.origin}/news`;

    this._titleService.setTitle(title);
    this._metaService.updateTag({ name: 'description', content: desc });
    this._metaService.updateTag({ property: 'og:title', content: title });
    this._metaService.updateTag({ property: 'og:description', content: desc });
    this._metaService.updateTag({ property: 'og:url', content: url });
    this._metaService.updateTag({ property: 'og:type', content: 'website' });

    const head = this._document.head;
    const existing = this._document.querySelector('link[rel="canonical"]');
    if (existing) {
      head.removeChild(existing);
    }
    const link: HTMLLinkElement = this._document.createElement('link');
    link.setAttribute('rel', 'canonical');
    link.setAttribute('href', url);
    head.appendChild(link);
  }
}
