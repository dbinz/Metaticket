import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, Inject, OnInit, PLATFORM_ID } from '@angular/core';
import { NavigationStart, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { environment } from '../environments/environment';
import { pathName } from './lib/constants/path-name';
import { Meta } from '@angular/platform-browser';
import { ContactWidgetComponent } from './lib/components/contact-widget/contact-widget.component';
import { routeName, storageKeys } from './lib/constants';
import { AuthService, LocalStorageService } from './lib/services';
import { AccessoryCartService, AccessoryCheckoutService, AccessoryCheckoutState } from './lib/stores';
import { MaintenanceComponent } from './lib/components/maintenance/maintenance.component';
import { MaintenanceService } from './lib/services/maintenance/maintenance.service';
// import { appImages } from './lib/theme';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, ContactWidgetComponent, MaintenanceComponent],
  templateUrl: './app.component.html',
})
export class AppComponent implements OnInit {
  private readonly _isBrowser: boolean;

  constructor(
    private _meta: Meta,
    private _router: Router,
    private _localStorage: LocalStorageService,
    private _authService: AuthService,
    private _checkoutService: AccessoryCheckoutService,
    private _cartService: AccessoryCartService,
    public readonly maintenance: MaintenanceService,
    @Inject(PLATFORM_ID) platformId: object,
  ) {
    this._isBrowser = isPlatformBrowser(platformId);
  }
  title = 'mt-Ticket';
  protected readonly metaDomain = environment.metaDomain;
  protected readonly webrtUrl = this.metaDomain + pathName.webrtUrl;
  protected readonly plannerUrl = this.metaDomain + pathName.plannerUrl;

  ngOnInit(): void {
    // DOM / window APIs are browser-only.
    if (!this._isBrowser) {
      return;
    }
    this.maintenance.start();
    // Inject external scripts needed by the app shell.
    this.appendScript(this.webrtUrl);
    this.appendScript(this.plannerUrl);
    this._applyBootstrapCheckoutRedirect();
    this._enforceCheckoutLock();
  }

  /**
   * Keep the customer inside accessory checkout while an order is still PENDING (Thanh toán, or
   * Hoàn tất while statuses are still polling). Header clicks would otherwise start a second
   * purchase and double-hold stock/discount until the TTL sweep. After Hoàn tất already has a
   * terminal result, the lock drops so header navigation works; leaving then `clear()`s the flow
   * the same way the page CTAs do (`_leaveSuccess` / `onBackToCart` / `onConfirmComplete`).
   *
   * Information + fulfillment (no order yet) are never locked. Hủy đơn / expiry / CTA exits call
   * `clear()` before navigate, so they keep working. Ticket booking is only blocked during the
   * pending window (≤ TTL).
   */
  private _enforceCheckoutLock(): void {
    this._router.events.pipe(filter((e): e is NavigationStart => e instanceof NavigationStart)).subscribe((event) => {
      if (this._hasPendingOrder() && !this._isAllowedWhileLocked(event.url)) {
        this._router.navigateByUrl(`/${routeName.productCheckout}`);
        return;
      }
      // Header (and any other nav) after a terminal Hoàn tất result — drop the persisted flow so
      // the next visit to checkout cannot resume the old completed screen.
      this._releaseResolvedCheckoutIfLeaving(event.url);
    });
  }

  /**
   * True while the logged-in owner still has an unresolved accessory order: payment step, or
   * Hoàn tất still polling (`paymentResult` empty). A terminal `Succeed` / `Failed` (success,
   * failed, or mismatch — mismatch always records one of those two) does not lock.
   *
   * Legacy indices (payment at 1, completed at 2) stay covered so an in-flight order is not
   * orphaned. Logged-out users and other accounts on the same browser are never locked.
   */
  private _hasPendingOrder(): boolean {
    const saved = this._ownedCheckout();
    if (!saved || this._isResolvedCompleted(saved)) {
      return false;
    }
    const isAtPayment = !!saved.encodedPaymentId && (saved.currentIndex === 2 || saved.currentIndex === 1);
    const isAtCompletedProcessing = !!saved.encodedOrderId && saved.currentIndex >= 3;
    return isAtPayment || isAtCompletedProcessing;
  }

  /** Persisted checkout belonging to the currently logged-in user, or null. */
  private _ownedCheckout(): AccessoryCheckoutState | null {
    if (!this._authService.isAuthenticated()) {
      return null;
    }
    const saved = this._localStorage.getData<AccessoryCheckoutState>(storageKeys.accessoryCheckout);
    if (!saved || !saved.ownerUserId || saved.ownerUserId !== this._authService.getUserId()) {
      return null;
    }
    return saved;
  }

  /** Hoàn tất already recorded a terminal payment result (header may navigate away). */
  private _isResolvedCompleted(saved: AccessoryCheckoutState): boolean {
    const isOnCompleted = saved.currentIndex >= 3 || (saved.currentIndex === 2 && !!saved.paymentResult);
    const hasTerminalResult = saved.paymentResult === 'Succeed' || saved.paymentResult === 'Failed';
    return !!saved.encodedOrderId && isOnCompleted && hasTerminalResult;
  }

  /** Routes the customer may still reach while a pending order locks the checkout (avoids nav loops). */
  private _isAllowedWhileLocked(url: string): boolean {
    return (
      url.includes(routeName.productCheckout) ||
      url.includes(routeName.paymentResult) ||
      url.includes(routeName.login) ||
      url.includes(routeName.logout)
    );
  }

  /**
   * Drop a resolved Hoàn tất flow when leaving checkout, matching the page CTAs: success also
   * clears the cart; failed / mismatch keep the cart (`onBackToCart` / `onConfirmComplete`).
   * Checkout and payment-return URLs are kept so reload / gateway return still show the result.
   */
  private _releaseResolvedCheckoutIfLeaving(url: string): void {
    if (url.includes(routeName.productCheckout) || url.includes(routeName.paymentResult)) {
      return;
    }
    this._releaseResolvedCheckout();
  }

  /** `clear()` a terminal Hoàn tất persist so a later `/san-pham/thanh-toan` starts a new order. */
  private _releaseResolvedCheckout(): void {
    const saved = this._localStorage.getData<AccessoryCheckoutState>(storageKeys.accessoryCheckout);
    if (!saved || !this._isResolvedCompleted(saved)) {
      return;
    }
    if (
      this._authService.isAuthenticated() &&
      saved.ownerUserId &&
      saved.ownerUserId !== this._authService.getUserId()
    ) {
      return;
    }
    // Succeed covers overall success and mismatch-where-payment-succeeded (cart already empty).
    if (saved.paymentResult === 'Succeed') {
      this._cartService.clear();
    }
    this._checkoutService.clear();
  }

  private _applyBootstrapCheckoutRedirect(): void {
    const path = window.location.pathname || '';
    if (this._hasPendingOrder() && !this._isAllowedWhileLocked(path)) {
      this._router.navigateByUrl(`/${routeName.productCheckout}`);
      return;
    }
    // Closed the tab on a finished Hoàn tất then reopened the site elsewhere — don't resume it.
    this._releaseResolvedCheckoutIfLeaving(path);
  }

  appendScript(src: string): void {
    // Dynamically append a <script> tag to the document head.
    const script = document.createElement('script');
    script.type = 'text/javascript';
    script.src = src;
    script.async = true;
    document.head.appendChild(script);
  }
}
