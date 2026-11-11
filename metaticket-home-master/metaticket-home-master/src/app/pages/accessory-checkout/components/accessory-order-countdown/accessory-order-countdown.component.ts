import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { NgIf } from '@angular/common';
import { Router } from '@angular/router';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { routeName } from 'src/app/lib/constants';
import { AccessoryService } from 'src/app/lib/services';
import { AccessoryOrderStatus } from 'src/app/lib/entities';
import { AccessoryCartQuery, AccessoryCheckoutService } from 'src/app/lib/stores';

/** Order statuses that mean the payment window closed without a successful payment. */
const FAILURE_STATUSES: readonly AccessoryOrderStatus[] = ['Expired', 'PaymentFailed', 'PaymentCancelled'];

/**
 * Display-only checkout countdown shown on the steps that follow order creation ("Thanh toán" and
 * "Hoàn tất"). It counts down to the order's TTL deadline (`expiresAt`, epoch millis) purely for the
 * customer's information — it does NOT expire the order (the backend TTL sweep does).
 *
 * When the countdown reaches zero it re-checks the authoritative order status from the server. If
 * the order has become terminal-without-payment (`Expired` / `PaymentFailed` / `PaymentCancelled`)
 * it shows a popup letting the customer return to their cart. If the order is still pending (the
 * backend sweep may lag the exact deadline by a few minutes) it keeps re-checking for a bounded
 * window; if the order actually succeeded it simply stops (no popup).
 */
@Component({
  selector: 'accessory-order-countdown',
  standalone: true,
  imports: [NgIf, NzTypographyModule],
  templateUrl: './accessory-order-countdown.component.html',
})
export class AccessoryOrderCountdownComponent implements OnInit, OnChanges, OnDestroy {
  /** Absolute deadline in epoch millis. 0 / falsy => nothing to show. */
  @Input() expiresAt = 0;
  /** Checksum-encoded order id used to re-check the status when the countdown hits zero. */
  @Input() encodedOrderId = '';

  /** Formatted remaining time ("mm:ss") shown in the banner. */
  remainingLabel = '';
  /** Whether the countdown has run out (banner switches to an "expired, checking…" note). */
  expired = false;
  /** Whether the "order expired → return to cart" popup is open. */
  expiredPopupOpen = false;

  /** Poll cadence while re-checking a still-pending order after the deadline. */
  private readonly _recheckIntervalMs = 5 * 1000;
  /** Bounded number of post-deadline re-checks (~2 min) so we never poll forever. */
  private readonly _maxRechecks = 24;
  private _recheckCount = 0;
  private _tickInterval?: ReturnType<typeof setInterval>;
  private _recheckTimeout?: ReturnType<typeof setTimeout>;
  private _stopped = false;

  constructor(
    private _accessoryService: AccessoryService,
    private _cartQuery: AccessoryCartQuery,
    private _checkoutService: AccessoryCheckoutService,
    private _router: Router,
  ) {}

  ngOnInit(): void {
    this._start();
  }

  ngOnChanges(changes: SimpleChanges): void {
    // Restart cleanly if the deadline is (re)assigned after init.
    if (changes['expiresAt'] && !changes['expiresAt'].firstChange) {
      this._start();
    }
  }

  ngOnDestroy(): void {
    this._stopped = true;
    this._clearTimers();
  }

  /** True while there is an active deadline to display. */
  get visible(): boolean {
    return this.expiresAt > 0;
  }

  private _start(): void {
    this._clearTimers();
    this._stopped = false;
    this.expired = false;
    this.expiredPopupOpen = false;
    this._recheckCount = 0;

    if (!this.expiresAt) {
      return;
    }

    this._tick();
    this._tickInterval = setInterval(() => this._tick(), 1000);
  }

  /** Update the label each second; when the deadline passes, switch to the re-check phase once. */
  private _tick(): void {
    if (this._stopped) {
      return;
    }

    const remainingMs = this.expiresAt - Date.now();
    if (remainingMs > 0) {
      this.remainingLabel = this._format(remainingMs);
      return;
    }

    // Deadline reached.
    this.remainingLabel = '00:00';
    if (!this.expired) {
      this.expired = true;
      if (this._tickInterval) {
        clearInterval(this._tickInterval);
        this._tickInterval = undefined;
      }
      this._checkOrderStatus();
    }
  }

  /** Fetch the authoritative order status and react to a terminal-without-payment outcome. */
  private _checkOrderStatus(): void {
    if (this._stopped || !this.encodedOrderId) {
      return;
    }

    this._accessoryService.getAccessoryOrder(this.encodedOrderId).subscribe({
      next: (order) => {
        const status = order.status;
        if (FAILURE_STATUSES.includes(status)) {
          this.expiredPopupOpen = true; // expired / failed / cancelled → let the user go back to cart
          return;
        }
        if (status === 'PaymentSucceed') {
          return; // paid in time → nothing to warn about
        }
        this._scheduleRecheck(); // still pending (sweep may lag) → look again shortly
      },
      error: () => this._scheduleRecheck(),
    });
  }

  /** Re-check a still-pending order after the deadline, up to a bounded number of attempts. */
  private _scheduleRecheck(): void {
    if (this._stopped || this._recheckCount >= this._maxRechecks) {
      return;
    }
    this._recheckCount += 1;
    this._recheckTimeout = setTimeout(() => this._checkOrderStatus(), this._recheckIntervalMs);
  }

  /** Confirm the popup → clear the flow and return to the cart on the show's product page. */
  onBackToCart(): void {
    this.expiredPopupOpen = false;
    const accessoryShowId = this._cartQuery.getValue().accessoryShowId;
    this._checkoutService.clear();
    const navigation = accessoryShowId
      ? this._router.navigate(['/', routeName.productDetail, accessoryShowId], { queryParams: { openCart: 1 } })
      : this._router.navigate(['/', routeName.product]);
    navigation.then(() => setTimeout(() => window.scrollTo(0, 0), 100));
  }

  private _format(ms: number): string {
    const totalSeconds = Math.max(0, Math.floor(ms / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${this._pad(minutes)}:${this._pad(seconds)}`;
  }

  private _pad(value: number): string {
    return value < 10 ? `0${value}` : `${value}`;
  }

  private _clearTimers(): void {
    if (this._tickInterval) {
      clearInterval(this._tickInterval);
      this._tickInterval = undefined;
    }
    if (this._recheckTimeout) {
      clearTimeout(this._recheckTimeout);
      this._recheckTimeout = undefined;
    }
  }
}
