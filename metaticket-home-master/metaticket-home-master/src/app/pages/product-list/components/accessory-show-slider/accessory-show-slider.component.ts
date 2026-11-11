import { NgFor, NgIf } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import { HammerModule } from '@angular/platform-browser';
import { ImageComponent } from 'src/app/lib/components/image/image.component';
import { AccessoryShowResponse, AccessoryShowSessionResponse } from 'src/app/lib/entities';
import { appIcons, appImages } from 'src/app/lib/theme';

/**
 * Full-bleed 16:9 banner carousel for **pinned** accessory shows (pinOrder).
 * Image-first slides with bottom caption; click opens the show. Autoplays when >1 slide.
 */
@Component({
  selector: 'app-accessory-show-slider',
  standalone: true,
  imports: [NgFor, NgIf, ImageComponent, HammerModule],
  templateUrl: './accessory-show-slider.component.html',
  styleUrl: './accessory-show-slider.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessoryShowSliderComponent implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) shows!: AccessoryShowResponse[];
  @Output() selected = new EventEmitter<AccessoryShowResponse>();

  protected readonly appIcons = appIcons;

  selectedIndex = 0;
  interval?: ReturnType<typeof setInterval>;

  constructor(private _cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.onSetInterval();
  }

  /** Restart autoplay when the pin list is replaced. */
  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['shows'] || changes['shows'].firstChange) {
      return;
    }
    this.selectedIndex = 0;
    this.onSetInterval();
    this._cdr.markForCheck();
  }

  ngOnDestroy(): void {
    this.onClearInterval();
  }

  get currentShow(): AccessoryShowResponse | undefined {
    return this.shows[this.selectedIndex];
  }

  /** Remounts the active slide so enter animation / Ken Burns restart on change. */
  get activeSlides(): AccessoryShowResponse[] {
    return this.currentShow ? [this.currentShow] : [];
  }

  trackByIndex(index: number): number {
    return index;
  }

  trackActiveSlide(_index: number, show: AccessoryShowResponse): string {
    return `${this.selectedIndex}-${show.id}`;
  }

  /** Start / restart autoplay (no-op for a single show). */
  onSetInterval(): void {
    this.onClearInterval();
    if (this.shows.length <= 1) {
      return;
    }
    this.interval = setInterval(() => {
      this.showNext();
      this._cdr.markForCheck();
    }, 6 * 1000);
  }

  /** Pause autoplay while the user interacts with the stage. */
  onClearInterval(): void {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = undefined;
    }
  }

  showPrev(): void {
    this.selectedIndex = this.selectedIndex === 0 ? this.shows.length - 1 : this.selectedIndex - 1;
  }

  showNext(): void {
    this.selectedIndex = this.selectedIndex === this.shows.length - 1 ? 0 : this.selectedIndex + 1;
  }

  /** Prev control — stop propagation so the slide click does not open the show. */
  onNavPrev(event: Event): void {
    event.stopPropagation();
    this.showPrev();
    this.onSetInterval();
    this._cdr.markForCheck();
  }

  /** Next control — stop propagation so the slide click does not open the show. */
  onNavNext(event: Event): void {
    event.stopPropagation();
    this.showNext();
    this.onSetInterval();
    this._cdr.markForCheck();
  }

  /** Hammer swipe left → next slide. */
  onSwipeNext(): void {
    if (this.shows.length <= 1) return;
    this.showNext();
    this.onSetInterval();
    this._cdr.markForCheck();
  }

  /** Hammer swipe right → previous slide. */
  onSwipePrev(): void {
    if (this.shows.length <= 1) return;
    this.showPrev();
    this.onSetInterval();
    this._cdr.markForCheck();
  }

  goTo(index: number): void {
    this.selectedIndex = index;
    this.onSetInterval();
    this._cdr.markForCheck();
  }

  onSelect(show: AccessoryShowResponse): void {
    this.selected.emit(show);
  }

  showImage(show: AccessoryShowResponse): string {
    return show.bannerImageUrl || appImages.imgMetaTicketThumbnail;
  }

  /** "TP.HCM 11.07.2026 · Hà Nội 25.07.2026" from the show's sessions (empty when none). */
  citiesLine(show: AccessoryShowResponse): string {
    return (show.sessions ?? []).map((s) => `${s.city} ${this._formatDate(s.date)}`).join(' · ');
  }

  /** "Nhà thi đấu Rạch Miễu · Nhà thi đấu Tây Hồ" from the show's sessions (empty when none). */
  venuesLine(show: AccessoryShowResponse): string {
    return (show.sessions ?? []).map((s) => s.venue).join(' · ');
  }

  private _formatDate(timestamp: number): string {
    const date = new Date(timestamp);
    const dd = `${date.getDate()}`.padStart(2, '0');
    const mm = `${date.getMonth() + 1}`.padStart(2, '0');
    const hh = `${date.getHours()}`.padStart(2, '0');
    const mi = `${date.getMinutes()}`.padStart(2, '0');
    if (date.getHours() !== 0 || date.getMinutes() !== 0) {
      return `${dd}.${mm}.${date.getFullYear()} ${hh}:${mi}`;
    }
    return `${dd}.${mm}.${date.getFullYear()}`;
  }

  trackBySession(_index: number, session: AccessoryShowSessionResponse): string {
    return `${session.city}-${session.date}`;
  }
}
