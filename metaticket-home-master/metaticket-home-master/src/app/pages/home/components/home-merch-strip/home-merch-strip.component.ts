import { NgForOf, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { routeName } from 'src/app/lib/constants';
import {
  AccessoryShowListPage,
  AccessoryShowResponse,
  GetHighlightShowResponse,
  SearchShowResponse,
} from 'src/app/lib/entities';
import { AccessoryService } from 'src/app/lib/services/accessory/accessory.service';
import { ShowService } from 'src/app/lib/services/show/show.service';
import { showDetailPath } from 'src/app/lib/utils';

/** One clickable frame in the infinite merch ribbon. */
interface MerchStripTile {
  uid: string;
  id: string;
  title: string;
  imageUrl: string;
  path: string;
  kind: 'show' | 'merch' | 'product';
  /** Tried in order when `imageUrl` 404s (show tiles: thumbnail, then logo). */
  fallbackUrls: string[];
}

const MERCH_SHOW_LIMIT = 8;
const DETAIL_FETCH_LIMIT = 4;
const MIN_LOOP_TILES = 8;
const SHOW_BANNER_PAGE_SIZE = 6;

@Component({
  selector: 'home-merch-strip',
  standalone: true,
  imports: [NgForOf, NgIf, RouterLink],
  templateUrl: './home-merch-strip.component.html',
  styleUrls: ['./home-merch-strip.component.scss'],
})
export class HomeMerchStripComponent implements OnInit {
  protected readonly routeName = routeName;

  loopWide: MerchStripTile[] = [];
  loopSquare: MerchStripTile[] = [];

  constructor(
    private readonly _showService: ShowService,
    private readonly _accessoryService: AccessoryService,
  ) {}

  /** Hide the ribbon until there is something worth advertising. */
  get visible(): boolean {
    return this.loopWide.length > 0 || this.loopSquare.length > 0;
  }

  /** Load show art + merch/product photos independently of the showcase grids. */
  ngOnInit(): void {
    forkJoin({
      showsPage0: this._highlightPage(0),
      showsPage1: this._highlightPage(1),
      merch: this._accessoryService
        .getAccessoryShows(0, MERCH_SHOW_LIMIT)
        .pipe(catchError(() => of(this._emptyMerchPage()))),
    })
      .pipe(
        switchMap(({ showsPage0, showsPage1, merch }) => {
          const summaries = merch.content ?? [];
          const bannerShows = this._uniqueShows([...(showsPage0.content ?? []), ...(showsPage1.content ?? [])]);
          if (!summaries.length) {
            return of({ bannerShows, summaries, details: [] as AccessoryShowResponse[] });
          }
          const detailCalls = summaries
            .slice(0, DETAIL_FETCH_LIMIT)
            .map((summary) => this._accessoryService.getAccessoryShow(summary.id).pipe(catchError(() => of(summary))));
          return forkJoin(detailCalls).pipe(map((details) => ({ bannerShows, summaries, details })));
        }),
      )
      .subscribe(({ bannerShows, summaries, details }) => {
        this._buildTracks(bannerShows, summaries, details);
      });
  }

  /** Stable identity for duplicated marquee frames. */
  trackByUid(_index: number, tile: MerchStripTile): string {
    return tile.uid;
  }

  /** Interleave show posters with merch banners / product shots, then duplicate for a seamless loop. */
  private _buildTracks(
    bannerShows: SearchShowResponse[],
    summaries: AccessoryShowResponse[],
    details: AccessoryShowResponse[],
  ): void {
    const showTiles = this._showTiles(bannerShows);
    const merchTiles = this._merchBannerTiles(summaries);
    const productTiles = this._productTiles(details);

    if (!merchTiles.length && !productTiles.length) {
      this.loopWide = [];
      this.loopSquare = [];
      return;
    }

    const wideSource = this._pad(this._uniqueByPath(this._interleave(merchTiles, showTiles)));
    const squareSource = this._pad(
      productTiles.length ? productTiles : this._interleave(merchTiles, showTiles).reverse(),
    );

    this.loopWide = this._duplicate(wideSource, 'w');
    this.loopSquare = this._duplicate(squareSource, 's');
  }

  /** One landscape frame per show — `bannerImageUrl` only; thumbnail/logo wait for img error. */
  private _showTiles(shows: SearchShowResponse[]): MerchStripTile[] {
    const tiles: MerchStripTile[] = [];
    const usedPaths = new Set<string>();
    for (const show of shows) {
      const banner = (show.bannerImageUrl || '').trim();
      if (!banner) {
        continue;
      }
      const path = showDetailPath(show);
      if (usedPaths.has(path)) {
        continue;
      }
      usedPaths.add(path);
      tiles.push({
        uid: '',
        id: show.id,
        title: show.showName,
        imageUrl: banner,
        path,
        kind: 'show',
        fallbackUrls: this._showFallbacks(banner, show),
      });
    }
    return tiles;
  }

  /** Thumbnail then logo, skipping blanks and URLs already used as the primary banner. */
  private _showFallbacks(banner: string, show: SearchShowResponse): string[] {
    const fallbacks: string[] = [];
    const seen = new Set<string>([banner]);
    for (const raw of [show.thumbnailUrl, show.logoShowUrl]) {
      const url = (raw || '').trim();
      if (!url || seen.has(url)) {
        continue;
      }
      seen.add(url);
      fallbacks.push(url);
    }
    return fallbacks;
  }

  /** Swap a broken show banner for thumbnail, then logo. */
  onWideImageError(tile: MerchStripTile): void {
    const next = tile.fallbackUrls.shift();
    if (next) {
      tile.imageUrl = next;
    }
  }

  /** Accessory-show banners — the merch collection covers. */
  private _merchBannerTiles(shows: AccessoryShowResponse[]): MerchStripTile[] {
    const tiles: MerchStripTile[] = [];
    for (const show of shows) {
      const imageUrl = (show.bannerImageUrl || '').trim();
      if (!imageUrl) {
        continue;
      }
      tiles.push({
        uid: '',
        id: `${show.id}-cover`,
        title: show.name,
        imageUrl,
        path: this._merchPath(show),
        kind: 'merch',
        fallbackUrls: [],
      });
    }
    return tiles;
  }

  /** Product / variant photos from accessory-show detail. */
  private _productTiles(shows: AccessoryShowResponse[]): MerchStripTile[] {
    const tiles: MerchStripTile[] = [];
    const seen = new Set<string>();
    for (const show of shows) {
      const path = this._merchPath(show);
      for (const accessory of show.accessories ?? []) {
        const urls = [
          accessory.imageUrl,
          ...(accessory.imageUrls ?? []),
          ...(accessory.variants ?? []).map((v) => v.imageUrl),
        ];
        for (const raw of urls) {
          const imageUrl = (raw || '').trim();
          if (!imageUrl || seen.has(imageUrl)) {
            continue;
          }
          seen.add(imageUrl);
          tiles.push({
            uid: '',
            id: `${accessory.id}-${tiles.length}`,
            title: accessory.name,
            imageUrl,
            path,
            kind: 'product',
            fallbackUrls: [],
          });
          if (tiles.length >= 16) {
            return tiles;
          }
        }
      }
    }
    return tiles;
  }

  /** `/san-pham/{slug|id}` for an accessory show. */
  private _merchPath(show: AccessoryShowResponse): string {
    const key = (show.slug || '').trim() || show.id;
    return `/${routeName.productDetail}/${key}`;
  }

  /** Highlight listing page (0-based); size 6 so page 0+1 is 12 banners. */
  private _highlightPage(page: number): Observable<GetHighlightShowResponse> {
    return this._showService
      .getHighlightShow({ page, size: SHOW_BANNER_PAGE_SIZE, cities: [] })
      .pipe(catchError(() => of(this._emptyShowPage())));
  }

  /** Keep first occurrence of each show id across highlight pages. */
  private _uniqueShows(shows: SearchShowResponse[]): SearchShowResponse[] {
    const seen = new Set<string>();
    const out: SearchShowResponse[] = [];
    for (const show of shows) {
      if (!show?.id || seen.has(show.id)) {
        continue;
      }
      seen.add(show.id);
      out.push(show);
    }
    return out;
  }

  /** One tile per destination URL so banner/logo never double-link the same show. */
  private _uniqueByPath(tiles: MerchStripTile[]): MerchStripTile[] {
    const seen = new Set<string>();
    const out: MerchStripTile[] = [];
    for (const tile of tiles) {
      if (seen.has(tile.path)) {
        continue;
      }
      seen.add(tile.path);
      out.push(tile);
    }
    return out;
  }

  /** Alternate two lists so show art and merch sit side by side. */
  private _interleave(primary: MerchStripTile[], secondary: MerchStripTile[]): MerchStripTile[] {
    const out: MerchStripTile[] = [];
    const max = Math.max(primary.length, secondary.length);
    for (let i = 0; i < max; i++) {
      if (primary[i]) {
        out.push(primary[i]);
      }
      if (secondary[i]) {
        out.push(secondary[i]);
      }
    }
    return out;
  }

  /** Repeat the source until the ribbon is long enough to loop without a gap. */
  private _pad(tiles: MerchStripTile[]): MerchStripTile[] {
    if (!tiles.length) {
      return [];
    }
    const out = [...tiles];
    let n = 0;
    while (out.length < MIN_LOOP_TILES) {
      const src = tiles[n % tiles.length];
      out.push({ ...src, id: `${src.id}-r${n}` });
      n += 1;
    }
    return out;
  }

  /** Clone the track so CSS can translate -50% forever. */
  private _duplicate(tiles: MerchStripTile[], prefix: string): MerchStripTile[] {
    return [...tiles, ...tiles].map((tile, index) => ({
      ...tile,
      uid: `${prefix}-${index}-${tile.id}`,
      fallbackUrls: [...tile.fallbackUrls],
    }));
  }

  /** Empty merch page so a listing 404 still yields a show-only ribbon skip. */
  private _emptyMerchPage(): AccessoryShowListPage {
    return {
      content: [],
      last: true,
      totalPages: 0,
      totalElements: 0,
      size: MERCH_SHOW_LIMIT,
      number: 0,
      first: true,
      empty: true,
      numberOfElements: 0,
    };
  }

  /** Empty banner page so a show API miss does not block merch photos. */
  private _emptyShowPage(): GetHighlightShowResponse {
    return {
      content: [],
      last: true,
      totalPages: 0,
      totalElements: 0,
      size: 0,
      number: 0,
      first: true,
      empty: true,
      numberOfElements: 0,
      ranking: 0,
      sessions: [],
      pageable: {
        sort: { empty: true, sorted: false, unsorted: true },
        pageNumber: 0,
        pageSize: 0,
        offset: 0,
        paged: true,
        unpaged: false,
      },
      sort: { empty: true, sorted: false, unsorted: true },
    };
  }
}
