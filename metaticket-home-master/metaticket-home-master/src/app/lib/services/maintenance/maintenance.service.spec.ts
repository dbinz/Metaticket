import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter, withNavigationErrorHandler } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { MaintenanceService, checkMaintenanceOnNavigationError } from './maintenance.service';

describe('MaintenanceService', () => {
  let service: MaintenanceService;
  let http: HttpTestingController;
  const statusUrl = `${environment.metaDomain}/maintenance/status`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(MaintenanceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('activates maintenance from the gateway status', () => {
    let isActive = false;
    service.isActive$.subscribe((value) => (isActive = value));
    service.checkStatus().subscribe();
    http.expectOne(statusUrl).flush({ maintenance: true });
    expect(isActive).toBeTrue();
  });

  it('keeps an active screen until an explicit page reload, even after maintenance ends', () => {
    service.activate();
    let isActive = false;
    service.isActive$.subscribe((value) => (isActive = value));
    service.checkStatus().subscribe();
    http.expectOne(statusUrl).flush({ maintenance: false });
    expect(isActive).toBeTrue();
  });

  it('reports unreachable status without mistaking a network failure for maintenance', () => {
    let isActive = false;
    let hasCheckFailed = false;
    let isEnabled: boolean | null = true;
    service.isActive$.subscribe((value) => (isActive = value));
    service.hasCheckFailed$.subscribe((value) => (hasCheckFailed = value));
    service.checkStatus().subscribe((value) => (isEnabled = value));
    http.expectOne(statusUrl).error(new ProgressEvent('error'));
    expect(isActive).toBeFalse();
    expect(hasCheckFailed).toBeTrue();
    expect(isEnabled).toBeNull();
  });

  it('rejects a malformed status instead of treating it as permission to resume', () => {
    let isEnabled: boolean | null = false;
    service.checkStatus().subscribe((value) => (isEnabled = value));
    http.expectOne(statusUrl).flush({ maintenance: 'false' });
    expect(isEnabled).toBeNull();
  });

  it('does not poll a hidden tab, then checks as soon as the customer returns to it', fakeAsync(() => {
    let isHidden = true;
    spyOnProperty(document, 'hidden').and.callFake((): boolean => isHidden);
    service.start();
    tick(90000);
    http.expectNone(statusUrl);

    isHidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    http.expectOne(statusUrl).flush({ maintenance: true });
    expect(service.isActive).toBeTrue();
    discardPeriodicTasks();
  }));

  it('keeps polling every 30 s while the tab stays visible', fakeAsync(() => {
    spyOnProperty(document, 'hidden').and.returnValue(false);
    service.start();
    tick();
    http.expectOne(statusUrl).flush({ maintenance: false });
    tick(29999);
    http.expectNone(statusUrl);
    tick(1);
    const request = http.expectOne(statusUrl);
    expect(request.request.method).toBe('GET');
    request.flush({ maintenance: false });
    discardPeriodicTasks();
  }));

  it('stops polling when the customer switches away mid-session and resumes on return', fakeAsync(() => {
    let isHidden = false;
    spyOnProperty(document, 'hidden').and.callFake((): boolean => isHidden);
    service.start();
    tick();
    http.expectOne(statusUrl).flush({ maintenance: false });

    isHidden = true;
    document.dispatchEvent(new Event('visibilitychange'));
    tick(90000);
    http.expectNone(statusUrl);

    isHidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    const request = http.expectOne(statusUrl);
    expect(request.request.method).toBe('GET');
    request.flush({ maintenance: false });
    discardPeriodicTasks();
  }));

  it('does not stack a second probe when the tab returns while one is still in flight', fakeAsync(() => {
    let isHidden = false;
    spyOnProperty(document, 'hidden').and.callFake((): boolean => isHidden);
    service.start();
    tick();
    const pending = http.expectOne(statusUrl);

    isHidden = true;
    document.dispatchEvent(new Event('visibilitychange'));
    isHidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    expect(http.match(statusUrl).length).toBe(0);

    pending.flush({ maintenance: false });
    tick(30000);
    http.expectOne(statusUrl).flush({ maintenance: false });
    discardPeriodicTasks();
  }));
});

describe('checkMaintenanceOnNavigationError', () => {
  it('shows maintenance as soon as a lazy chunk fails instead of waiting for the next poll', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(
          [
            {
              path: 'lazy',
              loadComponent: (): Promise<never> =>
                Promise.reject(new TypeError('Failed to fetch dynamically imported module')),
            },
          ],
          withNavigationErrorHandler(checkMaintenanceOnNavigationError),
        ),
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const service = TestBed.inject(MaintenanceService);
    let isActive = false;
    service.isActive$.subscribe((value) => (isActive = value));

    await TestBed.inject(Router)
      .navigateByUrl('/lazy')
      .catch(() => undefined);

    http.expectOne(`${environment.metaDomain}/maintenance/status`).flush({ maintenance: true });
    expect(isActive).toBeTrue();
    http.verify();
  });
});
