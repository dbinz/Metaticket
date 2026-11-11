import { Component, EventEmitter, Input, NgZone, OnInit, Output } from '@angular/core';
import { ImageComponent } from '../image/image.component';
import { appIcons } from 'src/app/lib/theme';
import { NgIf, NgTemplateOutlet } from '@angular/common';
import { SeatPlanEvent, SeatPlanTriggerEvent, SeatPlanTriggerItem } from 'src/app/lib/dto';
import { TicketResponse } from 'src/app/lib/entities';
import { EdType } from 'src/app/lib/enums';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { environment } from 'src/environments/environment';

declare let metaticket: any;

class SeatPlanListener {
  constructor(public onEventListener: (data: SeatPlanEvent) => void) {}

  onEvent(data: SeatPlanEvent): void {
    this.onEventListener(data);
  }
}

@Component({
  selector: 'app-seat-plan',
  standalone: true,
  imports: [ImageComponent, NgTemplateOutlet, NgIf, NzTypographyComponent, FormatPricePipe],
  templateUrl: './seat-plan.component.html',
})
export class SeatPlanComponent implements OnInit {
  @Input({ required: true }) seatPlanId!: string;
  @Input() listTickets: TicketResponse[] = [];
  @Input({ required: true }) type?: EdType;
  @Input() ledgendImageUrl?: string;
  @Input() isView = false;
  @Output() seatPlanListener = new EventEmitter<SeatPlanTriggerEvent | null>();

  protected readonly appIcons = appIcons;
  protected readonly edType = EdType;
  protected readonly metaDomain = environment.metaDomain;
  private _seatPlanner: any;
  isZoomIn = false;
  isZoomOut = false;
  isPan = false;
  ledgendVisible = true;
  zIndex = 10010;

  triggerEvent: SeatPlanTriggerEvent | null = null;
  top = 0;
  left = 0;

  constructor(private _zone: NgZone) {}

  private _initSeatPlan(): void {
    const el = document.getElementById('seat-plan-viewport');
    // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment,@typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
    this._seatPlanner = new metaticket.PlannerWrapper(this.type);
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-member-access
    this._seatPlanner.initialize(el, this.seatPlanId).then(() => {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
      this._seatPlanner.registerListener(this.seatPlanId, new SeatPlanListener((data) => this._listener(data)));
      this._initSeatPlanStatus();
      if (el?.firstChild) {
        el.firstChild.addEventListener('scroll', () => {
          this._zone.run(() => {
            if (this.triggerEvent != null) {
              this.onClosePopup();
            }
          });
        });
      }
    });
  }

  private _listener(data: SeatPlanEvent): void {
    if (this.isView) return;

    this._zone.run(() => {
      if (data.eventType === 'item-selected') {
        if (this.triggerEvent != null) {
          this.onClosePopup();
        }

        // handler show popup
        const rect = data.state.triggerEl.getBoundingClientRect();
        this.top = rect.y + window.scrollY + rect.height + 2;
        this.left = rect.x - 50;
        if (rect.x - 50 + 178 > window.innerWidth) {
          this.left = rect.x - 155;
        }

        this.triggerEvent = this._convertSeatPlanEvent(data);
        return;
      }

      if (data.eventType === 'item-deselected' && data.state.triggerItem?.sid === this.triggerEvent?.triggerItem?.sid) {
        this.triggerEvent = null;
        return;
      }

      this.seatPlanListener.emit(this._convertSeatPlanEvent(data));
    });
  }

  private _convertSeatPlanEvent(data: SeatPlanEvent): SeatPlanTriggerEvent | null {
    const item = this.listTickets.find((ticket) => ticket.sid === data.state.triggerItem?.sid);
    if (item) {
      const triggerItem: SeatPlanTriggerItem = {
        id: item.id,
        priceCat: item.priceCat,
        sid: data.state.triggerItem?.sid ?? '',
        displayName: item.priceCat + ' | ' + data.state.triggerItem?.sid,
        basePrice: item.basePrice,
        quantity: 1,
        remCap: item?.remCap,
      };

      return {
        eventType: data.eventType,
        items: data.state.items,
        triggerItem: triggerItem,
      };
    }

    return {
      eventType: data.eventType,
      items: data.state.items,
    };
  }

  onIncreaseQuantity(): void {
    if (this.triggerEvent && this.triggerEvent.triggerItem) {
      this.triggerEvent.triggerItem.quantity = this.triggerEvent.triggerItem.quantity + 1;
    }
  }

  onDecreaseQuantity(): void {
    if (this.triggerEvent && this.triggerEvent.triggerItem) {
      this.triggerEvent.triggerItem.quantity = this.triggerEvent.triggerItem.quantity - 1;
    }
  }

  onSelectTicket(): void {
    if (this.triggerEvent) {
      this.seatPlanListener.emit(this.triggerEvent);
      this.triggerEvent = null;
    }
  }

  onClosePopup(): void {
    if (this.triggerEvent && this.triggerEvent.triggerItem) {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
      this._seatPlanner.clearSelection(this.seatPlanId, [this.triggerEvent.triggerItem.sid]);
      this.triggerEvent = null;
    }
  }

  private _initSeatPlanStatus(): void {
    const listDisableTicket = this.listTickets.filter((ticket) => ticket.remCap <= 0);
    const listDisableSid = listDisableTicket.map((ticket) => ticket.sid);
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
    this._seatPlanner.setDisabled(true, this.seatPlanId, listDisableSid);
  }

  deselectTicket(sid: string): void {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
    this._seatPlanner.clearSelection(this.seatPlanId, [sid]);
  }

  updateTicketsStatus(items: TicketResponse[]): void {
    if (items.length === 0) return;

    const listEnableSeats: string[] = [];
    const listDisableSeats: string[] = [];

    items.forEach((item) => {
      if (item.remCap <= 0) {
        listDisableSeats.push(item.sid);
        return;
      }

      listEnableSeats.push(item.sid);
    });

    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
    this._seatPlanner.setDisabled(false, this.seatPlanId, [...listEnableSeats]);
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call
    this._seatPlanner.setDisabled(true, this.seatPlanId, [...listDisableSeats]);
  }

  private _clearTool(): void {
    this.isZoomIn = false;
    this.isZoomOut = false;
    this.isPan = false;
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-member-access
    this._seatPlanner.clearTool();
  }

  onLedgendVisible(): void {
    this.ledgendVisible = !this.ledgendVisible;
  }

  zoomIn(): void {
    if (this.isZoomIn) {
      this.zIndex = this.zIndex + 2;
      this._clearTool();
      return;
    }

    this._clearTool();
    this.isZoomIn = true;
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-member-access
    this._seatPlanner.startZoom('zoom-in');
  }

  zoomOut(): void {
    if (this.isZoomOut) {
      this.zIndex = this.zIndex + 2;
      this._clearTool();
      return;
    }

    this._clearTool();
    this.isZoomOut = true;
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-member-access
    this._seatPlanner.startZoom('zoom-out');
  }

  pan(): void {
    if (this.isPan) {
      this.zIndex = this.zIndex + 2;
      this._clearTool();
      return;
    }

    this._clearTool();
    this.isPan = true;
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-member-access
    this._seatPlanner.startPan();
  }

  ngOnInit(): void {
    this._initSeatPlan();
  }
}
