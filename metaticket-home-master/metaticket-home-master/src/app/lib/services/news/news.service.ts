import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { apiUrl } from 'src/app/lib/constants/api-url';
import { isNewsUseMock } from 'src/app/lib/constants/news.constants';
import {
  GetNewsListRequest,
  GetNewsListResponse,
  NewsCategoryItem,
  NewsDetailResponse,
  NewsHighlightPage,
  NewsItem,
  NewsSearchPage,
} from 'src/app/lib/entities/news.response';
import { HttpClientService } from '../http-client/http-client.service';
import { getMockNewsBySlug, getMockNewsCategories, getMockNewsList } from 'src/app/pages/news/news.mock';

@Injectable({ providedIn: 'root' })
export class NewsService {
  constructor(private readonly _httpClientService: HttpClientService) {}

  /**
   * Lấy danh sách danh mục tin tức.
   */
  getNewsCategoryList(): Observable<NewsCategoryItem[]> {
    if (isNewsUseMock) {
      return getMockNewsCategories();
    }
    return this._httpClientService.get<NewsCategoryItem[]>(apiUrl.getNewsCategoryList);
  }

  /**
   * Lấy danh sách tin tức (offset-based).
   * Lần đầu: offset=0, size=9 — Xem thêm: size=6.
   */
  getNewsList(request: GetNewsListRequest): Observable<GetNewsListResponse> {
    if (isNewsUseMock) {
      return getMockNewsList(request);
    }
    let params = new HttpParams().set('offset', request.offset).set('size', request.size);

    if (request.categoryCode) {
      params = params.set('categoryCode', request.categoryCode);
    }
    if (request.sort) {
      params = params.set('sort', request.sort);
    }
    if (request.excludeSlug) {
      params = params.set('excludeSlug', request.excludeSlug);
    }

    return this._httpClientService
      .get<GetNewsListResponse>(apiUrl.getNewsList, params)
      .pipe(map((resp) => this._decodeNewsListResponse(resp)));
  }

  /**
   * Lấy chi tiết bài viết theo slug.
   */
  getNewsBySlug(slug: string): Observable<NewsDetailResponse> {
    if (isNewsUseMock) {
      return getMockNewsBySlug(slug);
    }
    const params = new HttpParams().set('slug', slug);
    return this._httpClientService
      .get<NewsDetailResponse>(apiUrl.getNewsBySlug, params)
      .pipe(map((detail) => this._decodeNewsDetail(detail)));
  }

  /** Hub hero / “Bài viết nổi bật” — featured pins only. */
  getFeaturedNews(excludeSlug?: string): Observable<NewsHighlightPage> {
    let params = new HttpParams();
    if (excludeSlug) {
      params = params.set('excludeSlug', excludeSlug);
    }
    return this._httpClientService.get<NewsHighlightPage>(apiUrl.getFeaturedNews, params).pipe(
      map((resp) => ({
        content: (resp?.content ?? []).map((item) => this._decodeNewsItem(item)),
        totalElements: resp?.totalElements ?? 0,
      })),
    );
  }

  /** Keyword search (0-based `page`; both `page`/`size` required by the backend) — "Tin tức" search column. */
  searchNews(keyword: string, page = 0, size = 6): Observable<NewsSearchPage> {
    const params = new HttpParams().set('keyword', keyword).set('page', page).set('size', size);
    return this._httpClientService
      .get<NewsSearchPage>(apiUrl.searchNews, params)
      .pipe(map((resp) => ({ ...resp, content: (resp?.content ?? []).map((item) => this._decodeNewsItem(item)) })));
  }

  /** Same-category rail for the open article. */
  getRelatedNews(slug: string, size = 6, excludeSlug?: string): Observable<NewsHighlightPage> {
    let params = new HttpParams().set('slug', slug).set('size', size);
    if (excludeSlug) {
      params = params.set('excludeSlug', excludeSlug);
    }
    return this._httpClientService.get<NewsHighlightPage>(apiUrl.getRelatedNews, params).pipe(
      map((resp) => ({
        content: (resp?.content ?? []).map((item) => this._decodeNewsItem(item)),
        totalElements: resp?.totalElements ?? 0,
      })),
    );
  }

  /** Decode HTML entities in titles/summaries for listing and detail payloads. */

  private _decodeHtmlEntities(str: string): string {
    if (!str) return '';
    // 1. Giải mã các ký tự số (ví dụ: &#8216; -> ‘, &#8217; -> ’)
    let decoded = str.replace(/&#(\d+);/g, (match: string, dec: string): string => String.fromCharCode(Number(dec)));
    decoded = decoded.replace(/&#x([0-9a-fA-F]+);/g, (match: string, hex: string): string =>
      String.fromCharCode(parseInt(hex, 16)),
    );

    // 2. Giải mã các ký tự đặt tên thông dụng
    const namedEntities: Record<string, string> = {
      amp: '&',
      lt: '<',
      gt: '>',
      quot: '"',
      apos: "'",
      deg: '°',
      ndash: '–',
      mdash: '—',
      hellip: '…',
      middot: '·',
      copy: '©',
      reg: '®',
      trade: '™',
    };
    return decoded.replace(/&([a-zA-Z0-9]+);/g, (match: string, name: string): string => namedEntities[name] || match);
  }

  private _decodeNewsItem<T extends NewsItem>(item: T): T {
    if (!item) return item;
    return {
      ...item,
      title: this._decodeHtmlEntities(item.title),
      summary: this._decodeHtmlEntities(item.summary),
      subdesc: this._decodeHtmlEntities(item.subdesc ?? item.summary),
    };
  }

  private _decodeNewsDetail(detail: NewsDetailResponse): NewsDetailResponse {
    if (!detail) return detail;
    const decoded = this._decodeNewsItem(detail);
    return {
      ...decoded,
      relatedNews: (detail.relatedNews ?? []).map((item) => this._decodeNewsItem(item)),
    };
  }

  private _decodeNewsListResponse(resp: GetNewsListResponse): GetNewsListResponse {
    if (!resp || !resp.content) return resp;
    return {
      ...resp,
      content: resp.content.map((item) => this._decodeNewsItem(item)),
    };
  }
}
