import { Component, OnInit } from '@angular/core';
import { ButtonComponent, ImageComponent, LoadingComponent } from 'src/app/lib/components';
import { appIcons } from 'src/app/lib/theme';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { OrderService } from 'src/app/lib/services';
import { OrderSummaryResponse, OrderTicketResponse } from 'src/app/lib/entities';
import { BookingQuery } from 'src/app/lib/stores';
import { visitDateLabel } from 'src/app/lib/utils';

@Component({
  selector: 'booking-completed',
  standalone: true,
  imports: [
    ImageComponent,
    NzTypographyComponent,
    NzDividerComponent,
    NgTemplateOutlet,
    FormatPricePipe,
    NgForOf,
    NgIf,
    LoadingComponent,
    ButtonComponent,
  ],
  templateUrl: './booking-completed.component.html',
})
export class BookingCompletedComponent implements OnInit {
  protected readonly appIcons = appIcons;
  isLoading = false;
  orderSummary: OrderSummaryResponse | null = null;
  orderTickets: OrderTicketResponse[] = [];
  /** Set only for calendar orders: the tickets are valid on this date only. */
  visitDateLabel = '';

  constructor(
    private _orderService: OrderService,
    private _bookingQuery: BookingQuery,
  ) {}

  private _getOrderSummary(): void {
    this.isLoading = true;
    const orderId = this._bookingQuery.getValue().orderId;
    this._orderService.getOrderSummary(orderId).subscribe({
      next: (response) => {
        this.orderTickets = response.orderDetail.prices;
        this.orderSummary = response;
        this.isLoading = false;
      },
    });
  }

  ngOnInit(): void {
    const state = this._bookingQuery.getValue();
    if (state.visitDate) {
      const session = state.showDetail?.sessions?.find((item) => item.id === state.sessionId);
      this.visitDateLabel = visitDateLabel(state.visitDate, session?.calendar);
    }
    this._getOrderSummary();
  }

  onGoBack(): void {
    window.location.reload();
  }
}
