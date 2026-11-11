import { Component, Input } from '@angular/core';
import { UserProfileModel } from '../../entities/response/user';
import { NgIf } from '@angular/common';
import { AuthService } from '../../services';
import { appImages } from '../../theme';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-user-profile',
  templateUrl: './user.profile.component.html',
  standalone: true,
  imports: [NgIf, RouterLink],
})
export class UserProfileComponent {
  @Input() profile!: UserProfileModel;
  dropdownOpen = false;

  constructor(private authService: AuthService) {}

  toggleDropdown(): void {
    this.dropdownOpen = !this.dropdownOpen;
  }

  logout(): void {
    this.authService.logout();
  }

  protected readonly appImages = appImages;
}
