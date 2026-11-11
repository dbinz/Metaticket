import { NgClass, NgFor } from '@angular/common';
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { HammerModule } from '@angular/platform-browser';
import { SearchShowResponse } from 'src/app/lib/entities';
import { appIcons } from 'src/app/lib/theme';
import { NavigationService } from '../../services';
import { showDetailPath } from '../../utils';

@Component({
  selector: 'app-slider-banner-mobile',
  standalone: true,
  imports: [NgFor, NgClass, HammerModule],
  templateUrl: './slider-banner-mobile.component.html',
  styleUrl: './slider-banner-mobile.component.scss',
})
export class SliderBannerMobileComponent implements OnInit, OnDestroy {
  selectedIndex = 0;
  interval?: number;
  constructor(private _navigationService: NavigationService) {}
  protected readonly appIcons = appIcons;
  @Input() bannerShows!: SearchShowResponse[];

  ngOnInit(): void {
    this.interval = window.setInterval(() => {
      if (this.selectedIndex === this.bannerShows.length - 1) {
        this.selectedIndex = 0;
      } else {
        this.selectedIndex = this.selectedIndex + 1;
      }
    }, 3 * 1000);
  }

  ngOnDestroy(): void {
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
  setSelectedIndex(index: number): void {
    this.selectedIndex = index;
  }
  onViewDetail(show: SearchShowResponse): void {
    this._navigationService.navigateTo(showDetailPath(show));
  }
}
