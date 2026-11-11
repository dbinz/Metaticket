import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { catchError, distinctUntilChanged, filter, map, switchMap, takeUntil } from 'rxjs/operators';
import { LayoutComponent, LoadingComponent, NavbarComponent } from 'src/app/lib/components';
import { routeName } from 'src/app/lib/constants';
import { CustomerAccessoryOrderItem } from 'src/app/lib/entities';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { AccessoryService } from 'src/app/lib/services';
import { formatDate } from 'src/app/lib/utils';

/** Maps API fulfillment enum names → Vietnamese labels. */
/* eslint-disable @typescript-eslint/naming-convention -- keys match BE FulfillmentType enum */
const FULFILLMENT_LABELS: Record<string, string> = {
  PickupAtShow: 'Nhận tại show',
  MetaTicketPickup: 'Nhận tại META Ticket',
  Delivery: 'Giao hàng',
};
/* eslint-enable @typescript-eslint/naming-convention */

@Component({
  selector: 'app-order-manager',
  templateUrl: './order-manager.component.html',
  standalone: true,
  imports: [LayoutComponent, NavbarComponent, CommonModule, RouterLink, LoadingComponent, FormatPricePipe],
})
export class OrderManagerComponent implements OnInit, OnDestroy {
  protected readonly routeName = routeName;
  protected readonly formatDate = formatDate;
  protected orders$ = new BehaviorSubject<CustomerAccessoryOrderItem[] | null>(null);
  protected selectedOrder$ = new BehaviorSubject<CustomerAccessoryOrderItem | undefined>(undefined);
  private destroy$ = new Subject<void>();

  constructor(
    private accessoryService: AccessoryService,
    private router: Router,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
  ) {}

  /** Loads paid orders and syncs selection from `orderId` query param. */
  ngOnInit(): void {
    this.setupUrlListener();
    this.loadOrders();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Opens detail view via query param (browser Back-friendly). */
  protected selectOrder(order: CustomerAccessoryOrderItem): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { orderId: order.id },
      queryParamsHandling: 'merge',
    });
  }

  /** Returns to the paid-order list. */
  protected goBackToList(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { orderId: null },
      queryParamsHandling: 'merge',
    });
  }

  /** Human label for fulfillment mode. */
  protected fulfillmentLabel(mode: string): string {
    return FULFILLMENT_LABELS[mode] || mode;
  }

  /** Keeps selectedOrder in sync with `orderId` once the list is loaded. */
  private setupUrlListener(): void {
    this.orders$
      .pipe(
        filter((orders): orders is CustomerAccessoryOrderItem[] => orders !== null),
        switchMap((orders) =>
          this.route.queryParamMap.pipe(
            distinctUntilChanged((prev, curr) => prev.get('orderId') === curr.get('orderId')),
            map((params) => ({ params, orders })),
          ),
        ),
        takeUntil(this.destroy$),
      )
      .subscribe(({ params, orders }) => {
        const urlOrderId = params.get('orderId');
        if (urlOrderId) {
          const found = orders.find((o) => o.id === urlOrderId);
          if (found) {
            this.selectedOrder$.next(found);
          } else {
            this.selectedOrder$.next(undefined);
            this.router.navigate([], {
              relativeTo: this.route,
              queryParams: { orderId: null },
              queryParamsHandling: 'merge',
            });
          }
        } else {
          this.selectedOrder$.next(undefined);
        }
        this.cdr.markForCheck();
      });
  }

  /** Fetches PaymentSucceed accessory orders for the logged-in customer. */
  private loadOrders(): void {
    this.accessoryService
      .getMyAccessoryOrders()
      .pipe(
        catchError((err) => {
          console.error('Failed to load accessory orders', err);
          return of([] as CustomerAccessoryOrderItem[]);
        }),
        takeUntil(this.destroy$),
      )
      .subscribe((orders) => {
        this.orders$.next(orders || []);
      });
  }
}
