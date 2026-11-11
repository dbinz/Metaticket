import { Injectable } from '@angular/core';
import { NewsService } from '../../@core/metaticket/data.service';
import {
    NewsArticleForm,
    NewsArticleRow,
    NewsCategoryOption,
    NewsPageResponse,
    SaveNewsArticleRequest,
} from '../../@core/metaticket/model';
import { Observable } from 'rxjs';
import { NewsGateway } from '../gateway/news.gateway';

@Injectable()
export class MetaticketNewsService extends NewsService {
    constructor(private newsGateway: NewsGateway) {
        super();
    }

    listArticles(
        page: number,
        size: number,
        search?: string,
        category?: string,
    ): Observable<NewsPageResponse<NewsArticleRow>> {
        return this.newsGateway.listArticles(page, size, search, category);
    }

    getArticle(id: number): Observable<NewsArticleForm> {
        return this.newsGateway.getArticle(id);
    }

    createArticle(request: SaveNewsArticleRequest): Observable<NewsArticleForm> {
        return this.newsGateway.createArticle(request);
    }

    updateArticle(id: number, request: SaveNewsArticleRequest): Observable<NewsArticleForm> {
        return this.newsGateway.updateArticle(id, request);
    }

    setVisibility(id: number, visible: boolean): Observable<NewsArticleRow> {
        return this.newsGateway.setVisibility(id, visible);
    }

    listCategories(): Observable<NewsCategoryOption[]> {
        return this.newsGateway.listCategories();
    }
}
