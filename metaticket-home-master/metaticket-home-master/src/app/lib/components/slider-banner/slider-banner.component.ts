import { NgClass, NgFor } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { HammerModule } from '@angular/platform-browser';
import { SearchShowResponse } from 'src/app/lib/entities';
import { appIcons } from 'src/app/lib/theme';
import { ImageComponent } from 'src/app/lib/components/image/image.component';
import { showDetailPath } from 'src/app/lib/utils';
import { NavigationService } from '../../services';

@Component({
  selector: 'app-slider-banner',
  standalone: true,
  imports: [NgFor, ImageComponent, NgClass, HammerModule],
  templateUrl: './slider-banner.component.html',
  styleUrls: ['./slider-banner.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SliderBannerComponent implements OnInit, OnDestroy {
  selectedIndex = 0;
  interval?: ReturnType<typeof setInterval>;

  constructor(
    private _navigationService: NavigationService,
    private _cdr: ChangeDetectorRef,
  ) {}

  protected readonly appIcons = appIcons;
  @Input() bannerShows!: SearchShowResponse[];

  ngOnInit(): void {
    this.onSetInterval();
  }

  ngOnDestroy(): void {
    this.onClearInterval();
  }

  trackByShowId(_index: number, show: SearchShowResponse): string {
    return show.id;
  }

  trackByIndex(index: number): number {
    return index;
  }

  onSetInterval(): void {
    this.interval = setInterval(() => {
      if (this.selectedIndex === this.bannerShows.length - 1) {
        this.selectedIndex = 0;
      } else {
        this.selectedIndex = this.selectedIndex + 1;
      }
      this._cdr.markForCheck();
    }, 3 * 1000);
  }

  onClearInterval(): void {
    if (this.interval) {
      clearInterval(this.interval);
    }
  }

  showPrev(i: number): void {
    if (i === 0) {
      this.selectedIndex = this.bannerShows.length - 1;
    } else {
      this.selectedIndex = i - 1;
    }
  }

  showNext(i: number): void {
    if (i === this.bannerShows.length - 1) {
      this.selectedIndex = 0;
    } else {
      this.selectedIndex = i + 1;
    }
  }

  onViewDetail(show: SearchShowResponse): void {
    this._navigationService.navigateTo(showDetailPath(show));
  }
}
