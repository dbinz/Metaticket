import { Injectable } from '@angular/core';
import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';
import { AuthService } from '../services';
import { apiUrl, routeName } from '../constants';
import { environment } from 'src/environments/environment';

// Optional lists fetched lazily when the customer focuses a promo-code box: a 401 there must not log them out of a
// booking (the panel just hides). Matched on the exact gateway path HttpClientService builds (`/<prefix>/<path>`,
// prefix trimmed the same way), because a substring check would also silence e.g. `.../offers/x` or `public/...`.
const SILENT_UNAUTHORIZED_PATHS = [apiUrl.getPromotionOffers, apiUrl.getAccessoryDiscountOffers].map(
  (path) => `/${(environment.metaPrefix || '').replace(/^\/+|\/+$/g, '')}/${path}`,
);

function isSilentUnauthorizedPath(url: string): boolean {
  const path = url.split(/[?#]/, 1)[0];
  return SILENT_UNAUTHORIZED_PATHS.some((silentPath) => path.endsWith(silentPath));
}

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(
    private authService: AuthService,
    private router: Router,
  ) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.authService.getToken();
    // eslint-disable-next-line @typescript-eslint/naming-convention
    const authReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

    if (req.url.includes(apiUrl.logout) || req.url.includes(apiUrl.refreshToken)) {
      return next.handle(authReq);
    }

    // Same bypass as above: the token is still attached and the 401 still reaches the caller, only no logout/redirect.
    if (isSilentUnauthorizedPath(req.url)) {
      return next.handle(authReq);
    }

    return next.handle(authReq).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          return this.handle401Error();
        }
        return throwError(() => error);
      }),
    );
  }

  private handle401Error(): Observable<HttpEvent<any>> {
    // Chỉ redirect về login, không tự động refresh nữa vì đã có cơ chế proactive refresh
    if (!this.router.url.includes(routeName.login)) {
      this.authService.clearAuthState();
      this.router.navigate([routeName.login], { queryParams: { returnUrl: this.router.url } });
    }
    return throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }));
  }
}
