import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
    NewsArticleForm,
    NewsArticleRow,
    NewsCategoryOption,
    NewsPageResponse,
    NewsPinBoard,
    NewsPinCandidatePage,
    SaveNewsArticleRequest,
} from '../../@core/metaticket/model';
import { Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { privateApi } from '../../../environments/environment';

@Injectable()
export class NewsGateway {
    constructor(private http: HttpClient) {}

    listArticles(
        page: number,
        size: number,
        search?: string,
        category?: string,
    ): Observable<NewsPageResponse<NewsArticleRow>> {
        const params = new URLSearchParams({
            page: String(page),
            size: String(size),
        });
        if (search?.trim()) {
            params.set('search', search.trim());
        }
        if (category?.trim()) {
            params.set('category', category.trim());
        }
        return this.http
            .get<NewsPageResponse<NewsArticleRow>>(privateApi(`news/articles?${params.toString()}`))
            .pipe(take(1));
    }

    getArticle(id: number): Observable<NewsArticleForm> {
        return this.http.get<NewsArticleForm>(privateApi(`news/articles/${id}`)).pipe(take(1));
    }

    createArticle(request: SaveNewsArticleRequest): Observable<NewsArticleForm> {
        return this.http.post<NewsArticleForm>(privateApi('news/articles'), request).pipe(take(1));
    }

    updateArticle(id: number, request: SaveNewsArticleRequest): Observable<NewsArticleForm> {
        return this.http.put<NewsArticleForm>(privateApi(`news/articles/${id}`), request).pipe(take(1));
    }

    setVisibility(id: number, visible: boolean): Observable<NewsArticleRow> {
        return this.http
            .patch<NewsArticleRow>(privateApi(`news/articles/${id}/visibility`), { visible })
            .pipe(take(1));
    }

    listCategories(): Observable<NewsCategoryOption[]> {
        return this.http.get<NewsCategoryOption[]>(privateApi('news/categories')).pipe(take(1));
    }

    fetchFeaturedPinBoard(): Observable<NewsPinBoard> {
        return this.http.get<NewsPinBoard>(privateApi('news/pin/featured')).pipe(take(1));
    }

    refreshFeaturedPinBoard(): Observable<NewsPinBoard> {
        return this.http.post<NewsPinBoard>(privateApi('news/pin/featured/refresh'), {}).pipe(take(1));
    }

    confirmFeaturedPinBoard(slots: { pinOrder: number; articleId: number | null }[]): Observable<NewsPinBoard> {
        return this.http
            .post<NewsPinBoard>(privateApi('news/pin/featured/confirm'), { slots })
            .pipe(take(1));
    }

    fetchCategoryPinBoard(code: string): Observable<NewsPinBoard> {
        return this.http
            .get<NewsPinBoard>(privateApi(`news/pin/category/${encodeURIComponent(code)}`))
            .pipe(take(1));
    }

    refreshCategoryPinBoard(code: string): Observable<NewsPinBoard> {
        return this.http
            .post<NewsPinBoard>(privateApi(`news/pin/category/${encodeURIComponent(code)}/refresh`), {})
            .pipe(take(1));
    }

    confirmCategoryPinBoard(
        code: string,
        slots: { pinOrder: number; articleId: number | null }[],
    ): Observable<NewsPinBoard> {
        return this.http
            .post<NewsPinBoard>(privateApi(`news/pin/category/${encodeURIComponent(code)}/confirm`), { slots })
            .pipe(take(1));
    }

    searchPinCandidates(
        page: number,
        size: number,
        keyword?: string,
        categoryCode?: string,
    ): Observable<NewsPinCandidatePage> {
        let params = new URLSearchParams({
            page: String(page),
            size: String(size),
        });
        if (keyword?.trim()) {
            params.set('keyword', keyword.trim());
        }
        if (categoryCode?.trim()) {
            params.set('categoryCode', categoryCode.trim());
        }
        return this.http
            .get<NewsPinCandidatePage>(privateApi(`news/pin/candidates?${params.toString()}`))
            .pipe(take(1));
    }
}
