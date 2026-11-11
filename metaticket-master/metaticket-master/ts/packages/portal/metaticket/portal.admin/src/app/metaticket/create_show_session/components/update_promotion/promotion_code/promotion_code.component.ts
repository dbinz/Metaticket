import { Component, Input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

@Component({
    templateUrl: './promotion_code.component.html',
    styleUrls: ['./promotion_code.component.scss'],
    selector: 'promotion-code',
})
export class PromotionCodeComponent {
    @Input() index: number;
    @Input() formGroup: FormGroup;

    discountOptions = [
        { value: 'DiscountPercent', label: 'Khuyến mãi theo %' },
        { value: 'DiscountPrice', label: 'Khuyến mãi theo giá tiền' },
    ];
    get nameFormControl() {
        return <FormControl>this.formGroup.get('name');
    }

    get codeFormControl() {
        return <FormControl>this.formGroup.get('code');
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
}
