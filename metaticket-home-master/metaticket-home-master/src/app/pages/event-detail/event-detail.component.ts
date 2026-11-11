import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationExtras, Router } from '@angular/router';
import { TimeoutError, timeout } from 'rxjs';
import { NzIconDirective } from 'ng-zorro-antd/icon';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import {
  AppDropdownComponent,
  ButtonComponent,
  GridEventTicketComponent,
  ImageComponent,
  LayoutComponent,
  NavbarComponent,
  SeatPlanComponent,
} from 'src/app/lib/components';
import { routeName } from 'src/app/lib/constants';
import { HighlightShowRequest, SearchShowResponse, SessionResponse, ShowResponse } from 'src/app/lib/entities';
import { NavigationService, ShowService } from 'src/app/lib/services';
import { appIcons } from 'src/app/lib/theme';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { DropdownMenuItem } from 'src/app/lib/dto';
import { EdType } from 'src/app/lib/enums';
import { SessionStatus, SessionStatusName } from 'src/app/lib/enums/session-status';
import { calendarSessionRangeLabel, isCalendarSession } from 'src/app/lib/utils';

/**
 * Pure descriptor of where the `Đặt vé` button should navigate.
 * - `slug`: the readable `/s/{show-slug}/{session-slug}` route used when both slugs are present.
 * - `query`: the existing `/dat-ve?show=&session=` query-parameter route used otherwise.
 * Either target identifies the same show/session.
 */
export type BookingTarget =
  | { kind: 'slug'; commands: string[] }
  | { kind: 'query'; route: string; queryParams: { show: string; session: string } };

@Component({
  selector: 'app-event-detail',
  templateUrl: './event-detail.component.html',
  standalone: true,
  imports: [
    LayoutComponent,
    NavbarComponent,
    ButtonComponent,
    CommonModule,
    GridEventTicketComponent,
    ImageComponent,
    NzIconDirective,
    NzModalModule,
    NzTypographyComponent,
    SeatPlanComponent,
    AppDropdownComponent,
  ],
})
export class EventDetailComponent implements OnInit {
  /** Maximum time to wait for a slug resolution before treating the request as failed. */
  private static readonly slugFetchTimeoutMs = 10_000;

  show?: ShowResponse;
  sessions: SessionResponse[] = [];
  displaySessions: SessionResponse[] = [];
  nearestSession?: SessionResponse;
  isLoading = false;
  error: any;
  id!: string;
  modalVisible = false;
  recentShows: SearchShowResponse[] = [];
  highlightShows: SearchShowResponse[] = [];
  isLastHighLight = false;
  pageHighLight = 0;
  listSessionSeatPlan: DropdownMenuItem[] = [];
  uniqueSeatPlanId: string[] = [];
  sessionSelected?: DropdownMenuItem | null;

  constructor(
    private _route: ActivatedRoute,
    private _navigationService: NavigationService,
    private _showService: ShowService,
    private _sanitized: DomSanitizer,
    private _router: Router,
  ) {}

  handlerOnPressed(): void {}

  onViewMore(): void {}

  ngOnInit(): void {
    this._route.params.subscribe((params) => {
      const showSlug = params['showSlug'] as string | undefined;
      if (showSlug) {
        // Slug mode: resolve the show slug through the Fetch_By_Slug_Endpoint.
        this.fetchShowBySlug(showSlug);
        return;
      }

      // Id mode: existing behavior, unchanged.
      this.id = params['id'] as string;
      this.fetchShow(this.id);
    });
  }

  /** Time shown for a session: a calendar session spans many dates, so it shows its date range + opening hours. */
  sessionTimeLabel(session: SessionResponse, weekday?: boolean): string {
    return isCalendarSession(session)
      ? calendarSessionRangeLabel(session)
      : this.formatDate(session.startTime, weekday);
  }

