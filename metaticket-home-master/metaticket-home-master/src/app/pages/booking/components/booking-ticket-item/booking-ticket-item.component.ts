import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { ImageComponent } from 'src/app/lib/components';
import { NgIf } from '@angular/common';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { appIcons } from 'src/app/lib/theme';
import { SeatPlanTriggerItem } from 'src/app/lib/dto';

@Component({
  selector: 'booking-ticket-item',
  standalone: true,
  imports: [FormatPricePipe, ImageComponent, NgIf, NzTypographyComponent],
  templateUrl: './booking-ticket-item.component.html',
})
export class BookingTicketItemComponent {
  @Input({ required: true }) item!: SeatPlanTriggerItem;
  @Input() showDelete = false;
  @Output() handlerOnDelete = new EventEmitter<SeatPlanTriggerItem>();

  protected readonly appIcons = appIcons;

  onDelete(): void {
    this.handlerOnDelete.emit(this.item);
  }
}
