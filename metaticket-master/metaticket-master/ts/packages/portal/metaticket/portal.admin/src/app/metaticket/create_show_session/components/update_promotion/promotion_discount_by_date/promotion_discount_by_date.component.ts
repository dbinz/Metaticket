import { Component, Input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

@Component({
    templateUrl: './promotion_discount_by_date.component.html',
    styleUrls: ['./promotion_discount_by_date.component.scss'],
    selector: 'promotion-discount-by-date',
})
export class PromotionDiscountByDateComponent {
    @Input() index: number;
    @Input() formGroup: FormGroup;
    discountOptions = [
        { value: 'DiscountPercent', label: 'Theo %' },
        { value: 'DiscountPrice', label: 'Theo giá tiền' },
    ];
    get nameFormControl() {
        return <FormControl>this.formGroup.get('name');
    }
    get dateFormControl() {
        return <FormControl>this.formGroup.get('date');
    }

    get discountOptionFormControl() {
        return <FormControl>this.formGroup.get('discountOption');
    }

    get discountValueFormControl() {
        return <FormControl>this.formGroup.get('discountValue');
    }
}