  formatDate(timestamp: number, weekday?: boolean): string {
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

  fetchShow(id: string): void {
    this.isLoading = true;
    this._showService.getShowDetail(id).subscribe({
      next: (res) => {
        this._populateShow(res);
      },
      error: (error) => {
        this._navigationService.navigateToNotFound();
        console.log('error', error);
      },
    });
  }

  /**
   * Slug mode: normalize the show-slug path segment, resolve it through the Fetch_By_Slug_Endpoint,
   * and drive the SAME rendering path as id mode once the show resolves.
   *
   * A loading state is shown while pending, with a 10s timeout. A not-found (404)
   * result routes to the not-found page; any other error or a timeout shows an
   * error state with NO partial render, leaving id-based routes reachable.
   */
  fetchShowBySlug(showSlug: string): void {
    // The Show_Service normalizes the slug param server-side, so the raw path segment is sent as-is.
    this.isLoading = true;
    this.error = undefined;
    this._showService
      .getShowBySlug(showSlug)
      .pipe(timeout(EventDetailComponent.slugFetchTimeoutMs))
      .subscribe({
        next: (res) => {
          this.id = res.id;
          this._populateShow(res);
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 404) {
            // Not-found: show the not-found page, never the show detail page.
            this._navigationService.navigateToNotFound();
            return;
          }

          // Other error / timeout: error state with no partial render.
          this._resetShowState();
          this.error = error instanceof TimeoutError ? 'timeout' : error;
          this.isLoading = false;
          console.log('get show by slug error', error);
        },
      });
  }

  /** Clears any previously resolved show data so an error never leaves a partial render. */
  private _resetShowState(): void {
    this.show = undefined;
    this.sessions = [];
    this.displaySessions = [];
    this.nearestSession = undefined;
    this.listSessionSeatPlan = [];
    this.uniqueSeatPlanId = [];
    this.sessionSelected = null;
  }

  /** Shared rendering path for both id mode and slug mode. */
  private _populateShow(res: ShowResponse): void {
    this.show = res;
    const sessions = res.sessions.filter(
      (session) =>
        session.status == SessionStatus.Open ||
        session.status == SessionStatus.OpeningWait ||
        session.status == SessionStatus.Paused ||
        session.status == SessionStatus.Stopped,
    );
    this.sessions = sessions;
    this.displaySessions = this._dedupeDisplaySessions(sessions);
    this.nearestSession = this._pickNearestSession(sessions, Date.now());

    sessions.forEach((session) => {
      // Check Các session không có sơ đồ
      if (session.edType == EdType.SeatPlanner && session.seatPlanId) {
        this.listSessionSeatPlan.push({
          title: session.sessionName || '',
          value: session.seatPlanId,
          edType: session.edType || '',
          legendImageUrl: session.ledgendImageUrl || '',
        });
      }
    });

    // Check Các sessionPlanId unique
    this.uniqueSeatPlanId = this.listSessionSeatPlan
      .map((item) => item.value)
      .filter((value, index, self) => self.indexOf(value) === index);
    this.sessionSelected =
      this.listSessionSeatPlan.length && this.uniqueSeatPlanId.length == 1 ? this.listSessionSeatPlan[0] : null;

    const body: HighlightShowRequest = {
      page: this.pageHighLight,
      size: 12,
      cities: [],
    };
    this._showService.getHighlightShow(body).subscribe((res) => {
      this.highlightShows = res.content;
      this.isLastHighLight = res.last;
    });
    this.recentShows = this._showService.getShowsFromLocalStorage().slice(0, 6);
    this.isLoading = false;
  }

  /**
   * Suất sắp diễn ra sớm nhất (`startTime >= now`); suất theo lịch còn bán (`endTime >= now`) được coi là đang diễn ra
   * với mốc là "bây giờ". Không còn suất nào sắp tới thì lấy suất muộn nhất (show đã qua).
   */
  private _pickNearestSession(sessions: SessionResponse[], now: number): SessionResponse | undefined {
    const effectiveStart = (session: SessionResponse): number =>
      isCalendarSession(session) ? Math.max(session.startTime, now) : session.startTime;
    const isUpcoming = (session: SessionResponse): boolean =>
      isCalendarSession(session) ? session.endTime >= now : session.startTime >= now;

    const upcoming = sessions.filter(isUpcoming);
    if (upcoming.length) {
      return upcoming.reduce((nearest, session) =>
        effectiveStart(session) < effectiveStart(nearest) ? session : nearest,
      );
    }
    return sessions.reduce<SessionResponse | undefined>(
      (latest, session) => (!latest || session.startTime > latest.startTime ? session : latest),
      undefined,
    );
  }

