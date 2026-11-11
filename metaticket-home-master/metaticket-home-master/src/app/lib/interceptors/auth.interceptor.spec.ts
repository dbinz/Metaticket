import { HTTP_INTERCEPTORS, HttpErrorResponse, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { apiUrl, routeName } from 'src/app/lib/constants';
import { AccessoryService, AuthService, HttpClientService, OrderService } from 'src/app/lib/services';
import { AuthInterceptor } from './auth.interceptor';

/**
 * A 401 normally logs the customer out and sends them to the login page. The promotion-offer lists are optional and
 * fetched lazily when the customer focuses the code box, so their 401 must stay silent (the panel just hides), while
 * every other API, and near-miss URLs, keep the redirect.
 */
describe('AuthInterceptor — 401 handling', () => {
  const TOKEN = 'test-token';
  let httpMock: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['getToken', 'clearAuthState']);
    authService.getToken.and.returnValue(TOKEN);
    router = jasmine.createSpyObj<Router>('Router', ['navigate'], { url: '/dat-ve?show=1&session=2' });
    router.navigate.and.resolveTo(true);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  /** Fires the request, answers it with 401 and returns what the caller saw. */
  function answer401(request$: Observable<unknown>, pathSuffix: string): { request: TestRequest; errors: unknown[] } {
    const errors: unknown[] = [];
    request$.subscribe({ error: (error: unknown) => errors.push(error) });
    const request = httpMock.expectOne((req) => req.url.endsWith(pathSuffix));
    request.flush(null, { status: 401, statusText: 'Unauthorized' });
    return { request, errors };
  }

  function statusOf(error: unknown): number | undefined {
    return error instanceof HttpErrorResponse ? error.status : undefined;
  }

  it('keeps the session on a 401 from the ticket offers list, still sending the token and the error', () => {
    const orderService = TestBed.inject(OrderService);
    const { request, errors } = answer401(
      orderService.getPromotionOffers('show', 'session'),
      '/api/order/promotions/offers',
    );

    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${TOKEN}`);
    expect(errors.map(statusOf)).toEqual([401]);
    expect(authService.clearAuthState.calls.count()).toBe(0);
    expect(router.navigate.calls.count()).toBe(0);
  });

  it('keeps the session on a 401 from the accessory offers list, still sending the token and the error', () => {
    const accessoryService = TestBed.inject(AccessoryService);
    const { request, errors } = answer401(
      accessoryService.getDiscountOffers('enc-id'),
      '/api/accessory/discount/offers',
    );

    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${TOKEN}`);
    expect(errors.map(statusOf)).toEqual([401]);
    expect(authService.clearAuthState.calls.count()).toBe(0);
    expect(router.navigate.calls.count()).toBe(0);
  });

  it('still logs out and redirects on a 401 from the tentative price (regression)', () => {
    const http = TestBed.inject(HttpClientService);
    const { request, errors } = answer401(http.post(apiUrl.tentativePrice, {}), '/api/order/price/tentative');

    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${TOKEN}`);
    expect(errors.map(statusOf)).toEqual([401]);
    expect(authService.clearAuthState.calls.count()).toBe(1);
    expect(router.navigate.calls.allArgs()).toEqual([
      [[routeName.login], { queryParams: { returnUrl: '/dat-ve?show=1&session=2' } }],
    ]);
  });

  // Exact path match: a substring check on the offers paths would also silence these.
  for (const nearMiss of [
    'public/order/promotions/offers',
    'order/promotions/offers/extra',
    'xorder/promotions/offers',
  ]) {
    it(`still redirects on a 401 from the near-miss path ${nearMiss}`, () => {
      const http = TestBed.inject(HttpClientService);
      answer401(http.get(nearMiss), `/api/${nearMiss}`);

      expect(authService.clearAuthState.calls.count()).toBe(1);
      expect(router.navigate.calls.count()).toBe(1);
    });
  }
});
