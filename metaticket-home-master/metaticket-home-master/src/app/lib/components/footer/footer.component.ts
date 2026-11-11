import { Component, HostBinding, Input } from '@angular/core';
import { appIcons } from 'src/app/lib/theme';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NgTemplateOutlet } from '@angular/common';
import { ImageComponent } from '../image/image.component';
import { ModalService, NavigationService } from 'src/app/lib/services';
import { routeName } from 'src/app/lib/constants';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [NzTypographyModule, NgTemplateOutlet, ImageComponent, RouterLink],
  templateUrl: './footer.component.html',
})
export class FooterComponent {
  // Nested ng-template outlets + class interpolations mismatch SSR vs client DOM.
  @HostBinding('attr.ngSkipHydration') readonly ngSkipHydration = 'true';

  constructor(
    private _navigationService: NavigationService,
    protected modalService: ModalService,
  ) {}
  protected routeName = routeName;

  @Input() isDarkMode = true;

  public appIcons = appIcons;
  navigateToAboutUs(): void {
    this._navigationService.navigateTo(routeName.aboutUs);
  }
  navigateToContact(): void {
    this._navigationService.navigateTo(routeName.contact);
  }
  navigateToFaq(): void {
    this._navigationService.navigateTo(routeName.faq);
  }
  navigateToPolicyRegulation(): void {
    this._navigationService.navigateTo(routeName.policyRegulation);
  }
  navigateToPolicyInformationSecurity(): void {
    this._navigationService.navigateTo(routeName.policyInformationSecurity);
  }
  navigateToFb(): void {
    window.open('https://www.facebook.com/metaticketvietnam/', '_blank');
  }
}
