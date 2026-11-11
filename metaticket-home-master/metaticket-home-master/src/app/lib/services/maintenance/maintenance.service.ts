import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { HttpBackend, HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, PLATFORM_ID, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  BehaviorSubject,
  Observable,
  catchError,
  exhaustMap,
  filter,
  fromEvent,
  map,
  merge,
  of,
  tap,
  timeout,
  timer,
} from 'rxjs';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MaintenanceService {
  // Status must remain independent of authentication and the interceptor reporting maintenance.
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly document = inject(DOCUMENT);
  private readonly activeSubject = new BehaviorSubject(false);
  private readonly checkFailedSubject = new BehaviorSubject(false);
  private hasStarted = false;

  readonly isActive$ = this.activeSubject.asObservable();
  readonly hasCheckFailed$ = this.checkFailedSubject.asObservable();

  get isActive(): boolean {
    return this.activeSubject.value;
  }

  /** Detects maintenance in already-open tabs; polling never replays a business request. */
  start(): void {
    if (this.hasStarted || !isPlatformBrowser(this.platformId)) {
      return;
    }
    this.hasStarted = true;
    // Every open tab polls the gateway, so hidden tabs skip their ticks and returning to the tab checks at once.
    const becameVisible$ = fromEvent(this.document, 'visibilitychange').pipe(filter(() => !this.document.hidden));
    merge(timer(0, 30000), becameVisible$)
      .pipe(
        filter(() => !this.document.hidden),
        exhaustMap(() => this.checkStatus()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  activate(): void {
    this.activeSubject.next(true);
  }

  /** A failed/invalid probe must never reopen checkout or claim maintenance is over. */
  checkStatus(): Observable<boolean | null> {
    return this.http.get<unknown>(`${environment.metaDomain}/maintenance/status`).pipe(
      timeout(5000),
      map((body) => {
        if (!body || typeof body !== 'object' || !('maintenance' in body) || typeof body.maintenance !== 'boolean') {
          throw new Error('Invalid maintenance status');
        }
        return body.maintenance;
      }),
      tap((enabled) => {
        this.checkFailedSubject.next(false);
        // Keep the screen until the customer explicitly reloads: their reservation may have expired.
        if (enabled) {
          this.activate();
        }
      }),
      catchError(() => {
        this.checkFailedSubject.next(true);
        return of(null);
      }),
    );
  }
}

/** Router hook: a lazy chunk answered by the gateway's 503 page fails silently, so probe now instead of waiting for the poll. */
export function checkMaintenanceOnNavigationError(): void {
  inject(MaintenanceService).checkStatus().subscribe();
}
