import { Component } from '@angular/core';
import { NzCarouselModule } from 'ng-zorro-antd/carousel';
import { NgForOf, NgTemplateOutlet } from '@angular/common';
import { ImageComponent } from 'src/app/lib/components';
import { appIcons } from 'src/app/lib/theme';

@Component({
  selector: 'home-carousel',
  standalone: true,
  imports: [NzCarouselModule, NgForOf, NgTemplateOutlet, ImageComponent],
  templateUrl: './home-carousel.component.html',
})
export class HomeCarouselComponent {
  protected readonly array = [
    'https://www.cgv.vn/media/banner/cache/1/b58515f018eb873dafa430b6f9ae0c1e/d/h/dhd_rolling_banner.jpg',
    'https://www.cgv.vn/media/banner/cache/1/b58515f018eb873dafa430b6f9ae0c1e/d/h/dhd_rolling_banner.jpg',
    'https://www.cgv.vn/media/banner/cache/1/b58515f018eb873dafa430b6f9ae0c1e/d/h/dhd_rolling_banner.jpg',
  ];

  public currentIndex = 0;

  onChange(index: number): void {
    this.currentIndex = index;
  }

  protected readonly appIcons = appIcons;
}
