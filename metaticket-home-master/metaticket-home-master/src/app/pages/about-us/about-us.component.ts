import { NgTemplateOutlet } from '@angular/common';
import { Component } from '@angular/core';
import { FormInputComponent, ImageComponent, LayoutComponent, NavbarComponent } from 'src/app/lib/components';
import { appIcons, appImages } from 'src/app/lib/theme';

@Component({
  selector: 'app-about-us',
  standalone: true,
  imports: [NavbarComponent, LayoutComponent, FormInputComponent, ImageComponent, NgTemplateOutlet],
  templateUrl: './about-us.component.html',
})
export class AboutUsComponent {
  protected readonly appImages = appImages;
  protected readonly appIcons = appIcons;
}
