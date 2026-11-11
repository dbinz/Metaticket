import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import {
  ButtonComponent,
  ImageComponent,
  InputComponent,
  LoadingComponent,
  PromotionOfferPanelComponent,
  PromotionOfferSource,
} from 'src/app/lib/components';
import { NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { NotificationService, OrderService, TicketService } from 'src/app/lib/services';
import { BookingQuery, BookingService } from 'src/app/lib/stores';
import { BookingTicketItemComponent } from '../booking-ticket-item/booking-ticket-item.component';
import { FormatPricePipe, SafeHtmlPipe } from 'src/app/lib/pipes';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { NzDrawerModule } from 'ng-zorro-antd/drawer';
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
import { SeatPlanTriggerItem } from 'src/app/lib/dto';
import { appIcons } from 'src/app/lib/theme';
import { BookingPolicyDescItemComponent } from '../booking-policy-desc-item/booking-policy-desc-item.component';
import { BreakpointObserver } from '@angular/cdk/layout';
import { TicketReservationStatus } from 'src/app/lib/enums';
import { HttpErrorResponse } from '@angular/common/http';
import {
  PromotionCodeResult,
  escapeHtml,
  serverErrorText,
  describeTicketCouponCheckFailure,
  describeTicketCouponResult,
  truncateToNearestXMinutes,
} from 'src/app/lib/utils';

@Component({
  selector: 'app-booking-policy-desc',
  standalone: true,
  imports: [
    LoadingComponent,
    NgIf,
    NgTemplateOutlet,
    BookingTicketItemComponent,
    ButtonComponent,
    FormatPricePipe,
    SafeHtmlPipe,
    ImageComponent,
    InputComponent,
    NgForOf,
    NzDividerComponent,
    NzDrawerModule,
    NzTypographyComponent,
    BookingPolicyDescItemComponent,
    PromotionOfferPanelComponent,
  ],
  templateUrl: './booking-policy-desc.component.html',
})
export class BookingPolicyDescComponent implements OnInit, OnChanges, OnDestroy {
  protected readonly appIcons = appIcons;
  private readonly _showId: string;
  private readonly _sessionId: string;

  /** Visit date ("YYYY-MM-DD") of a CALENDAR session; its tickets are per date. Unset for normal sessions. */
  @Input() visitDate?: string;

  bottomSheetVisible = false;
  isLoading = false;
  listTickets: TicketResponse[] = [];
  /** Current session from booking store — used for optional HTML extraInfo. */
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
  /** Bumped on every visit date change; a date value alone cannot tell "still A" from "A -> B -> A" (selection reset). */
  private _visitDateGeneration = 0;

  constructor(
    private _ticketService: TicketService,
    private _orderService: OrderService,
    private _notificationService: NotificationService,
    private _bookingQuery: BookingQuery,
    private _bookingService: BookingService,
    private _breakpointObserver: BreakpointObserver,
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

  private _getAllTicket(): void {
    this.isLoading = true;
    const date = this.visitDate;
    const body: GetAllTicketRequest = {
      showId: this._showId,
      sessionId: this._sessionId,
      date,
    };
    this._ticketService.getAllTicket(body).subscribe({
      next: (response) => {
        // Ignore a response for a date the customer has already left.
        if (date !== this.visitDate) {
          return;
        }
        this.listTickets = response.tickets.sort((a, b) => {
          if (a.basePrice < b.basePrice) {
            return -1;
          }
          if (a.basePrice > b.basePrice) {
            return 1;
          }
          return 0;
        });
        this.isLoading = false;
      },
      error: (err) => {
        console.log('get all ticket error', err);
        this.isLoading = false;
      },
    });
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

  onChangeQuantity(quantity: number, ticket: TicketResponse): void {
    this.listSelectedTickets = [
      ...this.listSelectedTickets.filter((item) => item.sid !== ticket.sid),
      {
        id: ticket.id,
        sid: ticket.sid,
        priceCat: ticket.priceCat,
        displayName: ticket.priceCat,
        quantity: quantity,
        basePrice: ticket.basePrice,
        remCap: ticket.remCap,
      },
    ].filter((item) => item.quantity > 0);
    this._tentativeOrderPrice();
  }

  deselectTicket(item: SeatPlanTriggerItem): void {
    this.listSelectedTickets = this.listSelectedTickets.filter((ticket) => ticket.sid !== item.sid);
    this._tentativeOrderPrice();
  }

  createReservation(): void {
    this.createReservationLoading = true;
    // The held seats belong to this date selection; polling outlives a date change, so the selection travels with it.
    const generation = this._visitDateGeneration;
    const details: CreateReservationTicketRequest[] = this.listSelectedTickets.map((item) => ({
      ticketId: item.id,
      quantity: item.quantity,
      priceCat: item.priceCat,
      sid: item.sid,
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
            this.checkReservationStatus(response.reservationId, generation);
          },
          error: (err: HttpErrorResponse) => {
            this.handleReservationFailure(serverErrorText(err) || 'Chọn vé thất bại, vui lòng thử lại sau');
          },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.handleReservationFailure(serverErrorText(err) || 'Chọn vé thất bại, vui lòng thử lại sau');
      },
    });
  }

  /** Polls until the reservation is held; [generation] is the visit date selection it was created for. */
  checkReservationStatus = (reservationId: string, generation: number): void => {
    this._ticketService.getSingleReservation(reservationId).subscribe({
      next: (res) => {
        switch (res.status) {
          case TicketReservationStatus.Reserving:
            setTimeout(() => this.checkReservationStatus(reservationId, generation), 1000);
            break;

          case TicketReservationStatus.Reserved:
            // The date changed while reserving (even if changed back): the selection was reset, so moving on would
            // book the reservation with no tickets and a 0đ total. Free the seats instead.
            if (generation !== this._visitDateGeneration) {
              this._releaseReservation(reservationId);
              this.handleReservationFailure('Bạn đã đổi ngày tham quan, vui lòng chọn lại vé');
              break;
            }
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

  handleReservationFailure = (errorMessage: string): void => {
    this.createReservationLoading = false;
    this._notificationService.showErrorNotification(escapeHtml(errorMessage));
  };

  /** Best effort: if the release call fails the held seats still expire on the server's reservation timeout. */
  private _releaseReservation(reservationId: string): void {
    this._ticketService.releaseReservation(reservationId).subscribe({
      error: (error) => console.error('release reservation error', error),
    });
  }

  private _getTicketByTime(): void {
    const date = this.visitDate;
    const body: GetTicketByTimeRequest = {
      showId: this._showId,
      sessionId: this._sessionId,
      startTime: this._startTime,
      date,
    };
    this._ticketService.getTicketByTime(body).subscribe({
      next: (response) => {
        // Tickets of different dates share the same sid (priceCat), so a late poll for the previous date would
        // overwrite the new date's tickets in the merge below.
        if (date !== this.visitDate) {
          return;
        }
        this.listTickets = [
          ...this.listTickets.filter((ticket) => !response.tickets.some((item) => item.sid === ticket.sid)),
          ...response.tickets.sort((a, b) => {
            if (a.basePrice < b.basePrice) {
              return -1;
            }
            if (a.basePrice > b.basePrice) {
              return 1;
            }
            return 0;
          }),
        ];

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
    this.interval = window.setInterval(() => {
      this._getTicketByTime();
    }, 5 * 1000);
  }

  /**
   * Another visit date has other tickets/prices: drop the selection and reload; the poll picks up the new date.
   * The typed code stays and is re-checked once tickets of the new date are picked.
   */
  ngOnChanges(changes: SimpleChanges): void {
    const visitDateChange = changes['visitDate'];
    if (!visitDateChange || visitDateChange.firstChange) {
      return;
    }
    this._visitDateGeneration++;
    this.listTickets = [];
    this.listSelectedTickets = [];
    this.tentativePrice = { priceTotal: 0, priceDiscount: 0, shipPrice: 0 };
    // A price check still in flight was for the previous date's tickets: void it so it cannot overwrite the reset.
    this._tentativeSeq++;
    this.tentativePriceLoading = false;
    this.couponResult = null;
    this._startTime = 0;
    this._getAllTicket();
  }

  ngOnDestroy(): void {
    if (this.interval) {
      clearInterval(this.interval);
    }
  }
}
