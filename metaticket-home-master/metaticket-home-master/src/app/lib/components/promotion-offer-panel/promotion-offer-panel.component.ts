import { NgFor, NgIf } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
} from '@angular/core';
import { Observable, Subscription, catchError, defer, map, of, shareReplay, timeout } from 'rxjs';
import { PromotionOfferResponse } from 'src/app/lib/entities';
import {
  PromotionOfferDescription,
  describePromotionOffer,
  normalizePromotionCode,
  sanitizePromotionOffers,
} from 'src/app/lib/utils';

/** Loads the public codes of the current show/session; called lazily, at most once per function reference. */
export type PromotionOfferSource = () => Observable<PromotionOfferResponse[]>;

type PanelStatus = 'idle' | 'loading' | 'ready' | 'unavailable';

interface PromotionOfferView {
  offer: PromotionOfferResponse;
  code: string;
  key: string;
  title: string | null;
  description: PromotionOfferDescription;
}

const OFFER_REQUEST_TIMEOUT_MS = 10000;

// One request per source function, shared by every panel bound to it: a page renders the code box in several layouts
// (desktop sidebar, mobile bar, bottom sheet) and re-creates some of them, which must not each hit the API.
// WeakMap so a source the page dropped (show/session changed) is garbage-collected with its result.
const sharedOfferRequests = new WeakMap<PromotionOfferSource, Observable<PromotionOfferResponse[]>>();

function sharedOffers(source: PromotionOfferSource): Observable<PromotionOfferResponse[]> {
  let request = sharedOfferRequests.get(source);
  if (!request) {
    request = defer(source).pipe(
      timeout(OFFER_REQUEST_TIMEOUT_MS),
      map((raw: unknown) => sanitizePromotionOffers(raw)),
      // The list is optional: 401/403/404/5xx/timeout all just hide it and checkout keeps working.
      catchError(() => of([])),
      // refCount: an in-flight request is cancelled when its last panel is destroyed; a completed one stays replayed.
      shareReplay({ bufferSize: 1, refCount: true }),
    );
    sharedOfferRequests.set(source, request);
  }
  return request;
}

function toView(offer: PromotionOfferResponse): PromotionOfferView {
  const code = offer.code.trim();
  const key = normalizePromotionCode(code);
  const title = typeof offer.title === 'string' ? offer.title.trim() : '';
  // Accessory offers use the code as title; printing it twice adds nothing.
  const hasOwnTitle = title !== '' && normalizePromotionCode(title) !== key;
  return { offer, code, key, title: hasOwnTitle ? title : null, description: describePromotionOffer(offer) };
}

/**
 * Public promotion codes shown right under a code input (ticket booking and accessory cart). Closed by default; the
 * parent calls `open()` when its code input gets focus, and the built-in "Xem mã ưu đãi" link toggles it. Nothing is
 * fetched until the first open, and when there is nothing to offer (empty list or any error) the link disappears.
 */
@Component({
  selector: 'app-promotion-offer-panel',
  standalone: true,
  imports: [NgIf, NgFor],
  templateUrl: './promotion-offer-panel.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PromotionOfferPanelComponent implements OnChanges, OnDestroy {
  /** Keep the same reference while the show/session (or cart show) is unchanged: a new reference drops the cache. */
  @Input() source: PromotionOfferSource | null = null;
  /** Codes stay readable but cannot be picked, e.g. before any ticket is selected. */
  @Input() disabled = false;
  /** Code currently applied to the order; its card shows "Đang áp dụng" instead of a pick button. */
  @Input() appliedCode: string | null = null;
  /**
   * Element wrapping the code input. Presses inside it do not count as "outside": otherwise the click that focuses
   * the input (and so calls `open()`) would close the panel straight away.
   */
  @Input() anchor: HTMLElement | null = null;
  @Output() picked = new EventEmitter<string>();

  protected status: PanelStatus = 'idle';
  protected isOpen = false;
  protected views: PromotionOfferView[] = [];
  protected appliedKey = '';

  private _subscription?: Subscription;

  constructor(
    private _host: ElementRef<HTMLElement>,
    private _cdr: ChangeDetectorRef,
  ) {}

  get isLinkVisible(): boolean {
    return this.source !== null && this.status !== 'unavailable';
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['appliedCode']) {
      this.appliedKey = normalizePromotionCode(this.appliedCode);
    }
    const sourceChange = changes['source'];
    if (sourceChange && !sourceChange.firstChange) {
      this._reset();
    }
  }

  ngOnDestroy(): void {
    this._subscription?.unsubscribe();
  }

  /** Shows the list; the first call for a source starts the fetch. No-op when there is nothing to offer. */
  open(): void {
    const source = this.source;
    if (!source || this.isOpen || this.status === 'unavailable') {
      return;
    }
    this.isOpen = true;
    if (this.status === 'idle') {
      this._load(source);
    }
    // Called from the parent's template/handlers, which does not mark this OnPush view dirty.
    this._cdr.markForCheck();
  }

  close(): void {
    if (!this.isOpen) {
      return;
    }
    this.isOpen = false;
    this._cdr.markForCheck();
  }

  /** The listed offer for a code (case/space-insensitive) once the list is loaded, to explain a rejected code. */
  findOffer(code: string | null | undefined): PromotionOfferResponse | undefined {
    const key = normalizePromotionCode(code);
    return key ? this.views.find((view) => view.key === key)?.offer : undefined;
  }

  // Keep outside controls in place until their click runs: collapsing the panel on pointerdown can move the
  // control before pointerup and swallow the click. Blank areas still use pointerdown for iOS WebKit taps.
  @HostListener('document:pointerdown', ['$event.target'])
  onDocumentPointerDown(target: EventTarget | null): void {
    if (target instanceof Element && target.closest('button, a, input, select, textarea, label, [role="button"]')) {
      return;
    }
    this._closeFromOutside(target);
  }

  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: EventTarget | null): void {
    this._closeFromOutside(target);
  }

  private _closeFromOutside(target: EventTarget | null): void {
    if (!this.isOpen || !(target instanceof Node)) {
      return;
    }
    if (this._host.nativeElement.contains(target) || this.anchor?.contains(target)) {
      return;
    }
    this.close();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close();
  }

  protected toggle(): void {
    if (this.isOpen) {
      this.close();
    } else {
      this.open();
    }
  }

  protected pick(view: PromotionOfferView): void {
    if (this.disabled || view.key === this.appliedKey) {
      return;
    }
    this.picked.emit(view.code);
    this.close();
  }

  protected trackByKey(_index: number, view: PromotionOfferView): string {
    return view.key;
  }

  private _load(source: PromotionOfferSource): void {
    this.status = 'loading';
    this._subscription = sharedOffers(source).subscribe((offers) => {
      this.views = offers.map((offer) => toView(offer));
      this.status = this.views.length > 0 ? 'ready' : 'unavailable';
      if (this.status === 'unavailable') {
        this.isOpen = false;
      }
      this._cdr.markForCheck();
    });
  }

  /** Forgets the previous show/session: its request (if still running) and its list. */
  private _reset(): void {
    this._subscription?.unsubscribe();
    this._subscription = undefined;
    this.status = 'idle';
    this.views = [];
    this.isOpen = false;
    this._cdr.markForCheck();
  }
}
