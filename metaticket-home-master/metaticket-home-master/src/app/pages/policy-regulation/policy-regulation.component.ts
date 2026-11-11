import { Component } from '@angular/core';
import { ImageComponent, LayoutComponent, NavbarComponent } from 'src/app/lib/components';
import { appImages } from 'src/app/lib/theme';
@Component({
  selector: 'app-policy-regulation',
  standalone: true,
  imports: [LayoutComponent, NavbarComponent, ImageComponent],
  templateUrl: './policy-regulation.component.html',
  styleUrl: './policy-regulation.component.css',
})
export class PolicyRegulationComponent {
  protected appImages = appImages;
}
