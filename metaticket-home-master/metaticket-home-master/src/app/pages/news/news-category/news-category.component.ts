import { DOCUMENT } from '@angular/common';
import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, of, switchMap, takeUntil } from 'rxjs';
import { LayoutComponent, NavbarComponent } from 'src/app/lib/components';
import {
  NEWS_CATEGORY_PAGE_SIZE,
  NEWS_CATEGORY_PATH,
  newsArticleHref,
  newsShowCtaLabel,
} from 'src/app/lib/constants/news.constants';
import { routeName } from 'src/app/lib/constants';
import { NewsCategoryItem, NewsItem, NewsLinkedShowType } from 'src/app/lib/entities/news.response';
import { NewsService } from 'src/app/lib/services/news/news.service';
import { NewsCategorySectionUi, newsCategorySectionFromApi } from '../news.mock';

@Component({
  selector: 'app-news-category',
  standalone: true,
  imports: [LayoutComponent, NavbarComponent, RouterLink],
  templateUrl: './news-category.component.html',
  styleUrls: ['./news-category.component.scss'],
})
export class NewsCategoryComponent implements OnInit, OnDestroy {
  protected readonly routeName = routeName;
  protected readonly categoryPath = NEWS_CATEGORY_PATH;
  protected readonly newsArticleHref = newsArticleHref;

  section: NewsCategorySectionUi | undefined;
  items: NewsItem[] = [];
  currentPage = 1;
  totalPages = 1;
  totalElements = 0;

  /** True until the category is resolved and its first page of articles has loaded. */
  protected loading = true;
  /** Placeholder tile count for the skeleton — purely cosmetic, no data behind it. */
  protected readonly skeletonTiles = [0, 1, 2, 3, 4, 5];

  private readonly _destroy$ = new Subject<void>();

  constructor(
    @Inject(DOCUMENT) private readonly _document: Document,
    private readonly _route: ActivatedRoute,
    private readonly _router: Router,
    private readonly _newsService: NewsService,
    private readonly _titleService: Title,
    private readonly _metaService: Meta,
  ) {}

  ngOnInit(): void {
    this._route.paramMap
      .pipe(
        switchMap((params) => {
          const code = params.get('category') ?? '';
          return this._newsService.getNewsCategoryList().pipe(
            catchError(() => of([] as NewsCategoryItem[])),
            switchMap((cats) => {
              const index = cats.findIndex((c) => c.slug === code || c.code === code);
              if (index < 0) {
                void this._router.navigate(['/', routeName.notFound], { replaceUrl: true });
                return of(null);
              }
              this.section = newsCategorySectionFromApi(cats[index], index);
              return this._route.queryParamMap;
            }),
          );
        }),
        takeUntil(this._destroy$),
      )
      .subscribe((query) => {
        if (!this.section || !query) return;
        const page = Number(query.get('page') || '1');
        this._loadArticles(this.section, Number.isFinite(page) && page > 0 ? Math.floor(page) : 1);
      });
  }

  ngOnDestroy(): void {
    this._destroy$.next();
    this._destroy$.complete();
  }

  /** Event → ĐẶT VÉ NGAY, product → XEM SẢN PHẨM. */
  protected ctaLabel(type: NewsLinkedShowType): string {
    return newsShowCtaLabel(type);
  }

  /** Page numbers 1..totalPages for the pager. */
  pageList(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  /** Page 1 omits `?page=` so the canonical category URL stays clean. */
  pageQuery(page: number): Record<string, number> {
    return page <= 1 ? {} : { page };
  }

  private _loadArticles(section: NewsCategorySectionUi, page: number): void {
    const size = NEWS_CATEGORY_PAGE_SIZE;
    const offset = (page - 1) * size;

    this._newsService
      .getNewsList({ offset, size, categoryCode: section.code })
      .pipe(takeUntil(this._destroy$))
      .subscribe({
        next: (res) => {
          this.totalElements = res.totalElements;
          this.totalPages = Math.max(1, Math.ceil(res.totalElements / size) || 1);
          if (res.totalElements === 0) {
            this.currentPage = 1;
            this.items = [];
            this.loading = false;
            this._updatePageSeo(section.name, section.slug, 1);
            return;
          }
          if (page > this.totalPages) {
            void this._router.navigate(['/', routeName.news, NEWS_CATEGORY_PATH, section.slug], {
              queryParams: this.pageQuery(this.totalPages),
              replaceUrl: true,
            });
            return;
          }
          this.currentPage = page;
          this.items = res.content;
          this.loading = false;
          this._updatePageSeo(section.name, section.slug, page);
        },
      });
  }

  /** Title/canonical for a category listing; page > 1 is reflected in og:url. */
  private _updatePageSeo(categoryName: string, slug: string, page: number): void {
    const title = `${categoryName} | Tin tức | META Ticket`;
    const desc = `Tin tức ${categoryName} trên META Ticket.`;
    const origin = this._document.location.origin;
    const url =
      page > 1
        ? `${origin}/news/${NEWS_CATEGORY_PATH}/${slug}?page=${page}`
        : `${origin}/news/${NEWS_CATEGORY_PATH}/${slug}`;

    this._titleService.setTitle(title);
    this._metaService.updateTag({ name: 'description', content: desc });
    this._metaService.updateTag({ property: 'og:title', content: title });
    this._metaService.updateTag({ property: 'og:description', content: desc });
    this._metaService.updateTag({ property: 'og:url', content: url });
    this._metaService.updateTag({ property: 'og:type', content: 'website' });

    const existing = this._document.querySelector('link[rel="canonical"]');
    if (existing) {
      this._document.head.removeChild(existing);
    }
    const link: HTMLLinkElement = this._document.createElement('link');
    link.setAttribute('rel', 'canonical');
    link.setAttribute('href', url);
    this._document.head.appendChild(link);
  }
}
