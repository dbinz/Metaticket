import { Component, Input } from '@angular/core';
import { NbDialogRef } from '@nebular/theme';
import { QRCodeTicketInfoModel } from '../../model';

@Component({
    templateUrl: './popup.order-info.component.html',
    styleUrls: ['./popup.order-info.component.scss'],
    selector: 'popup-order-info',
})
export class PopupOrderInfoComponent {
    @Input() ticketInfo: QRCodeTicketInfoModel;

    constructor(public ref: NbDialogRef<PopupOrderInfoComponent>) {}

    buildSeatsList(seats: string[]): string {
        return seats.reduce((prev, curr) => prev.concat(', ').concat(curr));
    }
}