  /** Bỏ các suất trùng dòng hiển thị (cùng thành phố + cùng thời điểm) ở phần đầu trang. */
  private _dedupeDisplaySessions(sessions: SessionResponse[]): SessionResponse[] {
    const result: SessionResponse[] = [];
    const seen = new Set<string>();

    for (const item of sessions) {
      // Calendar sessions key on their date range, so they never collapse into a single session starting the same day.
      const key = `${item.city?.name} - ${this.sessionTimeLabel(item, false)}`;

      if (!seen.has(key)) {
        seen.add(key);
        result.push(item);
      }
    }

    return result;
  }

  onOpenModal(): void {
    this.modalVisible = true;
  }

  onCloseModal(): void {
    this.modalVisible = false;
  }

  handlerOnBookingTicket(sessionId: string): void {
    // Resolve the SessionResponse for the activated button so its slug (if any) can be used.
    const session = this.sessions.find((s) => s.id === sessionId);

    if (this.show && session) {
      const target = this.buildBookingTarget(this.show, session);
      if (target.kind === 'slug') {
        // Slug route: /s/{show-slug}/{session-slug} using canonical slugs from the fetch response.
        this._router.navigate(target.commands).then(() => {
          setTimeout(() => window.scrollTo(0, 0), 100);
        });
        return;
      }
      // Query route: existing /dat-ve?show=&session= behavior.
      this._navigationService.navigateTo(target.route, { queryParams: target.queryParams });
      return;
    }

    // Fallback when the session/show is not resolvable: keep the existing query-param behavior.
    const extras: NavigationExtras = {
      queryParams: {
        show: this.id,
        session: sessionId,
      },
    };
    this._navigationService.navigateTo(routeName.booking, extras);
  }

  /**
   * Pure function selecting the booking navigation target. It has no side effects:
   * it returns a descriptor that {@link handlerOnBookingTicket} uses to navigate.
   *
   * When BOTH the Show_Slug and the Session_Slug are non-empty it returns the slug route
   * `['/', 's', show.slug, session.slug]`; otherwise it returns the existing
   * `/dat-ve?show=&session=` query route using the show/session ids. Either
   * target identifies the same show and session.
   */
  buildBookingTarget(show: ShowResponse, session: SessionResponse): BookingTarget {
    if (show.slug && session.slug) {
      return { kind: 'slug', commands: ['/', routeName.slugPrefix, show.slug, session.slug] };
    }
    return {
      kind: 'query',
      route: routeName.booking,
      queryParams: { show: show.id, session: session.id },
    };
  }

  scrollIntoView(id: string): void {
    const element = document.getElementById(id);
    if (element) {
      element.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
        inline: 'nearest',
      });
    }
  }

  onNavigateToFaq(): void {
    this._navigationService.navigateTo(routeName.faq);
  }

  onViewMoreHighlight(): void {
    const body: HighlightShowRequest = {
      page: this.pageHighLight + 1,
      size: 12,
      cities: [],
    };
    this._showService.getHighlightShow(body).subscribe((res) => {
      this.highlightShows = [...this.highlightShows, ...res.content];
      this.isLastHighLight = res.last;
      this.pageHighLight = res.number;
    });
  }

  /** Renders show HTML as-is; only converts newlines to <br> for plain text (legacy). */
  formatDescription(des: string): SafeHtml {
    if (!des) {
      return this._sanitized.bypassSecurityTrustHtml('');
    }
    // Pretty-printed HTML has newlines between tags — do not turn those into <br>.
    const html = /<[a-z][\s\S]*>/i.test(des) ? des : des.replace(/\n/g, '<br/>');
    return this._sanitized.bypassSecurityTrustHtml(html);
  }

  onSelectSession(data: DropdownMenuItem): void {
    this.sessionSelected = data;
    this.onOpenModal();
  }

  protected readonly appIcons = appIcons;
  protected readonly sessionStatus = SessionStatus;
  protected readonly sessionStatusName = SessionStatusName;
  protected readonly isCalendarSession = isCalendarSession;
}
