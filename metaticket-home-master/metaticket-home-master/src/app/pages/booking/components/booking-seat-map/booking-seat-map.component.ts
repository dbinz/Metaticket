import { Component, Inject, NgZone, OnDestroy, OnInit, PLATFORM_ID, ViewChild } from '@angular/core';
import {
  ButtonComponent,
  ImageComponent,
  InputComponent,
  LoadingComponent,
  PromotionOfferPanelComponent,
  PromotionOfferSource,
  SeatPlanComponent,
} from 'src/app/lib/components';
import { FormatPricePipe, SafeHtmlPipe } from 'src/app/lib/pipes';
import { NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import {
  CreateReservationRequest,
  CreateReservationTicketRequest,
  GetAllTicketRequest,
  GetTicketByTimeRequest,
  PromotionOfferResponse,
  SessionResponse,
  TentativePriceRequest,
  TentativePriceResponse,
  TentativeTicketRequest,
  TicketResponse,
} from 'src/app/lib/entities';
import { Observable } from 'rxjs';
import { NotificationService, OrderService, ShowService, TicketService } from 'src/app/lib/services';
import { SeatPlanTriggerEvent, SeatPlanTriggerItem } from 'src/app/lib/dto';
import { BookingTicketItemComponent } from '../booking-ticket-item/booking-ticket-item.component';
import { BookingQuery, BookingService } from 'src/app/lib/stores';
import { EdType } from 'src/app/lib/enums';
import { appIcons } from 'src/app/lib/theme';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { BreakpointObserver } from '@angular/cdk/layout';
import { TicketReservationStatus } from 'src/app/lib/enums';
import { HttpErrorResponse } from '@angular/common/http';
import {
  PromotionCodeResult,
  escapeHtml,
  serverErrorText,
  SeatAdjacency,
  buildSeatAdjacencyFromBlocks,
  checkSingleSeatGaps,
  describeTicketCouponCheckFailure,
  describeTicketCouponResult,
  SeatGapDialogContent,
  seatGapDialogContent,
  truncateToNearestXMinutes,
} from 'src/app/lib/utils';

@Component({
  selector: 'booking-seat-map',
  standalone: true,
  imports: [
    ButtonComponent,
    FormatPricePipe,
    SafeHtmlPipe,
    InputComponent,
    LoadingComponent,
    NgForOf,
    NgIf,
    NzDividerComponent,
    NzTypographyComponent,
    SeatPlanComponent,
    NgTemplateOutlet,
    BookingTicketItemComponent,
    ImageComponent,
    NzDrawerModule,
    NzModalModule,
    PromotionOfferPanelComponent,
  ],
  templateUrl: './booking-seat-map.component.html',
})
export class BookingSeatMapComponent implements OnInit, OnDestroy {
  @ViewChild(SeatPlanComponent) seatPlanComponent?: SeatPlanComponent;

  protected readonly appIcons = appIcons;
  private readonly _showId: string;
  private readonly _sessionId: string;

  bottomSheetVisible = false;
  /** Panel "Thông tin bổ sung" phủ lên sơ đồ chỗ ngồi; mặc định đóng để không che chỗ chọn vé. */
  extraInfoOpen = false;
  isLoading = false;
  listTickets: TicketResponse[] = [];
  session?: SessionResponse;
  promotionCode = '';
  promotionOfferSource: PromotionOfferSource;
  couponResult: PromotionCodeResult | null = null;
  private _tentativeSeq = 0;
  listSelectedTickets: SeatPlanTriggerItem[] = [];
  createReservationLoading = false;
  tentativePriceLoading = false;
  tentativePrice: TentativePriceResponse = {
    priceTotal: 0,
    priceDiscount: 0,
    shipPrice: 0,
  };
  interval?: number;
  private _startTime = 0;
  // Latest known state per seat for the lone-seat rule: `listTickets` keeps the first load, the polls only reach the planner.
  private readonly _remCapBySid = new Map<string, number>();
  private readonly _priceCatBySid = new Map<string, string>();
  private _seatAdjacency: SeatAdjacency | null = null;
  /** Content of the open "lone seat" dialog; null = closed. */
  seatGapDialog: SeatGapDialogContent | null = null;

  constructor(
    private _ticketService: TicketService,
    private _orderService: OrderService,
    private _notificationService: NotificationService,
    private _bookingQuery: BookingQuery,
    private _bookingService: BookingService,
    private _zone: NgZone,
    private _breakpointObserver: BreakpointObserver,
    private _showService: ShowService,
    @Inject(PLATFORM_ID) private _platformId: object,
  ) {
    const state = this._bookingQuery.getValue();
    this._showId = state.showId;
    this._sessionId = state.sessionId;
    this.session = state.showDetail?.sessions?.find((item) => item.id === state.sessionId);
    this.promotionOfferSource = (): Observable<PromotionOfferResponse[]> =>
      this._orderService.getPromotionOffers(this._showId, this._sessionId);

    this._breakpointObserver
      .observe([
        // Breakpoints.HandsetPortrait,
        // Breakpoints.HandsetLandscape,
        // Breakpoints.TabletPortrait,
        // Breakpoints.TabletLandscape,
        '(max-width: 768px)',
      ])
      .subscribe((result) => {
        if (!result.matches) {
          this.onCloseBottomSheet();
        }
      });
  }

  onOpenBottomSheet(): void {
    this.bottomSheetVisible = true;
  }

  onCloseBottomSheet(): void {
    this.bottomSheetVisible = false;
  }

  onToggleExtraInfo(): void {
    this.extraInfoOpen = !this.extraInfoOpen;
  }

  private _getAllTicket(): void {
    this.isLoading = true;
    const body: GetAllTicketRequest = {
      showId: this._showId,
      sessionId: this._sessionId,
    };
    this._ticketService.getAllTicket(body).subscribe({
      next: (response) => {
        this.listTickets = response.tickets;
        this._rememberTicketState(response.tickets);
        this._startTime = response.version;
        this.isLoading = false;
      },
      error: (err) => {
        console.log('get all ticket error', err);
        this.isLoading = false;
      },
    });
  }

  seatPlanListener(data: SeatPlanTriggerEvent | null): void {
    if (!data) return;
    this._zone.run(() => {
      if (data.eventType === 'item-selected') {
        const ticket = this.listSelectedTickets.find((item) => item.sid === data.triggerItem!.sid);
        if (ticket) {
          this.listSelectedTickets = [
            ...this.listSelectedTickets.filter((item) => item.sid !== data.triggerItem!.sid),
            { ...ticket, quantity: ticket.quantity + data.triggerItem!.quantity },
          ];
          this._tentativeOrderPrice();
          return;
        }

        this.listSelectedTickets = [...this.listSelectedTickets, data.triggerItem!];
        this._tentativeOrderPrice();
        return;
      }

      if (data.eventType === 'item-deselected') {
        if (data.triggerItem) {
          this.listSelectedTickets = this.listSelectedTickets.filter((ticket) => ticket.sid !== data.triggerItem!.sid);
          this._tentativeOrderPrice();
          return;
        }
        this.listSelectedTickets = this.listSelectedTickets.filter(
          (ticket) => !data.items.some((item) => item.sid === ticket.sid),
        );
        this._tentativeOrderPrice();
      }
    });
  }

  onChangePromotionCode(value: string): void {
    this.promotionCode = value;
    if (this.couponResult) {
      this.couponResult = null;
    }
  }

  /** Code shown as "Đang áp dụng" in the offer panel: only once the price check accepted the code now in the box. */
  get appliedPromotionCode(): string | null {
    return this.couponResult?.ok ? this.promotionCode : null;
  }

  onPickPromotion(code: string): void {
    // Same path as typing: the previous code's result is dropped before the new check starts.
    this.onChangePromotionCode(code);
    this.onApplyPromotion();
  }

  onApplyPromotion(): void {
    this._tentativeOrderPrice();
  }

  onRemovePromotion(): void {
    this.promotionCode = '';
    this.couponResult = null;
    this._tentativeOrderPrice();
  }

  private _tentativeOrderPrice(): void {
    this.tentativePriceLoading = true;
    const currentCode = this.promotionCode.trim();
    const seq = ++this._tentativeSeq;
    const tickets: TentativeTicketRequest[] = this.listSelectedTickets.map((item) => ({
      ticketId: item.id,
      quantity: item.quantity,
      priceCat: item.priceCat,
    }));
    let body: TentativePriceRequest = {
      showId: this._showId,
      sessionId: this._sessionId,
      tickets: tickets,
    };
    if (currentCode.length > 0) {
      body = { ...body, promotionCode: currentCode };
    }
    this._orderService.tentativePrice(body).subscribe({
      next: (response) => {
        if (seq !== this._tentativeSeq) {
          return;
        }
        this.tentativePrice = response;
        this.tentativePriceLoading = false;
        if (this.promotionCode.trim() === currentCode) {
          this.couponResult = describeTicketCouponResult(currentCode, response.promotionDetails);
        }
      },
      error: (error) => {
        if (seq !== this._tentativeSeq) {
          return;
        }
        this.tentativePriceLoading = false;
        if (this.promotionCode.trim() === currentCode) {
          this.couponResult = describeTicketCouponCheckFailure(currentCode);
        }
        console.log('tentative price error', error);
      },
    });
  }

  deselectTicket(item: SeatPlanTriggerItem): void {
    if (this.session?.edType === EdType.PolicyConfig) {
      this._zone.run(() => {
        this.listSelectedTickets = this.listSelectedTickets.filter((ticket) => ticket.sid !== item.sid);
        this._tentativeOrderPrice();
      });
      return;
    }

    this.listSelectedTickets = this.listSelectedTickets.filter((ticket) => ticket.sid !== item.sid);
    this._tentativeOrderPrice();
    this.seatPlanComponent?.deselectTicket(item.sid);
  }

  private _rememberTicketState(tickets: TicketResponse[]): void {
    tickets.forEach((ticket) => {
      this._remCapBySid.set(ticket.sid, ticket.remCap);
      this._priceCatBySid.set(ticket.sid, ticket.priceCat);
    });
  }

  private _singleSeatRuleOn(): boolean {
    return this.session?.edType === EdType.SeatPlanner && this.session.preventSingleSeatGap === true;
  }

  /** Loads the seat blocks of the session's seat plan when the lone-seat rule is on; until they arrive the rule is off. */
  private _loadSeatBlocks(): void {
    const seatPlanId = this.session?.seatPlanId;
    if (!this._singleSeatRuleOn() || !seatPlanId) {
      return;
    }
    this._showService.getSeatBlocks(seatPlanId).subscribe({
      next: (response) => {
        // Blocks saved before the plan was last edited may pair seats that are no longer neighbours.
        this._seatAdjacency = response.stale ? null : buildSeatAdjacencyFromBlocks(response.blocks);
      },
      error: (error: HttpErrorResponse) => {
        // 404 = no reviewed blocks for this plan yet. Either way the soft rule stays off instead of blocking the sale.
        if (error.status !== 404) {
          console.warn('lone-seat rule disabled: seat blocks could not be loaded', error.status);
        }
      },
    });
  }

  /**
   * Refuses the choice (and tells the customer which seats to take instead) when the session turns the lone-seat rule on
   * and the chosen seats would leave a free seat alone. Storefront-only rule: the reservation API does not check it.
   */
  private _refusedForSingleSeatGap(): boolean {
    if (!this._singleSeatRuleOn() || !this._seatAdjacency) {
      return false;
    }
    const check = checkSingleSeatGaps(
      this._seatAdjacency,
      this.listSelectedTickets.map((ticket) => ticket.sid),
      this._remCapBySid,
      this._priceCatBySid,
    );
    if (check.gaps.length === 0) {
      return false;
    }
    // A dialog the customer closes, not a toast: the suggested seats must stay readable while they pick again.
    this.seatGapDialog = seatGapDialogContent(check);
    return true;
  }

  closeSeatGapDialog(): void {
    this.seatGapDialog = null;
  }

  createReservation(): void {
    if (this._refusedForSingleSeatGap()) {
      return;
    }
    if (this.interval) {
      clearInterval(this.interval);
    }
    this.createReservationLoading = true;
    const details: CreateReservationTicketRequest[] = this.listSelectedTickets.map((ticket) => ({
      ticketId: ticket.id,
      quantity: ticket.quantity,
      priceCat: ticket.priceCat,
      sid: ticket.sid,
    }));

    const body: CreateReservationRequest = {
      showId: this._showId,
      sessionId: this._sessionId,
      details: details,
    };

    this._ticketService.createReservation(body).subscribe({
      next: (response) => {
        // Call server to actually reserve the tickets
        this._ticketService.reserveReservation(response.reservationId).subscribe({
          next: () => {
            this.checkReservationStatus(response.reservationId);
          },
          error: (error: HttpErrorResponse) => {
            this.handleReservationFailure(serverErrorText(error) || 'Chọn vé thất bại, vui lòng thử lại sau');
          },
        });
      },
      error: (error: HttpErrorResponse) => {
        this.handleReservationFailure(serverErrorText(error) || 'Chọn vé thất bại, vui lòng thử lại sau');
      },
    });
  }

  checkReservationStatus = (reservationId: string): void => {
    this._ticketService.getSingleReservation(reservationId).subscribe({
      next: (res) => {
        switch (res.status) {
          case TicketReservationStatus.Reserving:
            setTimeout(() => this.checkReservationStatus(reservationId), 2 * 1000);
            break;

          case TicketReservationStatus.Reserved:
            this._bookingService.bookingSeatMap(
              reservationId,
              this.listSelectedTickets,
              this.tentativePrice,
              this.promotionCode,
            );
            this.createReservationLoading = false;
            break;

          default:
            this.handleReservationFailure('Chọn vé thất bại, vui lòng thử lại sau');
            break;
        }
      },
      error: () => {
        this.handleReservationFailure(`Chọn vé thất bại, vui lòng thử lại sau`);
      },
    });
  };

  handleReservationFailure = (errorMessage: string = 'Chọn vé thất bại, vui lòng thử lại sau'): void => {
    this.interval = window.setInterval(() => {
      this._getTicketByTime();
    }, 5 * 1000);
    this.createReservationLoading = false;
    this._notificationService.showErrorNotification(escapeHtml(errorMessage));
  };

  private _getTicketByTime(): void {
    const body: GetTicketByTimeRequest = {
      showId: this._showId,
      sessionId: this._sessionId,
      startTime: this._startTime,
    };
    this._ticketService.getTicketByTime(body).subscribe({
      next: (response) => {
        this._rememberTicketState(response.tickets);
        this.listSelectedTickets.forEach((ticket) => {
          if (response.tickets.some((item) => item.sid === ticket.sid && item.remCap <= 0)) {
            this.listSelectedTickets = this.listSelectedTickets.filter((it) => it.sid !== ticket.sid);
          }
        });
        this.seatPlanComponent?.updateTicketsStatus(response.tickets);

        // first, get the ticket changes since 10s ago.
        // however, to make caching efficient, truncate to 3 minute so that different people will
        // hit the same number and hence query string is not changed very frequently
        this._startTime = truncateToNearestXMinutes(response.version - 10 * 1000, 3);
      },
      error: (error) => {
        console.log('get ticket by time error', error);
      },
    });
  }

  onCheckEnableSubmit(): number {
    let count = 0;
    for (let i = 0; i <= this.listSelectedTickets.length; i++) {
      count = count + (this.listSelectedTickets[i]?.quantity ?? 0);
    }
    return count;
  }

  ngOnInit(): void {
    this._getAllTicket();
    this._loadSeatBlocks();
    this.interval = window.setInterval(() => {
      this._getTicketByTime();
    }, 5 * 1000);
  }

  ngOnDestroy(): void {
    if (this.interval) {
      clearInterval(this.interval);
    }
  }
}
