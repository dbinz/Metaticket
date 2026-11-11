import { Component } from '@angular/core';
import { ButtonComponent, ImageComponent } from 'src/app/lib/components';
import { appIcons } from 'src/app/lib/theme';
import { NavigationService } from 'src/app/lib/services';

@Component({
  selector: 'app-not-found',
  standalone: true,
  templateUrl: './not-found.component.html',
  imports: [ButtonComponent, ImageComponent],
})
export class NotFoundComponent {
  protected readonly appIcons = appIcons;

  constructor(private _navigationService: NavigationService) {}

  navigateToHome(): void {
    this._navigationService.navigateToHome();
  }

  goBack(): void {
    this._navigationService.goBack();
  }
}
