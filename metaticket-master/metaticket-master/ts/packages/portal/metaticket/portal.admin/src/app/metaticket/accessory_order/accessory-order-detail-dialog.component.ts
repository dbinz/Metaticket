import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, Input } from '@angular/core';
import { NbDialogRef } from '@nebular/theme';
import { AccessoryOrderSummaryModel } from '../../@core/metaticket/model';

const STATUS_LABELS: Record<string, string> = {
    Created: 'Đã tạo',
    WaitingForPayment: 'Chờ thanh toán',
    PaymentProcessing: 'Đang thanh toán',
    PaymentSucceed: 'Đã thanh toán',
    PaymentFailed: 'Thanh toán thất bại',
    PaymentCancelled: 'Đã hủy',
    Expired: 'Hết hạn',
};

const FULFILLMENT_LABELS: Record<string, string> = {
    PickupAtShow: 'Nhận tại show',
    MetaTicketPickup: 'Nhận tại META Ticket',
    Delivery: 'Giao hàng',
};

@Component({
    selector: 'accessory-order-detail-dialog',
    templateUrl: './accessory-order-detail-dialog.component.html',
    styleUrls: ['./accessory-order-detail-dialog.component.scss'],
    providers: [DecimalPipe, DatePipe],
})
export class AccessoryOrderDetailDialogComponent {
    @Input() order!: AccessoryOrderSummaryModel;

    constructor(
        public ref: NbDialogRef<AccessoryOrderDetailDialogComponent>,
        private decimalPipe: DecimalPipe,
        private datePipe: DatePipe,
    ) {}

    /** Closes the dialog. */
    close(): void {
        this.ref.close();
    }

    /** Formats VND amounts. */
    formatVnd(amount: number): string {
        return (this.decimalPipe.transform(amount, '1.0-0') || '0') + ' ₫';
    }

    /** Formats order created timestamp. */
    formatTime(epochMs: number): string {
        return this.datePipe.transform(epochMs, 'dd/MM/yyyy HH:mm', '+0700') || String(epochMs);
    }

    get statusLabel(): string {
        return STATUS_LABELS[this.order?.status] || this.order?.status || '';
    }

    get fulfillmentLabel(): string {
        return FULFILLMENT_LABELS[this.order?.fulfillment] || this.order?.fulfillment || '';
    }

    get isDelivery(): boolean {
        return this.order?.fulfillment === 'Delivery';
    }

    get isMetaTicketPickup(): boolean {
        return this.order?.fulfillment === 'MetaTicketPickup';
    }

    get isPickupAtShow(): boolean {
        return this.order?.fulfillment === 'PickupAtShow';
    }

    /** Line total = unitPrice × quantity. */
    lineTotal(unitPrice: number, quantity: number): number {
        return unitPrice * quantity;
    }
}
