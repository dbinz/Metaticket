import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { EMPTY, Observable, expand, map, of, reduce, throwError } from 'rxjs';
import { environment } from 'src/environments/environment';
import { mockShowDetail, mockShowPage } from './show.mock';
import { apiUrl } from 'src/app/lib/constants';
import {
  CityResponse,
  EventCategoryResponse,
  GetHighlightShowResponse,
  HighlightShowRequest,
  SearchShowFilter,
  SearchShowResponse,
  SeatBlocksResponse,
  ShowBySlugResponse,
  ShowResponse,
} from 'src/app/lib/entities';
import { HttpClientService } from '../http-client/http-client.service';

@Injectable({ providedIn: 'root' })
export class ShowService {
  private readonly mockEnabled = 'mockShows' in environment && environment.mockShows === true;
  constructor(private _httpClientService: HttpClientService) {}

  getShowDetail(showId: string): Observable<ShowResponse> {
    const mock = this.mockEnabled ? mockShowDetail(showId) : undefined;
    if (mock) return of(mock);
    const param = new HttpParams().set('id', showId);
    return this._httpClientService.getJson<ShowResponse>(apiUrl.getShowDetail, param);
  }

  getShowBySlug(showSlug: string, sessionSlug?: string): Observable<ShowBySlugResponse> {
    const mock = this.mockEnabled ? mockShowDetail(showSlug) : undefined;
    if (mock) return of({ ...mock, matchedSessionId: mock.sessions.find((s) => s.slug === sessionSlug)?.id });
    let params = new HttpParams().set('showSlug', showSlug);
    if (sessionSlug) {
      params = params.set('sessionSlug', sessionSlug);
    }
    return this._httpClientService.getJson<ShowBySlugResponse>(apiUrl.getShowBySlug, params);
  }

  searchShowByFilter(body: SearchShowFilter): Observable<GetHighlightShowResponse> {
    let params = new HttpParams().set('page', body.page).set('size', body.size);
    if ('isFree' in body) {
      params = params.set(`isFree`, body.isFree ?? false);
    }
    if (body.eventCategoryCode) {
      params = params.set(`eventCategoryCode`, body.eventCategoryCode);
    }
    if (body.startTime) {
      params = params.set(`startTime`, body.startTime);
    }
    if (body.endTime) {
      params = params.set(`endTime`, body.endTime);
    }
    if (body.cities && body.cities.length > 0) {
      body.cities.forEach((city) => {
        params = params.append(`cities`, city);
      });
    }
    if (!this.mockEnabled) return this._httpClientService.getJson<GetHighlightShowResponse>(apiUrl.getShowFilter, params);
    // Local fixtures are added to the complete filtered API result before pagination.
    const fetchPage = (page: number) => this._httpClientService.getJson<GetHighlightShowResponse>(
      apiUrl.getShowFilter, params.set('page', page).set('size', 60),
    );
    return fetchPage(0).pipe(
      expand((result) => {
        if (result.last || result.number + 1 >= result.totalPages) return EMPTY;
        if (result.number >= 150) return throwError(() => new Error('Event results exceed the API pagination window'));
        return fetchPage(result.number + 1);
      }),
      reduce((shows, result) => [...shows, ...result.content], [] as SearchShowResponse[]),
      // Reuse the same filtering/paging contract for mocks and real results.
      map((shows) => mockShowPage(body, shows)),
    );
  }

  getHighlightShow(body: HighlightShowRequest): Observable<GetHighlightShowResponse> {
    let params = new HttpParams().set('page', body.page).set('size', body.size);
    if (body.cities && body.cities.length > 0) {
      body.cities.forEach((city) => {
        params = params.append(`cities`, city);
      });
    }
    return this._httpClientService.getJson<GetHighlightShowResponse>(apiUrl.getHighlightShow, params);
  }

  getTopBannerShow(): Observable<GetHighlightShowResponse> {
    return this._httpClientService.getJson<GetHighlightShowResponse>(apiUrl.getTopBanner);
  }

  searchShowByKeyword(param: { keyword: string; page: number; size: number }): Observable<GetHighlightShowResponse> {
    const params = new HttpParams()
      .set('page', param.page)
      .set('size', param.size)
      .set('keyword', param.keyword.trim())
      .set('nameOnly', true);
    return this._httpClientService.getJson<GetHighlightShowResponse>(apiUrl.searchShowByKeyword, params);
  }

  getEventCategory(): Observable<EventCategoryResponse[]> {
    return this._httpClientService.getJson<EventCategoryResponse[]>(apiUrl.getEventCategory);
  }

  getListCity(): Observable<CityResponse[]> {
    return this._httpClientService.getJson<CityResponse[]>(apiUrl.getListCity);
  }

  getSeatBlocks(seatPlanId: string): Observable<SeatBlocksResponse> {
    const params = new HttpParams().set('globalId', seatPlanId);
    return this._httpClientService.getJson<SeatBlocksResponse>(apiUrl.getSeatBlocks, params);
  }

  saveToLocalStorage(show: SearchShowResponse): void {
    const shows = localStorage.getItem('shows') || '[]';
    const showsJson = JSON.parse(shows) as SearchShowResponse[];
    const index = showsJson.findIndex((item: SearchShowResponse) => item.id === show.id);
    if (index != -1) {
      showsJson.splice(index, 1);
    }
    showsJson.unshift(show);
    localStorage.setItem('shows', JSON.stringify(showsJson));
  }

  getShowsFromLocalStorage(): SearchShowResponse[] {
    const shows = localStorage.getItem('shows');
    if (shows) {
      return JSON.parse(shows) as SearchShowResponse[];
    }
    return [];
  }
}
