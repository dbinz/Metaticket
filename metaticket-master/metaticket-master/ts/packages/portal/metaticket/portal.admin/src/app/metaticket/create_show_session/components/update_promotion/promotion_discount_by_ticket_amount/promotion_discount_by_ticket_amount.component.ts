import { Component, Input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

@Component({
    templateUrl: './promotion_discount_by_ticket_amount.component.html',
    styleUrls: ['./promotion_discount_by_ticket_amount.component.scss'],
    selector: 'promotion-discount-by-ticket-amount',
})
export class PromotionDiscountByTicketAmountComponent {
    @Input() index: number;
    @Input() formGroup: FormGroup;

    discountOptions = [
        { value: 'DiscountPercent', label: 'Theo %' },
        { value: 'DiscountPrice', label: 'Theo giá tiền' },
    ];
    quantityOptions = [
        { value: 'Ticket', label: 'Số lượng theo vé' },
        { value: 'Combo', label: 'Sớ lượng theo đơn hàng' },
    ];
    get ticketAmountMinFormControl() {
        return <FormControl>this.formGroup.get('ticketAmountMin');
    }

    get ticketAmountMaxFormControl() {
        return <FormControl>this.formGroup.get('ticketAmountMax');
    }

    get discountOptionFormControl() {
        return <FormControl>this.formGroup.get('discountOption');
    }

    get discountValueFormControl() {
        return <FormControl>this.formGroup.get('discountValue');
    }

    get needCheckQuantityFormControl() {
        return <FormControl>this.formGroup.get('needCheckQuantity');
    }

    get originalQuantityFormControl() {
        return <FormControl>this.formGroup.get('originalQuantity');
    }

    get quantityOptionFormControl() {
        return <FormControl>this.formGroup.get('quantityOption');
    }
}
