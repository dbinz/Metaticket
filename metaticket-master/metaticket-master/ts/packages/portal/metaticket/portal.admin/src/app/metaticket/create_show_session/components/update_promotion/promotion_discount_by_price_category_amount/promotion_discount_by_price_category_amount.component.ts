import { Component, Input } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { ApplyDiscountOnType } from '../edit_promotion_dialog/edit_promotion_dialog.component';

@Component({
    templateUrl: './promotion_discount_by_price_category_amount.component.html',
    styleUrls: ['./promotion_discount_by_price_category_amount.component.scss'],
    selector: 'promotion-discount-by-price-category-amount',
})
export class PromotionDiscountByPriceCategoryAmountComponent {
    @Input() index: number;
    @Input() formGroup: FormGroup;

    constructor(private formBuilder: FormBuilder) {}

    discountOptions = [
        { value: 'DiscountPercent', label: 'Theo %' },
        { value: 'DiscountPrice', label: 'Theo giá tiền' },
    ];

    applyDiscountOnOptions = [
        { value: ApplyDiscountOnType.TotalOrder, label: 'Tổng giá trị đơn hàng' },
        { value: ApplyDiscountOnType.MatchedItems, label: 'Giá trị các vé thỏa điều kiện' },
    ];

    get conditions() {
        return this.formGroup.get('priceCatConditions') as FormArray;
    }

    get conditionsControls() {
        return this.conditions.controls as FormGroup[];
    }

    addCondition() {
        this.conditions.push(this.formBuilder.group({
            priceCat: new FormControl('', Validators.required),
            minQuantity: new FormControl(0, Validators.required),
            maxQuantity: new FormControl(null)
        }));
    }

    removeCondition(i: number) {
        this.conditions.removeAt(i);
    }

    get discountOptionFormControl() {
        return <FormControl>this.formGroup.get('discountOption');
    }

    get discountValueFormControl() {
        return <FormControl>this.formGroup.get('discountValue');
    }

    get applyDiscountOnFormControl() {
        return <FormControl>this.formGroup.get('applyDiscountOn');
    }
}
