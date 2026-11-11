import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ImageComponent } from 'src/app/lib/components';
import { NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { appIcons } from 'src/app/lib/theme';

/**
 * Step progress bar for the accessory checkout, mirroring
 * `BookingBreadcrumbComponent`. Renders the 4 step titles
 * (Chọn → Điền thông tin → Thanh toán → Hoàn tất) with the current step
 * highlighted.
 */
@Component({
  selector: 'accessory-breadcrumb',
  standalone: true,
  imports: [ImageComponent, NgIf, NzTypographyComponent, NgForOf, NgTemplateOutlet],
  templateUrl: './accessory-breadcrumb.component.html',
})
export class AccessoryBreadcrumbComponent {
  @Input({ required: true }) currentIndex!: number;
  @Input({ required: true }) steps!: { title: string }[];
  @Input() locked = false;
  @Output() stepSelected = new EventEmitter<number>();

  isClickable(index: number): boolean {
    return !this.locked && index < this.currentIndex;
  }

  onSelectStep(index: number): void {
    if (this.isClickable(index)) {
      this.stepSelected.emit(index);
    }
  }

  readonly appIcons = appIcons;
}
