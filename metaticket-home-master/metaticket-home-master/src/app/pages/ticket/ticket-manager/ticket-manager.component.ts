import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { LayoutComponent, LoadingComponent, NavbarComponent } from 'src/app/lib/components';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { routeName } from 'src/app/lib/constants';
import { SessionResponse, ShowResponse, TicketSlotResponse } from 'src/app/lib/entities';
import { BehaviorSubject, forkJoin, of, Subject } from 'rxjs';
import { catchError, distinctUntilChanged, filter, map, switchMap, takeUntil } from 'rxjs/operators';
import { formatDate, visitDateLabel } from 'src/app/lib/utils';
import { TicketSlotByOrderModel } from './model';
import { ShowService, TicketSlotsService } from 'src/app/lib/services';
import { TicketSlotManagerComponent } from './ticket-slot-manager/ticket-slot-manager.component';

@Component({
  selector: 'app-ticket-manager',
  templateUrl: './ticket-manager.component.html',
  standalone: true,
  imports: [LayoutComponent, NavbarComponent, CommonModule, RouterLink, LoadingComponent, TicketSlotManagerComponent],
})
export class TicketManagerComponent implements OnInit, OnDestroy {
  protected readonly routeName = routeName;
  protected ticketSlotsByOrders = new BehaviorSubject<TicketSlotByOrderModel[]>([]);
  protected selectedOrder = new BehaviorSubject<TicketSlotByOrderModel | undefined>(undefined);
  private destroy$ = new Subject<void>();

