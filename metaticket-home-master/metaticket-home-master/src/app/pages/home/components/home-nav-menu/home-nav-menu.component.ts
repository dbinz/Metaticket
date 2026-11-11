import { Component, Input, OnInit } from '@angular/core';
import { NgForOf, NgTemplateOutlet } from '@angular/common';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { ImageComponent } from 'src/app/lib/components';
import { EventCategoryResponse } from 'src/app/lib/entities';
import { NavigationService, ShowService } from 'src/app/lib/services';
import { appIcons } from 'src/app/lib/theme';
import { NavigationExtras } from '@angular/router';
import { routeName } from 'src/app/lib/constants';
import { animate, state, style, transition, trigger } from '@angular/animations';

@Component({
  selector: 'home-nav-menu',
  standalone: true,
  imports: [NgForOf, NgTemplateOutlet, ImageComponent, NzTypographyModule],
  templateUrl: './home-nav-menu.component.html',
  styleUrls: ['./home-nav-menu.component.css'],
  animations: [
    trigger('scaleEffect', [
      state(
        'small',
        style({
          transform: 'scale(1)',
        }),
      ),
      state(
        'large',
        style({
          transform: 'scale(1.2)',
        }),
      ),
      transition('small<=>large', animate('150ms ease-in')),
    ]),
  ],
})
export class HomeNavMenuComponent implements OnInit {
  @Input() isVisible: boolean = false;
  protected readonly listIcons: { [key: string]: string | undefined } = {
    music: appIcons.music,
    theatre: appIcons.theatre,
    tourism: appIcons.tourism,
    meeting: appIcons.meeting,
    sport: appIcons.sport,
    children: appIcons.children,
    stage: appIcons.theatre,
    tourist: appIcons.tourism,
    conference: appIcons.meeting,
    workshop: appIcons.meeting,
    cinema: appIcons.theatre,
  };
  isLoading = false;
  listEventCategory: EventCategoryResponse[] = [];

  constructor(
    private _showService: ShowService,
    private _navigationService: NavigationService,
  ) {}

  getIcon(categoryId: string): string {
    if (categoryId === 'merchandise') {
      return appIcons.icMerchandise;
    }

    const icon = this.listIcons[categoryId];

    return icon ?? appIcons.icBackup;
  }

  navigateToEventCategory(item: EventCategoryResponse): void {
    if (item.id === 'merchandise') {
      this.navigateToMerchandise();
      return;
    }

    const extras: NavigationExtras = {
      queryParams: {
        id: item.id,
        name: item.name,
      },
    };
    this._navigationService.navigateTo(routeName.event, extras);
  }

  navigateToMerchandise(): void {
    this._navigationService.navigateTo(routeName.product);
  }

  ngOnInit(): void {
    this.isLoading = true;
    this._showService.getEventCategory().subscribe({
      next: (response) => {
        this.listEventCategory = response.map((item) => {
          return {
            ...item,
            animation: 'small',
          };
        });
        this.listEventCategory.push({
          id: 'merchandise',
          name: 'Merchandise',
          animation: 'small',
        });
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        console.log('get event category error', err);
      },
    });
  }

  animateImage(index: number): void {
    this.listEventCategory[index].animation = this.listEventCategory[index].animation === 'small' ? 'large' : 'small';
  }
}
