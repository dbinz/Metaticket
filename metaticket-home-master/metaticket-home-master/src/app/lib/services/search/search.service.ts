import { Injectable } from '@angular/core';
import { Observable, combineLatest, of } from 'rxjs';
import { catchError, map, startWith, timeout } from 'rxjs/operators';
import { SearchGroup, emptySearchGroup, failedSearchGroup, loadingSearchGroup } from 'src/app/lib/dto';
import { AccessoryShowResponse, SearchShowResponse } from 'src/app/lib/entities';
import { NewsItem } from 'src/app/lib/entities/news.response';
import { AccessoryService } from '../accessory/accessory.service';
import { NewsService } from '../news/news.service';
import { ShowService } from '../show/show.service';

/** Dropdown suggestion sizes — Sự kiện gets the most slots since it's the primary commercial intent. */
const SUGGEST_EVENT_SIZE = 5;
const SUGGEST_PRODUCT_SIZE = 3;
const SUGGEST_NEWS_SIZE = 3;

/**
 * WordPress (Tin tức) is consistently slower than the other two sources, hence the longer budget.
 * Each source renders independently, so a generous budget never delays the others — it only decides
 * when a slow-but-working request (mobile network, cold TLS) flips to "Không tải được".
 */
const EVENT_TIMEOUT_MS = 6000;
const PRODUCT_TIMEOUT_MS = 6000;
const NEWS_TIMEOUT_MS = 8000;

export interface SearchSuggestGroups {
  events: SearchGroup<SearchShowResponse>;
  products: SearchGroup<AccessoryShowResponse>;
  news: SearchGroup<NewsItem>;
}

/**
 * Fan-out facade over the three independent search sources (metasearch/ES, accessory/Mongo,
 * news/WordPress). Every method assumes a non-blank `keyword` — callers (navbar, /search page)
 * own the "keyword too short / empty" decision, since the empty-keyword case renders different UI
 * (highlight shows) rather than a search result.
 */
@Injectable({ providedIn: 'root' })
export class SearchService {
  constructor(
    private _showService: ShowService,
    private _accessoryService: AccessoryService,
    private _newsService: NewsService,
  ) {}

  searchEvents(keyword: string, page = 0, size = SUGGEST_EVENT_SIZE): Observable<SearchGroup<SearchShowResponse>> {
    return this._showService.searchShowByKeyword({ keyword, page, size }).pipe(
      map((res) => toGroup(res.content, res.totalElements)),
      timeout(EVENT_TIMEOUT_MS),
      catchError(() => of(failedSearchGroup<SearchShowResponse>())),
      startWith(loadingSearchGroup<SearchShowResponse>()),
    );
  }

  searchProducts(
    keyword: string,
    page = 0,
    size = SUGGEST_PRODUCT_SIZE,
  ): Observable<SearchGroup<AccessoryShowResponse>> {
    return this._accessoryService.searchAccessoryShows(keyword, page, size).pipe(
      map((res) => toGroup(res.content, res.totalElements)),
      timeout(PRODUCT_TIMEOUT_MS),
      catchError(() => of(failedSearchGroup<AccessoryShowResponse>())),
      startWith(loadingSearchGroup<AccessoryShowResponse>()),
    );
  }

  searchNews(keyword: string, page = 0, size = SUGGEST_NEWS_SIZE): Observable<SearchGroup<NewsItem>> {
    return this._newsService.searchNews(keyword, page, size).pipe(
      map((res) => toGroup(res.content, res.totalElements)),
      timeout(NEWS_TIMEOUT_MS),
      catchError(() => of(failedSearchGroup<NewsItem>())),
      startWith(loadingSearchGroup<NewsItem>()),
    );
  }

  /**
   * Dropdown gợi ý: gọi song song 3 nguồn, phát ngay khi từng nguồn đã sẵn sàng thay vì chờ nguồn
   * chậm nhất (`combineLatest` re-emits per branch), nhờ đó cột Sự kiện (Elasticsearch) thường
   * hiện trước cột Tin tức (WordPress, chậm hơn).
   */
  suggest(keyword: string): Observable<SearchSuggestGroups> {
    return combineLatest([
      this.searchEvents(keyword, 0, SUGGEST_EVENT_SIZE),
      this.searchProducts(keyword, 0, SUGGEST_PRODUCT_SIZE),
      this.searchNews(keyword, 0, SUGGEST_NEWS_SIZE),
    ]).pipe(map(([events, products, news]) => ({ events, products, news })));
  }

  static emptySuggestGroups(): SearchSuggestGroups {
    return {
      events: emptySearchGroup<SearchShowResponse>(),
      products: emptySearchGroup<AccessoryShowResponse>(),
      news: emptySearchGroup<NewsItem>(),
    };
  }
}

function toGroup<T>(items: T[], total: number): SearchGroup<T> {
  return { items, total, loading: false, failed: false };
}
