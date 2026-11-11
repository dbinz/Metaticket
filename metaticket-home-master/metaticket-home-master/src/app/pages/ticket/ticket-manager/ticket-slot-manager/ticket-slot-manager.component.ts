import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LayoutComponent, LoadingComponent, NavbarComponent, PopupErrorComponent } from 'src/app/lib/components';
import { AsyncPipe, NgClass, NgForOf, NgIf } from '@angular/common';
import { TicketSlotResponse, TicketSlotStateResponse } from 'src/app/lib/entities';
import { BehaviorSubject } from 'rxjs';
import { formatDate, visitDateLabel } from 'src/app/lib/utils';
import { TicketSlotByOrderModel } from '../model';
import { TicketSlotsService } from 'src/app/lib/services';
import { HttpErrorResponse } from '@angular/common/http';
import { TransferTicketSlotComponent } from '../transfer-ticket-slot/transfer-ticket-slot.component';

@Component({
  selector: 'app-ticket-slots',
  templateUrl: './ticket-slot-manager.component.html',
  styleUrls: ['./ticket-slot-manager.component.scss'],
  standalone: true,
  imports: [
    LayoutComponent,
    NavbarComponent,
    RouterLink,
    NgForOf,
    NgIf,
    AsyncPipe,
    LoadingComponent,
    NgClass,
    PopupErrorComponent,
    TransferTicketSlotComponent,
  ],
})
export class TicketSlotManagerComponent {
  // Nhận dữ liệu trực tiếp, không phải BehaviorSubject
  @Input() ticketSlotsByOrderData: TicketSlotByOrderModel | undefined | null;
  @Output() backToList = new EventEmitter<void>();
  @Output() ticketTransferred = new EventEmitter<void>();

  protected showQrCodeModal = new BehaviorSubject<boolean>(false);
  protected currentQrCodeImage = new BehaviorSubject<string | undefined>(undefined);
  protected currentQrCodeTicketSlot = new BehaviorSubject<TicketSlotResponse | undefined>(undefined);
  protected transferTicketSlot = new BehaviorSubject<
    { slot: TicketSlotResponse; state: TicketSlotStateResponse } | undefined
  >(undefined);

  @ViewChild('popupError') popupError!: PopupErrorComponent;

  constructor(private ticketSlotService: TicketSlotsService) {}

  protected readonly formatDate = formatDate;
  protected readonly visitDateLabel = visitDateLabel;

  protected openQrCode(ticketSlot: TicketSlotResponse, state: TicketSlotStateResponse): void {
    if (ticketSlot.optStatus === 'CLOSED' || ticketSlot.optStatus === 'NONE') {
      this.popupError.open('QR hiện chưa được kích hoạt. Vui lòng chờ đến sự kiện Offline.');
      return;
    }

    if (ticketSlot.optStatus !== 'OPENED') {
      this.popupError.open('Vé này không thể xem QR Code');
      return;
    }

    if (state.reason === 'TRANSFERRED_AWAY') {
      this.popupError.open('Vé này đã được chuyển sang người khác');
      return;
    }

    if (state.status === 'CREATED') {
      this.popupError.open('Vé này chưa có QR Code');
      return;
    }

    if (state.status == 'CHECKED_IN') {
      this.popupError.open('Vé này đã được check-in');
      return;
    }

    this.ticketSlotService.getQrCode(ticketSlot.id, state.stateIdx).subscribe({
      next: (qrCode) => {
        this.currentQrCodeImage.next('data:image/png;base64,' + qrCode);
        this.currentQrCodeTicketSlot.next(ticketSlot);
        this.showQrCodeModal.next(true);
      },
      error: (resp: HttpErrorResponse) => {
        console.error(resp);
        this.popupError.open((resp.error as string) ?? 'Lỗi tải QR Code');
      },
    });
  }

  protected closeQrCodeModal(): void {
    this.showQrCodeModal.next(false);
    this.currentQrCodeImage.next(undefined);
    this.currentQrCodeTicketSlot.next(undefined);
  }

  protected openPopupTransferTicketSlot(ticketSlot: TicketSlotResponse, state: TicketSlotStateResponse): void {
    this.transferTicketSlot.next({ slot: ticketSlot, state: state });
  }

  protected goBack(): void {
    this.backToList.emit();
  }

  protected onTransferSuccess(): void {
    this.ticketTransferred.emit();
  }
}
