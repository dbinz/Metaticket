import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, TimeoutError, timeout } from 'rxjs';
import {
  BlockerWarningBannerComponent,
  ImageComponent,
  LayoutComponent,
  NavbarComponent,
} from 'src/app/lib/components';
import { AsyncPipe, NgComponentOutlet, NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NzBreadCrumbModule } from 'ng-zorro-antd/breadcrumb';
import {
  BookingCalendarComponent,
  BookingCompletedComponent,
  BookingInformationComponent,
  BookingPaymentComponent,
  BookingPolicyDescComponent,
  BookingSeatMapComponent,
} from './components';
import { BookingQuery, BookingService, BookingState } from 'src/app/lib/stores';
import { ActivatedRoute, NavigationExtras } from '@angular/router';
import {
  LocalStorageService,
  NavigationService,
  NotificationService,
  ShowService,
  TicketService,
} from 'src/app/lib/services';
import { SessionResponse, ShowResponse } from 'src/app/lib/entities';
import { EdType } from 'src/app/lib/enums';
import { appIcons } from 'src/app/lib/theme';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { routeName } from 'src/app/lib/constants';
import { SessionStatus } from 'src/app/lib/enums/session-status';
import {
  calendarSessionRangeLabel,
  formatDateKey,
  isCalendarSession,
  isReservationGoneError,
  showDetailPath,
} from 'src/app/lib/utils';

@Component({
  selector: 'app-booking',
  standalone: true,
  imports: [
    LayoutComponent,
    NgTemplateOutlet,
    BlockerWarningBannerComponent,
    NgIf,
    NavbarComponent,
    NzBreadCrumbModule,
    NzTypographyModule,
    NgComponentOutlet,
    ImageComponent,
    NzDropDownModule,
    NgForOf,
    AsyncPipe,
  ],
  templateUrl: './booking.component.html',
})
export class BookingComponent implements OnInit, OnDestroy {
  /** Maximum time to wait for a slug resolution before treating the request as failed. */
  private static readonly slugFetchTimeoutMs = 10_000;

  protected readonly appIcons = appIcons;

  showDetail?: ShowResponse;
  currentIndex = -1;
  isLoading = false;
  error: any;
  showId = '';
  sessionId = '';
  currentSession?: SessionResponse;
  readonly visitDate$: Observable<string | undefined>;
  steps: {
    title: string;
    component:
      | typeof BookingCalendarComponent
      | typeof BookingSeatMapComponent
      | typeof BookingPolicyDescComponent
      | typeof BookingInformationComponent
      | typeof BookingPaymentComponent
      | typeof BookingCompletedComponent;
  }[] = [];

  constructor(
    private _route: ActivatedRoute,
    private _bookingService: BookingService,
    private _localStorageService: LocalStorageService,
    private _showService: ShowService,
    private _ticketService: TicketService,
    private _bookingQuery: BookingQuery,
    private _navigationService: NavigationService,
    private _notificationService: NotificationService,
  ) {
    this.visitDate$ = this._bookingQuery.select('visitDate');
    this._bookingQuery.select('currentIndex').subscribe((index) => {
      this.currentIndex = index;
      this._syncImmersiveChrome();
    });
  }

  ngOnInit(): void {
    const showSlug = this._route.snapshot.params['showSlug'] as string | undefined;
    if (showSlug) {
      // Slug mode: resolve show/session slugs through the Fetch_By_Slug_Endpoint.
      const sessionSlug = this._route.snapshot.params['sessionSlug'] as string | undefined;
      this._resolveBySlug(showSlug, sessionSlug);
      return;
    }

    // Query-param mode: existing behavior, unchanged.
    this._route.queryParamMap.subscribe((queryParams) => {
      this.showId = queryParams.get('show') ?? '';
      this.sessionId = queryParams.get('session') ?? '';
      this._getShowDetail();
    });
  }

