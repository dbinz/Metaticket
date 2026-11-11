import { Component } from '@angular/core';
import { ImageComponent, LayoutComponent } from 'src/app/lib/components';
import { appImages, appIcons } from 'src/app/lib/theme';
import { HomeContentComponent, HomeHeaderComponent } from './components';
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [LayoutComponent, HomeHeaderComponent, HomeContentComponent, ImageComponent, ImageComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent {
  protected readonly appImages = appImages;
  protected readonly appIcons = appIcons;
}
