import { NgClass, NgForOf, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { routeName } from 'src/app/lib/constants';
import { newsArticleHref } from 'src/app/lib/constants/news.constants';
import {
  AccessoryShowListPage,
  AccessoryShowResponse,
  GetHighlightShowResponse,
  SearchShowResponse,
} from 'src/app/lib/entities';
import { AccessoryService } from 'src/app/lib/services/accessory/accessory.service';
import { NewsService } from 'src/app/lib/services/news/news.service';
import { ShowService } from 'src/app/lib/services/show/show.service';
import { appImages } from 'src/app/lib/theme';
import { formatDate, minPriceLabel, nearestHomeSession, ongoingCalendarLabel, showDetailPath } from 'src/app/lib/utils';
import {
  ShowcaseArticleCard,
  ShowcaseMerchCard,
  ShowcaseShowBatch,
  ShowcaseShowCard,
  ShowcaseStatus,
} from './home-showcase.models';

const SHOW_PAGE_SIZE = 6;
const MERCH_LIMIT = 6;
const ARTICLE_LIMIT = 6;

@Component({
  selector: 'home-showcase',
  standalone: true,
  imports: [NgForOf, NgIf, NgClass, RouterLink],
  templateUrl: './home-showcase.component.html',
  styleUrls: ['./home-showcase.component.scss'],
})
export class HomeShowcaseComponent implements OnInit {
  protected readonly routeName = routeName;
  protected readonly newsArticleHref = newsArticleHref;

  shows: ShowcaseShowCard[] = [];
  merch: ShowcaseMerchCard[] = [];
  articles: ShowcaseArticleCard[] = [];

  showsLast = true;
  showsLoading = false;
  private _showPage = 0;

  constructor(
    private readonly _showService: ShowService,
    private readonly _accessoryService: AccessoryService,
    private readonly _newsService: NewsService,
  ) {}

  /** First 6 shows: hero = pin 1, two beside = 2–3, three below = 4–6. */
  get firstBatch(): ShowcaseShowBatch | null {
    if (!this.shows.length) {
      return null;
    }
    const chunk = this.shows.slice(0, SHOW_PAGE_SIZE);
    return {
      hero: chunk[0],
      side: chunk.slice(1, 3),
      bottom: chunk.slice(3, 6),
    };
  }

  /** Page 2+ from load-more — same tile cards as merch, no hero. */
  get moreShows(): ShowcaseShowCard[] {
    return this.shows.slice(SHOW_PAGE_SIZE);
  }

  /** Load all three strips independently so one 404 does not block the others. */
  ngOnInit(): void {
    this._loadShows(false);
    this._loadMerch();
    this._loadArticles();
  }

  /** Append the next highlight page — same contract as staging homepage load-more. */
  loadMoreShows(): void {
    if (this.showsLoading || this.showsLast) {
      return;
    }
    this._showPage += 1;
    this._loadShows(true);
  }

  /** Maps sale state to a chip color modifier. */
  chipClass(status: ShowcaseStatus): string {
    return `hs-chip--${status}`;
  }

  /** Stable ngFor identity for showcase cards. */
  trackById(_index: number, item: { id: string }): string {
    return item.id;
  }

  /** Load one highlight page; 404/empty → hide the strip (or stop load-more). */
  private _loadShows(append: boolean): void {
    this.showsLoading = true;
    this._showService
      .getHighlightShow({ page: this._showPage, size: SHOW_PAGE_SIZE, cities: [] })
      .pipe(catchError(() => of(this._emptyShowPage())))
      .subscribe({
        next: (res) => {
          const mapped = (res.content ?? []).map((show) => this._mapShow(show));
          this.shows = append ? [...this.shows, ...mapped] : mapped;
          this.showsLast = res.last === true || (res.totalPages ?? 1) - 1 <= this._showPage || mapped.length === 0;
          this.showsLoading = false;
        },
        error: () => {
          this.showsLoading = false;
          this.showsLast = true;
        },
      });
  }

  /** Pinned accessory shows — missing endpoint becomes an empty strip. */
  private _loadMerch(): void {
    this._accessoryService
      .getAccessoryShows(0, MERCH_LIMIT)
      .pipe(catchError(() => of(this._emptyAccessoryPage())))
      .subscribe({
        next: (res) => {
          this.merch = (res.content ?? []).map((show) => this._mapMerch(show));
        },
      });
  }

  /** Featured pins; 404 / empty → empty blog strip. */
  private _loadArticles(): void {
    this._newsService
      .getFeaturedNews()
      .pipe(catchError(() => of({ content: [], totalElements: 0 })))
      .subscribe((body) => {
        this.articles = (body.content ?? []).slice(0, ARTICLE_LIMIT).map((item) => ({
          id: item.id,
          title: item.title,
          subdesc: item.subdesc || item.summary,
          category: item.categoryName,
          meta: item.displayDate,
          imageUrl: item.thumbnail || appImages.imgMetaTicketThumbnail,
          slug: item.slug,
        }));
      });
  }

  /** Empty listing page so merch catchError can keep the UI up. */
  private _emptyAccessoryPage(): AccessoryShowListPage {
    return {
      content: [],
      last: true,
      totalPages: 0,
      totalElements: 0,
      size: MERCH_LIMIT,
      number: 0,
      first: true,
      empty: true,
      numberOfElements: 0,
    };
  }

  /** Empty Spring page so catchError can keep the UI up. */
  private _emptyShowPage(): GetHighlightShowResponse {
    return {
      content: [],
      last: true,
      totalPages: 0,
      totalElements: 0,
      size: SHOW_PAGE_SIZE,
      number: this._showPage,
      first: true,
      empty: true,
      numberOfElements: 0,
      ranking: 0,
      sessions: [],
      pageable: {
        sort: { empty: true, sorted: false, unsorted: true },
        pageNumber: this._showPage,
        pageSize: SHOW_PAGE_SIZE,
        offset: 0,
        paged: true,
        unpaged: false,
      },
      sort: { empty: true, sorted: false, unsorted: true },
    };
  }

  /** Map a metasearch highlight card onto the homepage tile. */
  private _mapShow(show: SearchShowResponse): ShowcaseShowCard {
    const chip = this._showChip(show);
    return {
      id: show.id,
      title: show.showName,
      category: show.eventCategory || '',
      // Highlight cards carry ongoingUntil instead of sessions; other payloads list sessions.
      dateLabel:
        ongoingCalendarLabel({
          sessionType: 'CALENDAR',
          startTime: show.startTime,
          endTime: show.ongoingUntil ?? undefined,
        }) ||
        ongoingCalendarLabel(nearestHomeSession(show.sessions)) ||
        (show.startTime ? formatDate(show.startTime) : ''),
      venue: show.venue || show.cityName || '',
      status: chip.status,
      statusLabel: chip.label,
      imageUrl: show.bannerImageUrl || show.thumbnailUrl || appImages.imgMetaTicketThumbnail,
      logoShowUrl: show.logoShowUrl || '',
      path: showDetailPath(show),
      priceLabel: minPriceLabel(show.minPrice),
    };
  }

  /** Map an accessory show; the product count stays blank until the API exposes it. */
  private _mapMerch(show: AccessoryShowResponse): ShowcaseMerchCard {
    const pathKey = (show.slug || '').trim() || show.id;
    const chip = this._merchChip(show.status);
    return {
      id: show.id,
      title: show.name,
      category: 'Merch',
      meta: '',
      priceLabel: minPriceLabel(show.minPrice),
      status: chip.status,
      statusLabel: chip.label,
      imageUrl: show.bannerImageUrl || appImages.imgMetaTicketThumbnail,
      path: `/${routeName.productDetail}/${pathKey}`,
      ctaLabel: 'Xem bộ',
    };
  }

  /** Chip for accessory show status on the merch strip. */
  private _merchChip(status?: string): { status: ShowcaseStatus; label: string } {
    switch ((status || '').trim()) {
      case 'Waiting':
        return { status: 'openingWait', label: 'Sắp mở bán' };
      case 'Closed':
        return { status: 'paused', label: 'Đã đóng' };
      default:
        return { status: 'open', label: 'Đang bán' };
    }
  }

  /** Badge only for OpeningWait / Open / Paused; other show statuses hide it. */
  private _showChip(show: SearchShowResponse): { status: ShowcaseStatus | null; label: string } {
    switch ((show.status || '').trim()) {
      case 'OpeningWait':
        return { status: 'openingWait', label: 'Chờ mở bán' };
      case 'Open':
        return { status: 'open', label: 'Đang mở bán' };
      case 'Paused':
        return { status: 'paused', label: 'Tạm dừng bán vé' };
      default:
        return { status: null, label: '' };
    }
  }
}
