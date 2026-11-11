import { Component, OnInit } from '@angular/core';
import { ImageComponent, NavbarComponent } from 'src/app/lib/components';
import { SliderBannerMobileComponent } from 'src/app/lib/components/slider-banner-mobile/slider-banner-mobile.component';
import { SliderBannerComponent } from 'src/app/lib/components/slider-banner/slider-banner.component';
import { SearchShowResponse } from 'src/app/lib/entities';
import { ShowService } from 'src/app/lib/services';
import { appIcons } from 'src/app/lib/theme';
import { HomeNavMenuComponent } from '../home-nav-menu/home-nav-menu.component';

@Component({
  selector: 'home-header',
  standalone: true,
  imports: [NavbarComponent, HomeNavMenuComponent, SliderBannerComponent, SliderBannerMobileComponent, ImageComponent],
  templateUrl: './home-header.component.html',
})
export class HomeHeaderComponent implements OnInit {
  protected appIcons = appIcons;
  constructor(private _showService: ShowService) {}
  public bannerShows: SearchShowResponse[] = [];
  public bannersLoading = true;
  public bannersFailed = false;
  ngOnInit(): void {
    this._showService.getTopBannerShow().subscribe({
      next: (res) => {
        this.bannerShows = res.content ?? [];
        this.bannersLoading = false;
      },
      error: () => {
        this.bannersFailed = true;
        this.bannersLoading = false;
      },
    });
  }
}