  /**
   * Slug mode: normalize the slug path segments, resolve them through the Fetch_By_Slug_Endpoint,
   * assign the resolved identifiers to the existing showId/sessionId fields, then run the SAME
   * _getShowDetail() flow as query-param mode so the booking state is identical.
   *
   * A loading state is shown while pending, with a 10s timeout. A not-found (404)
   * for either the show slug or session slug routes to the not-found page.
   * Any other error or a timeout shows an error state with NO booking page rendered and the requested
   * URL preserved.
   */
  private _resolveBySlug(showSlug: string, sessionSlug?: string): void {
    // The Show_Service normalizes the slug params server-side, so raw path segments are sent as-is.
    this.isLoading = true;
    this.error = undefined;
    this._showService
      .getShowBySlug(showSlug, sessionSlug)
      .pipe(timeout(BookingComponent.slugFetchTimeoutMs))
      .subscribe({
        next: (res) => {
          // Resolve into the existing identifier fields.
          this.showId = res.id;
          this.sessionId = res.matchedSessionId ?? '';
          if (!this.sessionId) {
            // Session-slug miss → not-found, never the booking page.
            this._navigationService.navigateToNotFound();
            return;
          }

          // Run the existing flow so booking state (including non-open session handling) is identical.
          this._getShowDetail();
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 404) {
            // Show-slug or session-slug miss → not-found, never the booking page.
            this._navigationService.navigateToNotFound();
            return;
          }

          // Other error / timeout → error state, booking page not shown, requested URL preserved.
          this._resetState();
          this.error = error instanceof TimeoutError ? 'timeout' : error;
          this.isLoading = false;
          console.log('get show by slug error', error);
        },
      });
  }

  ngOnDestroy(): void {
    document.body.classList.remove('booking-immersive');
  }

  /**
   * Bước chọn ghế chiếm trọn màn hình nên các thành phần nổi toàn cục (contact widget render ở
   * app.component) phải ẩn đi để không che sơ đồ. Dùng class trên body vì widget nằm ngoài cây
   * component của trang đặt vé.
   */
  private _syncImmersiveChrome(): void {
    document.body.classList.toggle('booking-immersive', this.isSeatMapStep);
  }

  /** Clears any previously resolved booking data so an error never leaves the booking page rendered. */
  private _resetState(): void {
    this.showDetail = undefined;
    this.currentSession = undefined;
    this.steps = [];
    this.currentIndex = -1;
  }

  formatDate(timestamp?: number, weekday?: boolean): string {
    if (!timestamp) return '';

    const date = new Date(timestamp);
    return date.toLocaleDateString('vi-VN', {
      weekday: weekday ? 'long' : undefined,
      hour: '2-digit',
      minute: '2-digit',
      day: 'numeric',
      month: 'numeric',
      year: 'numeric',
    });
  }

  /** Header time: the chosen visit date of a calendar session (its date range until one is picked), else start–end. */
  sessionTimeLabel(session?: SessionResponse, visitDate?: string | null): string {
    if (session && isCalendarSession(session)) {
      return visitDate ? `Ngày tham quan: ${formatDateKey(visitDate)}` : calendarSessionRangeLabel(session);
    }
    return `Từ ${this.formatDate(session?.startTime)} đến ${this.formatDate(session?.endTime)}`;
  }

  /** Session-switch menu item time; a calendar session has no single start time. */
  sessionOptionLabel(session: SessionResponse): string {
    return isCalendarSession(session) ? calendarSessionRangeLabel(session) : this.formatDate(session.startTime);
  }

  /** Leaves booking and opens the show detail page (slug URL when the show has one). */
  goToShowDetail(): void {
    if (!this.showDetail) return;
    this._navigationService.navigateByUrl(showDetailPath(this.showDetail));
  }

  onChangeSession(session: SessionResponse): void {
    const extras: NavigationExtras = {
      queryParams: {
        show: this.showId,
        session: session.id,
      },
      queryParamsHandling: 'merge',
    };
    this._navigationService.navigateTo(routeName.booking, extras);
  }

  getOpenSessions(): SessionResponse[] {
    return this.showDetail?.sessions?.filter((session) => session.status === SessionStatus.Open) || [];
  }

  /** Chỉ hiện bộ chọn suất khi show thực sự có nhiều suất để chuyển. */
  hasMultipleSessions(): boolean {
    return this.getOpenSessions().length > 1;
  }

  /**
   * Bước chọn ghế được hiển thị toàn màn hình, nền tối, bỏ header/breadcrumb để khách tập trung vào
   * sơ đồ chỗ ngồi. Bước chọn theo policy descriptor giữ nguyên bố cục cũ.
   */
  get isSeatMapStep(): boolean {
    return this.currentIndex === 0 && !!this.currentSession && this.currentSession.edType !== EdType.PolicyConfig;
  }

  private _getShowDetail(): void {
    this.isLoading = true;
    this._showService.getShowDetail(this.showId).subscribe({
      next: (response) => {
        this.currentSession = response.sessions.find((session) => session.id === this.sessionId);
        if (!this.currentSession) {
          this._navigationService.navigateToNotFound();
          return;
        }
        // Phiên khôi phục từ localStorage đã ở bước chọn ghế trước khi có currentSession, nên phải
        // đồng bộ lại sau khi biết edType của suất.
        this._syncImmersiveChrome();
        this.showDetail = response;

        if (
          [SessionStatus.OpeningWait, SessionStatus.Paused, SessionStatus.Stopped].includes(this.currentSession.status)
        ) {
          const statusMessageMap: Record<SessionStatus, string> = {
            [SessionStatus.OpeningWait]: 'chưa mở bán',
            [SessionStatus.Paused]: 'đang tạm dừng bán vé',
            [SessionStatus.Stopped]: 'đã kết thúc',
            [SessionStatus.Open]: 'đang mở bán',
          };
          this._notificationService.showWarningNotification(
            `Xin bạn thông cảm. Sự kiện ${statusMessageMap[this.currentSession.status]}, bạn hãy kiểm tra lại sau.`,
          );
          // this._navigationService.navigateTo(`/show/${this.showId}`, { replaceUrl: true });
          this.goToShowDetail();
          return;
        }

        this.steps = [
          {
            title: 'Chọn ',
            component: this._selectionStepComponent(this.currentSession),
          },
          {
            title: 'Điền thông tin',
            component: BookingInformationComponent,
          },
          {
            title: 'Thanh toán',
            component: BookingPaymentComponent,
          },
          {
            title: 'Hoàn tất',
            component: BookingCompletedComponent,
          },
        ];

        const initialState: BookingState = {
          currentIndex: 0,
          showId: this.showId,
          showDetail: response,
          requireDelivery: this.currentSession.requireDelivery,
          sessionId: this.sessionId,
          reservationId: '',
          listSelectedTickets: [],
          tentativePrice: {
            priceTotal: 0,
            priceDiscount: 0,
            shipPrice: 0,
          },
          orderId: '',
          promotionCode: '',
          paymentId: '',
          isStartedPayment: false,
          // Explicit so switching from a calendar session does not carry its date over (store.update merges).
          visitDate: undefined,
        };
        const data = isCalendarSession(this.currentSession)
          ? this._latestCalendarBookingSession()
          : this._localStorageService.getBookingSession(this.showId, this.sessionId);
        if (data !== null) {
          this._ticketService.getSingleReservation(data.reservationId).subscribe({
            next: (res) => {
              if (res.expireInS > 0) {
                this._bookingService.initial({
                  ...data,
                  // Keep the customer's progress but not the show saved with it: session settings an admin changed
                  // since (e.g. the lone-seat rule) must apply now, not after the saved booking expires.
                  showDetail: response,
                  // States saved before calendar tickets have no visitDate key; never inherit one from the store.
                  visitDate: data.visitDate,
                  tentativePrice: {
                    ...data.tentativePrice,
                    priceDiscount: data.currentIndex >= 2 ? data.tentativePrice.priceDiscount : 0,
                  },
                });
                this.isLoading = false;
                return;
              }

              this._localStorageService.removeBookingSession(this.showId, this.sessionId, data.visitDate);
              this._bookingService.initial(initialState);
              this.isLoading = false;
            },
            error: (error: unknown) => {
              if (isReservationGoneError(error)) {
                this._localStorageService.removeBookingSession(this.showId, this.sessionId, data.visitDate);
                this._bookingService.initial(initialState);
                this.isLoading = false;
                return;
              }
              // Transient failure (e.g. repeated F5): keep the saved booking so the next load resumes it. Starting over
              // here would leave the customer's own hold locking the seats they are trying to pick again.
              this.error = error;
              this.isLoading = false;
            },
          });
          return;
        }

        this._bookingService.initial(initialState);
        this.isLoading = false;
      },
      error: (error) => {
        this._navigationService.navigateToNotFound();
        this.isLoading = false;
        console.log('get show detail error', error);
      },
    });
  }

  /** Calendar sessions pick a visit date first; the ticket list of that date reuses the policy-config step. */
  private _selectionStepComponent(
    session: SessionResponse,
  ): typeof BookingCalendarComponent | typeof BookingPolicyDescComponent | typeof BookingSeatMapComponent {
    if (isCalendarSession(session)) {
      return BookingCalendarComponent;
    }
    return session.edType === EdType.PolicyConfig ? BookingPolicyDescComponent : BookingSeatMapComponent;
  }

  /**
   * Calendar sessions save one state per visit date and the date is unknown on reload, so resume the most advanced
   * one (two tabs on different dates is the only way to have several).
   */
  private _latestCalendarBookingSession(): BookingState | null {
    const states = this._localStorageService.getCalendarBookingSessions(this.showId, this.sessionId);
    return states.reduce<BookingState | null>(
      (latest, state) => (!latest || state.currentIndex > latest.currentIndex ? state : latest),
      null,
    );
  }
}
