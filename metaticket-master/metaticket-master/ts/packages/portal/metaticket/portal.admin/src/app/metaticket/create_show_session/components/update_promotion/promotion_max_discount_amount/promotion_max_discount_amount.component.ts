import { Component, Input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

@Component({
    templateUrl: './promotion_max_discount_amount.component.html',
    styleUrls: ['./promotion_max_discount_amount.component.scss'],
    selector: 'promotion-max-discount-amount',
})
export class PromotionMaxDiscountAmountComponent {
    @Input() index: number;
    @Input() formGroup: FormGroup;

    get maxDiscountTotalFormControl() {
        return <FormControl>this.formGroup.get('maxDiscountTotal');
    }

    get maxDiscountByDateFormControl() {
        return <FormControl>this.formGroup.get('maxDiscountByDate');
    }

    get maxDiscountByTicketAmountFormControl() {
        return <FormControl>this.formGroup.get('maxDiscountByTicketAmount');
    }

    get maxDiscountByPromotionCodeFormControl() {
        return <FormControl>this.formGroup.get('maxDiscountByPromotionCode');
    }

    get enablePerTypeFormControl() {
        return <FormControl>this.formGroup.get('enablePerType');
    }
}