  constructor(
    private showService: ShowService,
    private ticketSlotService: TicketSlotsService,
    private router: Router,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    // 1. Thiết lập lắng nghe thay đổi URL sau khi dữ liệu có sẵn
    this.setupUrlListener();
    // 2. Tải danh sách slot vé.
    this.loadTicketSlotsByOrder();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  protected selectOrder(order: TicketSlotByOrderModel): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { orderId: order.orderId },
      queryParamsHandling: 'merge', // 'merge' replaces current history state if only query params change
    });
  }

  protected goBackToOrderList(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { orderId: null }, // Set orderId to null to remove it
      queryParamsHandling: 'merge',
    });
  }

  /**
   * Thiết lập một listener để theo dõi thay đổi của query parameter 'orderId' trong URL.
   * Khi 'orderId' thay đổi (bao gồm cả khi bị xóa bởi nút Back của trình duyệt),
   * nó sẽ cập nhật selectedOrder tương ứng.
   */
  private setupUrlListener(): void {
    // Lắng nghe ticketSlotsByOrders trước, và chỉ tiếp tục khi có dữ liệu
    this.ticketSlotsByOrders
      .pipe(
        filter((orders) => orders.length > 0), // Chỉ xử lý khi có dữ liệu đơn hàng
        // Sau khi có dữ liệu orders, chúng ta switchMap sang queryParamMap
        // để lấy giá trị hiện tại của orderId từ URL.
        // Điều này đảm bảo rằng chúng ta luôn kiểm tra URL sau khi dữ liệu đã sẵn sàng.
        switchMap((orders) =>
          this.route.queryParamMap.pipe(
            distinctUntilChanged((prev, curr) => prev.get('orderId') === curr.get('orderId')), // chỉ phản ứng khi orderId thay đổi
            map((params) => ({ params, orders })), // Kết hợp params và orders
          ),
        ),
        takeUntil(this.destroy$),
      )
      .subscribe(({ params, orders }) => {
        const urlOrderId = params.get('orderId');
        if (urlOrderId) {
          const orderToSelect = orders.find((o) => o.orderId === urlOrderId);
          if (orderToSelect) {
            this.selectedOrder.next(orderToSelect);
            this.cdr.markForCheck();
          } else {
            // Nếu orderId có trong URL nhưng không tìm thấy trong dữ liệu đã tải
            // (ví dụ: dữ liệu cũ hoặc ID không hợp lệ)
            // Xóa lựa chọn và query param để tránh trạng thái không nhất quán.
            this.selectedOrder.next(undefined);
            this.cdr.markForCheck();
            this.router.navigate([], {
              relativeTo: this.route,
              queryParams: { orderId: null },
              queryParamsHandling: 'merge',
            });
          }
        } else {
          // Nếu không có orderId trong URL (ví dụ: người dùng nhấn Back và orderId bị xóa)
          this.selectedOrder.next(undefined);
          this.cdr.markForCheck();
        }
      });
  }

  private loadTicketSlotsByOrder(): void {
    this.ticketSlotService
      .getAllTicketSlots(1, 1000)
      .pipe(
        switchMap((ticketSlots: TicketSlotResponse[]) => {
          if (!ticketSlots || ticketSlots.length === 0) {
            return of([]); // No ticket slots, so no orders to display
          }

          const groupedByOrder = this.groupTicketSlotsByOrderId(ticketSlots);
          // Extract unique, valid show IDs
          const showIds = Array.from(
            new Set(ticketSlots.map((ts) => ts.showId).filter((id) => !!id && id.trim() !== '')),
          );

          if (showIds.length === 0) {
            const resultWithNoShowInfo: TicketSlotByOrderModel[] = [];
            for (const [, slotsInOrder] of groupedByOrder.entries()) {
              if (slotsInOrder.length > 0) {
                const firstSlot = slotsInOrder[0];
                resultWithNoShowInfo.push({
                  showId: firstSlot.showId || 'UNKNOWN_SHOW_ID',
                  sessionId: firstSlot.sessionId || 'UNKNOWN_SESSION_ID',
                  orderId: firstSlot.orderId,
                  orderCode: firstSlot.orderCode,
                  ticketSlots: slotsInOrder,
                  evenName: 'N/A (No Show ID)',
                  eventTime: 0,
                  visitDate: firstSlot.visitDate,
                  city: 'N/A',
                  venus: 'N/A',
                  bannerUrl: '',
                });
              }
            }
            // Sắp xếp theo lastModified giảm dần (mới nhất trước)
            const sortedResult = resultWithNoShowInfo.sort((a, b) => {
              const aLastModified = Math.max(...a.ticketSlots.map((slot) => slot.lastModified));
              const bLastModified = Math.max(...b.ticketSlots.map((slot) => slot.lastModified));
              return bLastModified - aLastModified;
            });
            return of(sortedResult);
          }

          // Fetch show details for all unique showIds
          // Assumes showService.getShowDetail(id) returns Observable<ShowResponse>
          const showDetailsObservables = showIds.map((id) =>
            this.showService.getShowDetail(id).pipe(
              catchError((error) => {
                console.error(`Error fetching show details for showId ${id}:`, error);
                // Return a fallback ShowResponse object to allow forkJoin to complete
                // and to provide some default values.
                return of({
                  id: id, // Keep the id for mapping
                  showName: 'Show Information Unavailable',
                  slug: '',
                  sessions: [],
                  bannerImageUrl: '',
                  code: `ERROR-${id}`,
                  website: '',
                  logoShowUrl: '',
                  status: 'ERROR',
                  shipPrice: 0,
                  createdAt: 0,
                  detailImageUrl: '',
                  thumbnailUrl: '',
                  // Ensure all required fields of ShowResponse are present with default/error values
                } as ShowResponse);
              }),
            ),
          );

          return forkJoin(showDetailsObservables).pipe(
            map((showDetailsArray: ShowResponse[]) => {
              const showDetailsMap = new Map<string, ShowResponse>();
              showDetailsArray.forEach((detail) => {
                if (detail && detail.id) {
                  // Ensure detail is valid and has an id
                  showDetailsMap.set(detail.id, detail);
                }
              });

              const result: TicketSlotByOrderModel[] = [];
              for (const [, slotsInOrder] of groupedByOrder.entries()) {
                if (slotsInOrder.length > 0) {
                  const firstSlot = slotsInOrder[0];
                  const showInfo = showDetailsMap.get(firstSlot.showId);
                  let sessionInfo: SessionResponse | undefined;

                  if (showInfo && showInfo.sessions) {
                    sessionInfo = showInfo.sessions.find((s) => s.id === firstSlot.sessionId);
                  }

                  let cityName = 'N/A';
                  if (sessionInfo?.city) {
                    cityName = sessionInfo.city?.name || 'N/A';
                  }

                  result.push({
                    showId: firstSlot.showId,
                    sessionId: firstSlot.sessionId,
                    orderId: firstSlot.orderId,
                    orderCode: firstSlot.orderCode,
                    ticketSlots: slotsInOrder,
                    evenName: showInfo?.showName || 'N/A',
                    eventTime: sessionInfo?.startTime || 0,
                    visitDate: firstSlot.visitDate,
                    calendar: sessionInfo?.calendar,
                    city: cityName,
                    venus: sessionInfo?.venue || 'N/A',
                    bannerUrl: showInfo?.bannerImageUrl || '',
                  });
                }
              }
              // Sắp xếp theo lastModified giảm dần (mới nhất trước)
              return result.sort((a, b) => {
                const aLastModified = Math.max(...a.ticketSlots.map((slot) => slot.lastModified));
                const bLastModified = Math.max(...b.ticketSlots.map((slot) => slot.lastModified));
                return bLastModified - aLastModified;
              });
            }),
          );
        }),
        catchError((error) => {
          // Catch errors from ticketSlotService.getAllTicketSlots or any unhandled errors in the main pipe
          console.error('Failed to load ticket slots by order:', error);
          return of([]); // Return an empty array for ticketSlotsByOrders in case of a critical error
        }),
        takeUntil(this.destroy$),
      )
      .subscribe({
        next: (data) => {
          this.ticketSlotsByOrders.next(data);
          // setupUrlListener sẽ tự động phản ứng với cập nhật dữ liệu này
          // và orderId hiện tại trong URL.
        },
        error: (err) => {
          console.error('Subscription error for ticketSlotsByOrders:', err);
          // Đảm bảo selectedOrder được xóa nếu tải dữ liệu thất bại
          this.selectedOrder.next(undefined);
        },
      });
  }

  private groupTicketSlotsByOrderId(ticketSlots: TicketSlotResponse[]): Map<string, TicketSlotResponse[]> {
    const grouped = new Map<string, TicketSlotResponse[]>();
    for (const slot of ticketSlots) {
      if (!grouped.has(slot.orderId)) {
        grouped.set(slot.orderId, []);
      }
      grouped.get(slot.orderId)!.push(slot);
    }
    return grouped;
  }

  protected readonly formatDate = formatDate;
  protected readonly visitDateLabel = visitDateLabel;

  public reloadTicketSlots(): void {
    this.loadTicketSlotsByOrder();
  }
}
