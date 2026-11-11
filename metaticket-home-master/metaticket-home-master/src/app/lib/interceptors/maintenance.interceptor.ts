import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { NEVER, Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MaintenanceService } from '../services/maintenance/maintenance.service';

@Injectable()
export class MaintenanceInterceptor implements HttpInterceptor {
  constructor(private readonly maintenance: MaintenanceService) {}

  /** Only the gateway's explicit maintenance response may block the storefront. */
  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const requestUrl = new URL(request.url, environment.metaDomain);
    const isGatewayApi =
      requestUrl.origin === new URL(environment.metaDomain).origin && /^\/api(?:\/|$)/.test(requestUrl.pathname);
    const isPaymentCallback = /^\/api\/payment\/(ipn\/result|return\/result)(\/|$)/.test(requestUrl.pathname);
    if (this.maintenance.isActive && isGatewayApi && !isPaymentCallback) {
      return NEVER;
    }
    return next.handle(request).pipe(
      catchError((error: unknown) => {
        if (
          isGatewayApi &&
          error instanceof HttpErrorResponse &&
          error.status === 503 &&
          error.headers.get('X-Maintenance-Mode') === 'on'
        ) {
          this.maintenance.activate();
          // Legacy error/complete handlers clear auth/payment state. Hold until reload instead.
          return NEVER;
        }
        return throwError(() => error);
      }),
    );
  }
}
