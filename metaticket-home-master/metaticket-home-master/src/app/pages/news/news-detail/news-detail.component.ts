import { DOCUMENT } from '@angular/common';
import { Component, Inject, OnDestroy, OnInit, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { LayoutComponent, NavbarComponent } from 'src/app/lib/components';
import { newsArticleHref, newsShowCtaLabel } from 'src/app/lib/constants/news.constants';
import { routeName } from 'src/app/lib/constants';
import { NewsDetailResponse, NewsItem, NewsLinkedShowType } from 'src/app/lib/entities/news.response';
import { NewsService } from 'src/app/lib/services';

@Component({
  selector: 'app-news-detail',
  standalone: true,
  imports: [LayoutComponent, NavbarComponent, RouterLink],
  templateUrl: './news-detail.component.html',
  styleUrl: './news-detail.component.scss',
})
export class NewsDetailComponent implements OnInit, OnDestroy {
  protected readonly routeName = routeName;
  protected readonly newsArticleHref = newsArticleHref;

  newsDetail: NewsDetailResponse | undefined;
  relatedNews: NewsItem[] = [];
  recommendedNews: NewsItem[] = [];

  /** True until the article itself has loaded (related/recommended load in afterward, no skeleton needed for those). */
  protected loading = true;
  /** Varying widths so the skeleton paragraph doesn't look like a repeated bar. */
  protected readonly skeletonLines = ['100%', '95%', '88%', '92%', '60%', '80%'];

  private readonly _destroy$ = new Subject<void>();
  private readonly _route = inject(ActivatedRoute);
  private readonly _router = inject(Router);
  private readonly _newsService = inject(NewsService);
  private readonly _titleService = inject(Title);
  private readonly _metaService = inject(Meta);

  constructor(@Inject(DOCUMENT) private readonly _document: Document) {}

  ngOnInit(): void {
    this._route.params.pipe(takeUntil(this._destroy$)).subscribe((params) => {
      const slug = params['slug'] as string | undefined;
      if (slug) {
        this._loadNewsDetail(slug);
      }
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

  /** Italic hash-prefixed tag list at the end of the article. */
  protected tagLine(tags: string[] | undefined): string {
    return (tags ?? []).map((name) => `#${name}`).join(' ');
  }

  /** Loads article by slug; missing mock/API slug goes to not-found. */
  private _loadNewsDetail(slug: string): void {
    this._newsService
      .getNewsBySlug(slug)
      .pipe(takeUntil(this._destroy$))
      .subscribe({
        next: (news) => {
          this.newsDetail = news;
          this.loading = false;
          this._updateSeo(news);
          this._loadSideRails(news);
        },
        error: () => {
          this.newsDetail = undefined;
          void this._router.navigate(['/', routeName.notFound], { replaceUrl: true });
        },
      });
  }

  /** Related = `/fetch/related`; recommended = top 6 in current category. */
  private _loadSideRails(news: NewsDetailResponse): void {
    const slug = news.slug;
    this._newsService
      .getRelatedNews(slug, 6)
      .pipe(takeUntil(this._destroy$))
      .subscribe({
        next: (res) => {
          this.relatedNews = res.content ?? [];
        },
      });

    if (!news.categoryCode) {
      this.recommendedNews = [];
      return;
    }
    this._newsService
      .getNewsList({ offset: 0, size: 6, categoryCode: news.categoryCode, excludeSlug: slug })
      .pipe(takeUntil(this._destroy$))
      .subscribe({
        next: (res) => {
          this.recommendedNews = res.content ?? [];
        },
      });
  }

  /** Title/meta/canonical for the article; description uses summary (no `subdesc`). */
  private _updateSeo(news: NewsDetailResponse): void {
    const title = `${news.title} | META Ticket`;
    const desc = news.subdesc || news.summary;
    const url = `${this._document.location.origin}${newsArticleHref(news.slug)}`;
    const image = news.thumbnail;

    this._titleService.setTitle(title);
    this._metaService.updateTag({ name: 'description', content: desc });
    this._metaService.updateTag({ property: 'og:title', content: title });
    this._metaService.updateTag({ property: 'og:description', content: desc });
    this._metaService.updateTag({ property: 'og:url', content: url });
    this._metaService.updateTag({ property: 'og:type', content: 'article' });
    this._metaService.updateTag({ property: 'og:image', content: image });

    const canonical = this._document.querySelector('link[rel="canonical"]');
    if (canonical) {
      this._document.head.removeChild(canonical);
    }
    const link: HTMLLinkElement = this._document.createElement('link');
    link.setAttribute('rel', 'canonical');
    link.setAttribute('href', url);
    this._document.head.appendChild(link);
  }
}
