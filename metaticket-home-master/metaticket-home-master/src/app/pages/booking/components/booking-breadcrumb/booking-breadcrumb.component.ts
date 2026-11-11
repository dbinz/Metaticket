import { Component, Input } from '@angular/core';
import { ImageComponent } from 'src/app/lib/components';
import { NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { appIcons } from 'src/app/lib/theme';
import { BookingSeatMapComponent } from '../booking-seat-map/booking-seat-map.component';
import { BookingInformationComponent } from '../booking-information/booking-information.component';
import { BookingPaymentComponent } from '../booking-payment/booking-payment.component';
import { BookingCompletedComponent } from '../booking-completed/booking-completed.component';
import { BookingPolicyDescComponent } from '../booking-policy-desc/booking-policy-desc.component';

@Component({
  selector: 'booking-breadcrumb',
  standalone: true,
  imports: [ImageComponent, NgIf, NzTypographyComponent, NgForOf, NgTemplateOutlet],
  templateUrl: './booking-breadcrumb.component.html',
})
export class BookingBreadcrumbComponent {
  @Input({ required: true }) currentIndex!: number;
  @Input({ required: true }) steps!: {
    title: string;
    component:
      | typeof BookingSeatMapComponent
      | typeof BookingPolicyDescComponent
      | typeof BookingInformationComponent
      | typeof BookingPaymentComponent
      | typeof BookingCompletedComponent;
  }[];

  readonly appIcons = appIcons;
}
