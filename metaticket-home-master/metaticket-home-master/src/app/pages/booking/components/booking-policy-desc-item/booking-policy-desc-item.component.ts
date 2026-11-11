import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { appIcons } from 'src/app/lib/theme';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { ImageComponent } from 'src/app/lib/components';
import { NgForOf, NgIf } from '@angular/common';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { TicketResponse } from 'src/app/lib/entities';
import { SeatPlanTriggerItem } from 'src/app/lib/dto';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Component({
  selector: 'booking-policy-desc-item',
  standalone: true,
  imports: [FormatPricePipe, ImageComponent, NgForOf, NgIf, NzTypographyComponent],
  templateUrl: './booking-policy-desc-item.component.html',
})
export class BookingPolicyDescItemComponent implements OnChanges {
  protected readonly appIcons = appIcons;
  @Input({ required: true }) item!: TicketResponse;
  @Input({ required: true }) listSelectedTickets!: SeatPlanTriggerItem[];
  @Output() handlerChangeQuantity = new EventEmitter<number>();

  constructor(private _sanitized: DomSanitizer) {}

  quantity = 0;

  onIncreaseQuantity(): void {
    this.handlerChangeQuantity.emit(this.quantity + 1);
  }

  onDecreaseQuantity(): void {
    this.handlerChangeQuantity.emit(this.quantity - 1);
  }

  ngOnChanges(): void {
    const ticket = this.listSelectedTickets.find((item) => item.sid === this.item.sid);
    this.quantity = ticket?.quantity ?? 0;
  }

  formatDescription(des: string): SafeHtml {
    return this._sanitized.bypassSecurityTrustHtml(des?.replace(/\n/g, '<br/>'));
  }
}
