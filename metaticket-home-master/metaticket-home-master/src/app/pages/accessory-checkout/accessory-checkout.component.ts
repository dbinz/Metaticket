import { Component, OnInit, Type } from '@angular/core';
import { NgComponentOutlet, NgIf } from '@angular/common';
import { Router } from '@angular/router';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { NzBreadCrumbModule } from 'ng-zorro-antd/breadcrumb';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { BlockerWarningBannerComponent, LayoutComponent, NavbarComponent } from 'src/app/lib/components';
import { routeName } from 'src/app/lib/constants';
import { AuthService, AccessoryService } from 'src/app/lib/services';
import {
  AccessoryCartQuery,
  AccessoryCartService,
  AccessoryCheckoutQuery,
  AccessoryCheckoutService,
} from 'src/app/lib/stores';
import {
  AccessoryBreadcrumbComponent,
  AccessoryCompletedComponent,
  AccessoryFulfillmentComponent,
  AccessoryInformationComponent,
  AccessoryPaymentComponent,
} from './components';

/**
 * Accessory checkout container. Drives
 * Chọn → Điền thông tin → Thông tin nhận hàng → Thanh toán → Hoàn tất
 * via breadcrumb + `*ngComponentOutlet`.
 */
@Component({
  selector: 'app-accessory-checkout',
  standalone: true,
  imports: [
    NgIf,
    NgComponentOutlet,
    NzDividerComponent,
    NzBreadCrumbModule,
    NzTypographyModule,
    LayoutComponent,
    NavbarComponent,
    AccessoryBreadcrumbComponent,
    BlockerWarningBannerComponent,
  ],
  templateUrl: './accessory-checkout.component.html',
})
export class AccessoryCheckoutComponent implements OnInit {
  currentIndex = 0;

  // Rendered steps on this page ("Chọn" already happened on the product page).
  readonly steps: { title: string; component: Type<unknown> }[] = [
    { title: 'Điền thông tin', component: AccessoryInformationComponent },
    { title: 'Thông tin nhận hàng', component: AccessoryFulfillmentComponent },
    { title: 'Thanh toán', component: AccessoryPaymentComponent },
    { title: 'Hoàn tất', component: AccessoryCompletedComponent },
  ];

  // Full breadcrumb including pre-page "Chọn". Page index is offset by +1.
  readonly breadcrumbSteps: { title: string }[] = [
    { title: 'Chọn' },
    { title: 'Điền thông tin' },
    { title: 'Thông tin nhận hàng' },
    { title: 'Thanh toán' },
    { title: 'Hoàn tất' },
  ];

  constructor(
    private _checkoutService: AccessoryCheckoutService,
    private _checkoutQuery: AccessoryCheckoutQuery,
    private _cartService: AccessoryCartService,
    private _cartQuery: AccessoryCartQuery,
    private _router: Router,
    private _authService: AuthService,
    private _accessoryService: AccessoryService,
  ) {}

  ngOnInit(): void {
    this._cartService.restore();
    this._checkoutService.restore();

    const restored = this._checkoutQuery.getValue();
    if (restored.ownerUserId && restored.ownerUserId !== this._authService.getUserId()) {
      this._checkoutService.clear();
    }

    this._checkoutService.migrateLegacyStepIndices();
    const state = this._checkoutQuery.getValue();

    if (state.currentIndex >= 3 && state.encodedOrderId) {
      // Completed — resume even if cart was cleared.
    } else if (state.currentIndex === 2 && state.encodedPaymentId) {
      // Payment step with an existing order.
    } else if (state.currentIndex === 1 && state.customerName) {
      // Fulfillment draft (customer already entered on step 2).
    } else if (this._cartQuery.getValue().lines.length > 0) {
      this._checkoutService.startAtInformation();
    } else {
      this._router.navigate(['/', routeName.product]);
      return;
    }

    this._checkoutQuery.currentIndex$.subscribe((index) => {
      this.currentIndex = index;
    });

    const hasPlacedOrder = !!(state.encodedOrderId || state.encodedPaymentId);
    if (!hasPlacedOrder) {
      this._ensureShowOnSale();
    }
  }

  /** Bounce back to the show page when it is Waiting/Closed (checkout/discount are rejected server-side too). */
  private _ensureShowOnSale(): void {
    const showId = this._cartQuery.getValue().accessoryShowId;
    if (!showId) {
      return;
    }
    this._accessoryService.getAccessoryShow(showId).subscribe({
      next: (show) => {
        if ((show.status || '').trim() === 'Open') {
          return;
        }
        const pathKey = (show.slug || '').trim() || show.id;
        void this._router.navigate(['/', routeName.productDetail, pathKey]);
      },
    });
  }

  /** Breadcrumb highlight: page step offset by the completed "Chọn" step. */
  get breadcrumbIndex(): number {
    return this.currentIndex + 1;
  }

  /**
   * Jump back to an earlier breadcrumb step. Only reachable while `[locked]` is false (before
   * "Thanh toán", where an order already exists and going back is no longer offered — see the
   * "Hủy đơn" note in accessory-payment.component.html). Reuses the exact navigation each step's
   * own "Quay lại" button already performs, rather than duplicating new logic.
   */
  onSelectBreadcrumbStep(index: number): void {
    if (index === 0) {
      // Same as AccessoryInformationComponent.onGoBack(): leave the page, back to
      // product-detail with the cart open.
      const accessoryShowId = this._cartQuery.getValue().accessoryShowId;
      if (accessoryShowId) {
        this._router.navigate(['/', routeName.productDetail, accessoryShowId], { queryParams: { openCart: 1 } });
      }
      return;
    }
    if (index === 1) {
      // Same as AccessoryFulfillmentComponent.onGoBack().
      this._checkoutService.onPreviousIndex();
    }
  }
}
