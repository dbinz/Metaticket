import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

@Injectable({
  providedIn: 'root',
})
export class HttpClientService {
  constructor(private _http: HttpClient) {}

  private _metaDomain = environment.metaDomain;
  private _metaPrefix = environment.metaPrefix;

  /** Builds absolute (`https://host/api/...`) or same-origin (`/api/...`) URL. */
  private url(path: string): string {
    const prefix = (this._metaPrefix || '').replace(/^\/+|\/+$/g, '');
    const cleanPath = (path || '').replace(/^\/+/, '');
    const domain = (this._metaDomain || '').replace(/\/+$/, '');
    if (!domain) {
      return `/${prefix}/${cleanPath}`;
    }
    return `${domain}/${prefix}/${cleanPath}`;
  }

  get<T>(path: string, params?: HttpParams): Observable<T> {
    return this._http.get<T>(this.url(path), { params });
  }

  /** Reads JSON without losing plain-text error bodies in FetchBackend. Opt in per API contract. */
  getJson<T>(path: string, params?: HttpParams): Observable<T> {
    return this._http
      .get(this.url(path), { params, responseType: 'text', observe: 'response' })
      .pipe(map((response) => this.parseJsonResponse<T>(response)));
  }

  /** Reads JSON on success and retains the HTTP status, headers and text on failure. */
  postJson<T>(path: string, body: unknown, params?: HttpParams): Observable<T> {
    return this._http
      .post(this.url(path), body, { params, responseType: 'text', observe: 'response' })
      .pipe(map((response) => this.parseJsonResponse<T>(response)));
  }

  /** Void APIs can succeed with an empty body or a text acknowledgement (e.g. resend OTP). */
  postVoid(path: string, body: unknown): Observable<void> {
    return this._http.post(this.url(path), body, { responseType: 'text', observe: 'response' }).pipe(
      map((response) => {
        let parsed: unknown;
        try {
          parsed = JSON.parse(response.body ?? '');
        } catch {
          // No JSON payload is required by this contract; HTTP errors still propagate from HttpClient.
          return;
        }
        this.throwResponseError(response, parsed);
      }),
    );
  }

  private parseJsonResponse<T>(response: HttpResponse<string>): T {
    let parsed: unknown;
    try {
      parsed = JSON.parse(response.body ?? '');
    } catch {
      const text = response.body?.trim() ?? '';
      throw this.responseError(
        response,
        text && !text.startsWith('<') ? text : 'Phản hồi từ máy chủ không hợp lệ. Vui lòng thử lại.',
      );
    }
    this.throwResponseError(response, parsed);
    return parsed as T;
  }

  // Older services may return ErrorCommon with HTTP 200; never let it advance the success flow.
  private throwResponseError(response: HttpResponse<string>, parsed: unknown): void {
    if (parsed !== null && typeof parsed === 'object' && 'code' in parsed && 'message' in parsed) {
      if (typeof parsed.code === 'string' && parsed.code && typeof parsed.message === 'string' && parsed.message) {
        throw this.responseError(response, parsed.message);
      }
    }
  }

  private responseError(response: HttpResponse<string>, error: string): HttpErrorResponse {
    return new HttpErrorResponse({
      error,
      status: response.status,
      statusText: response.statusText,
      headers: response.headers,
      url: response.url ?? undefined,
    });
  }

  getAsText(path: string, params?: HttpParams): Observable<string> {
    return this._http.get<string>(this.url(path), { params, responseType: 'text' as 'json' });
  }

  post<T>(path: string, body: any, params?: HttpParams): Observable<T> {
    return this._http.post<T>(this.url(path), body, { params });
  }

  postAsText(path: string, body: any, params?: HttpParams): Observable<string> {
    return this._http.post(this.url(path), body, { params, responseType: 'text' });
  }

  put<T>(path: string, body: any, params?: HttpParams): Observable<T> {
    return this._http.put<T>(this.url(path), body, { params });
  }

  delete<T>(path: string, params?: HttpParams): Observable<T> {
    return this._http.delete<T>(this.url(path), { params });
  }
}
