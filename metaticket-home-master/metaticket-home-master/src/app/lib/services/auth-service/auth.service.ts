import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, throwError, timer } from 'rxjs';
import { map, retry, switchMap, tap } from 'rxjs/operators';
import { LocalStorageService } from '../local-storage/local-storage.service';
import { jwtDecode, JwtPayload } from 'jwt-decode';
import { apiUrl, routeName } from '../../constants';
import { HttpClientService } from '../http-client/http-client.service';
import { CaptchaSettingResponse, LoginRequest, LoginResponse, UserProfileModel } from '../../entities';
import { Router } from '@angular/router';
import { UserService } from '../user-service/user.service';
import { HttpErrorResponse } from '@angular/common/http';
import { environment } from 'src/environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private tokenKey: string = 'token';
  private _authStatusSubject = new BehaviorSubject<boolean>(this.isAuthenticated());
  readonly authStatus$ = this._authStatusSubject.asObservable();
  private readonly _userProfile$ = new BehaviorSubject<UserProfileModel | null>(null);
  private _isUserProfileFetching = false;
  private refreshSubscription: Subscription | null = null;
  private refreshTokenBeforeExpireInM = environment.refreshTokenBeforeExpireInM;

  constructor(
    private _http: HttpClientService,
    private _localStorage: LocalStorageService,
    private router: Router,
    private userService: UserService,
  ) {
    if (this.isAuthenticated()) {
      this.autoRefreshToken();
    }
  }

  // Lấy Access Token từ LocalStorage
  getToken(): string | null {
    return this._localStorage.getData(this.tokenKey);
  }

  /**
   * The current user's stable id, or null when not logged in / token invalid. Used to scope
   * per-user client state (e.g. the accessory cart) so it is never shared across accounts.
   *
   * NOTE: the customer JWT (service.customer.auth `JWTUtil`) does NOT set the standard `sub` claim —
   * it carries the id in a custom `userId` claim (`.withClaim("userId", userId)`), so we read that.
   */
  getUserId(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = jwtDecode<JwtPayload & { userId?: string }>(token);
      return payload.userId ?? null;
    } catch (e) {
      return null;
    }
  }

  get userProfile$(): Observable<UserProfileModel | null> {
    const isAuthenticated = this.isAuthenticated();
    const currentUserProfile = this._userProfile$.getValue();

    if (isAuthenticated && currentUserProfile === null && !this._isUserProfileFetching) {
      this._isUserProfileFetching = true;
      this.userService.getUserProfile().subscribe({
        next: (resp) => {
          this._userProfile$.next(resp);
          this._isUserProfileFetching = false;
        },
        error: (err) => {
          this.clearAuthState();
          console.log('get user profile error', err);
          this._isUserProfileFetching = false;
        },
      });
    }
    return this._userProfile$.asObservable();
  }

  getCaptchaSetting(): Observable<CaptchaSettingResponse> {
    return this._http.getJson(apiUrl.getAuthCaptchaSetting);
  }

  // Đăng nhập
  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this._http.postAsText(apiUrl.login, credentials).pipe(
      map((raw) => {
        let parsed: unknown;
        try {
          parsed = JSON.parse(raw);
        } catch {
          parsed = raw;
        }

        if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
          return parsed as LoginResponse;
        }

        // Mọi trường hợp còn lại (plain text, JSON string, number, array...) -> coi là message lỗi
        const message = typeof parsed === 'string' ? parsed : raw;
        throw new Error(message || 'Đăng nhập không thành công');
      }),
      tap((response) => {
        if (response.token) {
          this._localStorage.saveData(this.tokenKey, response.token);
          this._authStatusSubject.next(true);
          this.autoRefreshToken();
        }
      }),
    );
  }

  // Làm mới Access Token
  refreshToken(): Observable<LoginResponse> {
    return this._http.postJson<LoginResponse>(apiUrl.refreshToken, {}).pipe(
      tap((response) => {
        if (response.token) {
          this._localStorage.saveData(this.tokenKey, response.token);
          this._authStatusSubject.next(true);
          this.autoRefreshToken();
        }
      }),
    );
  }

  // Đăng xuất
  logout(): void {
    if (this.refreshSubscription) {
      this.refreshSubscription.unsubscribe();
      this.refreshSubscription = null;
    }
    this._http.postAsText(apiUrl.logout, {}).subscribe({
      next: () => {
        this.clearAuthState();
      },
      error: () => {
        this.clearAuthState();
      },
    });
  }

  clearAuthState(): void {
    if (this.refreshSubscription) {
      this.refreshSubscription.unsubscribe();
      this.refreshSubscription = null;
    }
    this._localStorage.removeData(this.tokenKey);
    this._authStatusSubject.next(false);
    this._userProfile$.next(null);
    this._isUserProfileFetching = false;
    // Session ended → send the user to the login page (consistent with the token-expiry paths, and
    // required so logging out mid-checkout leaves the — now unauthenticated — checkout instead of
    // being bounced back to it by the in-progress-order navigation lock).
    this.router.navigate([routeName.login]);
  }

  // Kiểm tra xem user có đăng nhập hay không
  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;

    try {
      const payload: JwtPayload = jwtDecode<JwtPayload>(token);
      return payload.exp ? payload.exp * 1000 > Date.now() : false;
    } catch (e) {
      return false;
    }
  }

  private autoRefreshToken(): void {
    if (this.refreshSubscription) {
      this.refreshSubscription.unsubscribe();
    }
    const token = this.getToken();
    if (!token) return;

    try {
      const payload: JwtPayload = jwtDecode<JwtPayload>(token);
      if (payload.exp) {
        const expires = new Date(payload.exp * 1000);
        // Refresh token trước khi hết hạn
        const timeout = expires.getTime() - Date.now() - this.refreshTokenBeforeExpireInM * 60 * 1000;

        this.refreshSubscription = timer(Math.max(0, timeout))
          .pipe(
            switchMap(() =>
              this.refreshToken().pipe(
                retry({
                  delay: (error: HttpErrorResponse) => {
                    console.warn('Auto refresh token failed', error);
                    // Nếu token đã hết hạn thực sự, dừng retry
                    if (Date.now() > expires.getTime()) {
                      setTimeout(() => {
                        this.clearAuthState();
                        this.router.navigate([routeName.login]);
                      }, expires.getTime() - Date.now());
                      return throwError(() => error);
                    }
                    // Nếu lỗi 401/403 (Token không hợp lệ), dừng retry
                    if (error.status === 401 || error.status === 403) {
                      setTimeout(() => {
                        this.clearAuthState();
                        this.router.navigate([routeName.login]);
                      }, expires.getTime() - Date.now());
                      return throwError(() => error);
                    }
                    // Retry sau 5 giây
                    return timer(5000);
                  },
                }),
              ),
            ),
          )
          .subscribe({
            error: (err) => {
              console.error('Auto refresh token failed after retries', err);
              // Nếu refresh thất bại và token đã hết hạn, logout
              if (!this.isAuthenticated()) {
                this.clearAuthState();
                this.router.navigate([routeName.login]);
              }
            },
          });
      }
    } catch (e) {
      console.error('Error decoding token for auto refresh', e);
    }
  }
}
