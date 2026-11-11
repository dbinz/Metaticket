import { Component, Input } from '@angular/core';
import { FooterComponent } from '../footer/footer.component';
import { NgIf } from '@angular/common';
import { ImageComponent } from '../image/image.component';
import { appIcons } from 'src/app/lib/theme';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [FooterComponent, NgIf, ImageComponent],
  templateUrl: './layout.component.html',
})
export class LayoutComponent {
  @Input() isDarkMode = true;
  @Input() isLoading = false;

  protected readonly appIcons = appIcons;
}
