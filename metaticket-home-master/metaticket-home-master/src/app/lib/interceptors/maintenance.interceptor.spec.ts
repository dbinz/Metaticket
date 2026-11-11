import { TestBed } from '@angular/core/testing';
import {
  HTTP_INTERCEPTORS,
  HttpClient,
  HttpHeaders,
  provideHttpClient,
  withInterceptorsFromDi,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { MaintenanceService } from '../services/maintenance/maintenance.service';
import { MaintenanceInterceptor } from './maintenance.interceptor';

describe('MaintenanceInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let service: MaintenanceService;
  const apiUrl = `${environment.metaDomain}/api/order/create`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: HTTP_INTERCEPTORS, useClass: MaintenanceInterceptor, multi: true },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(MaintenanceService);
  });

  afterEach(() => http.verify());

  it('shows maintenance without replaying a rejected order', () => {
    let isActive = false;
    const errorHandler = jasmine.createSpy('business error handler');
    const completeHandler = jasmine.createSpy('business completion handler');
    service.isActive$.subscribe((value) => (isActive = value));
    const subscription = client.post(apiUrl, {}).subscribe({ error: errorHandler, complete: completeHandler });
    http
      .expectOne(apiUrl)
      .flush(
        { code: 'MAINTENANCE' },
        { status: 503, statusText: 'Service Unavailable', headers: new HttpHeaders().set('X-Maintenance-Mode', 'on') },
      );
    expect(isActive).toBeTrue();
    expect(errorHandler).not.toHaveBeenCalled();
    expect(completeHandler).not.toHaveBeenCalled();
    http.expectNone(apiUrl);
    subscription.unsubscribe();
  });

  it('blocks subsequent business requests while keeping payment returns available', () => {
    service.activate();
    const subscription = client.post(apiUrl, {}).subscribe();
    http.expectNone(apiUrl);
    const returnUrl = `${environment.metaDomain}/api/payment/return/result/onepay`;
    client.get(returnUrl).subscribe();
    const request = http.expectOne(returnUrl);
    expect(request.request.method).toBe('GET');
    request.flush({});
    subscription.unsubscribe();
  });

  it('does not mislabel an ordinary backend outage as planned maintenance', () => {
    let isActive = false;
    service.isActive$.subscribe((value) => (isActive = value));
    client.get(apiUrl).subscribe({ error: () => undefined });
    http.expectOne(apiUrl).flush({}, { status: 503, statusText: 'Service Unavailable' });
    expect(isActive).toBeFalse();
  });

  it('ignores maintenance headers from another origin', () => {
    let isActive = false;
    const externalUrl = 'https://example.invalid/api/order/create';
    service.isActive$.subscribe((value) => (isActive = value));
    client.get(externalUrl).subscribe({ error: () => undefined });
    http
      .expectOne(externalUrl)
      .flush(
        {},
        { status: 503, statusText: 'Service Unavailable', headers: new HttpHeaders().set('X-Maintenance-Mode', 'on') },
      );
    expect(isActive).toBeFalse();
  });
});
