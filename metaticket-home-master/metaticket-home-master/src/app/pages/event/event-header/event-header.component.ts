import { BreakpointObserver } from '@angular/cdk/layout';
import { Component } from '@angular/core';
import { appIcons, appImages } from 'src/app/lib/theme';
import { ImageComponent, NavbarComponent } from 'src/app/lib/components';
import { NgClass, NgIf, NgStyle } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-event-header',
  standalone: true,
  imports: [ImageComponent, NavbarComponent, NgClass, NgStyle, NgIf],
  templateUrl: './event-header.component.html',
})
export class EventHeaderComponent {
  protected readonly appImages = appImages;
  protected readonly appIcons = appIcons;
  protected readonly listBanners: { [key: string]: string | undefined } = {
    music: appImages.bgEventMusic,
    theatre: appImages.bgStage,
    tourism: appImages.bgTourist,
    meeting: appImages.bgConference,
    sport: appImages.bgSport,
    children: appImages.bgChild,
    stage: appImages.bgStage,
    tourist: appImages.bgTourist,
    conference: appImages.bgConference,
    workshop: appImages.bgConference,
    cinema: appImages.bgStage,
  };
  banner = '';
  name = '';
  isMobile: boolean;

  constructor(
    private _breakpointObserver: BreakpointObserver,
    private _route: ActivatedRoute,
  ) {
    this._route.queryParamMap.subscribe((queryParams) => {
      const id = queryParams.get('id');
      this.name = queryParams.get('name') ?? '';
      this.banner = this.listBanners[id!] ?? '';
    });

    this.isMobile = this._breakpointObserver.isMatched('(max-width: 767px)'); // Adjust the breakpoint as needed
    this._breakpointObserver
      .observe([
        // Breakpoints.HandsetPortrait,
        // Breakpoints.HandsetLandscape,
        // Breakpoints.TabletPortrait,
        // Breakpoints.TabletLandscape,
        '(max-width: 1024px)',
      ])
      .subscribe((result) => {
        this.isMobile = result.matches;
      });
  }
}
