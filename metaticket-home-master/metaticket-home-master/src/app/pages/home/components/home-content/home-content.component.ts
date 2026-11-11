import { NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { GridEventTicketComponent, ImageComponent } from 'src/app/lib/components';
import { SearchShowResponse } from 'src/app/lib/entities';
import { ShowService } from 'src/app/lib/services';
import { appIcons } from 'src/app/lib/theme';
import { HomeSearchService } from 'src/app/lib/stores';
import { ActivatedRoute } from '@angular/router';
import { HomeMerchStripComponent } from '../home-merch-strip/home-merch-strip.component';
import { HomeShowcaseComponent } from '../home-showcase/home-showcase.component';

@Component({
  selector: 'home-content',
  standalone: true,
  imports: [GridEventTicketComponent, ImageComponent, NgIf, HomeMerchStripComponent, HomeShowcaseComponent],
  templateUrl: './home-content.component.html',
})
export class HomeContentComponent implements OnInit {
  protected appIcons = appIcons;

  constructor(
    private _route: ActivatedRoute,
    private _showService: ShowService,
    private _homeSearchService: HomeSearchService,
  ) {
    this._route.queryParamMap.pipe(takeUntilDestroyed()).subscribe(() => {
      this._homeSearchService.onSelectEvent(null);
    });
  }

  public recentShows: SearchShowResponse[] = [];

  /** Restores the last few shows the visitor opened, if any. */
  ngOnInit(): void {
    this.recentShows = this._showService.getShowsFromLocalStorage().slice(0, 6);
  }
}
