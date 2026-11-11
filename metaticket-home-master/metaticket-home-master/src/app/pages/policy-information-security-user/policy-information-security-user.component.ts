import { Component } from '@angular/core';
import { ImageComponent, LayoutComponent, NavbarComponent } from 'src/app/lib/components';

@Component({
  selector: 'app-policy-information-security-user',
  standalone: true,
  imports: [LayoutComponent, NavbarComponent, ImageComponent],
  templateUrl: './policy-information-security-user.component.html',
  styleUrl: './policy-information-security-user.component.css',
})
export class PolicyInformationSecurityUserComponent {}
