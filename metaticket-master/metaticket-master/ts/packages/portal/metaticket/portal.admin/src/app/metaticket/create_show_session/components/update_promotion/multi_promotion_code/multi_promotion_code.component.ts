import { AfterViewInit, Component, Input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { NbTagComponent, NbTagInputAddEvent } from '@nebular/theme';

@Component({
    templateUrl: './multi_promotion_code.component.html',
    styleUrls: ['./multi_promotion_code.component.scss'],
    selector: 'multi-promotion-code',
})
export class MultiPromotionCodeComponent implements AfterViewInit {
    @Input() index: number;
    @Input() formGroup: FormGroup;

    tags: Set<string> = new Set([]);

    discountOptions = [
        { value: 'DiscountPercent', label: 'Khuyến mãi theo %' },
        { value: 'DiscountPrice', label: 'Khuyến mãi theo giá tiền' },
    ];

    ngAfterViewInit(): void {
        const tags: string = this.codesFormControl.getRawValue();
        if (tags.length > 0) {
            const tagsArr = tags.split(',');
            for (let i = 0; i < tagsArr.length; i++) {
                this.tags.add(tagsArr[i]);
            }
        }
    }

    get nameFormControl() {
        return <FormControl>this.formGroup.get('name');
    }

    get codesFormControl() {
        return <FormControl>this.formGroup.get('codes');
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

    onTagRemove(tagToRemove: NbTagComponent): void {
        this.tags.delete(tagToRemove.text);

        let codes = '';
        for (const tag of this.tags) {
            if (codes.length === 0) {
                codes = tag;
            } else {
                codes += `,${tag}`;
            }
        }
        this.codesFormControl.patchValue(codes);
    }

    onTagAdd({ value, input }: NbTagInputAddEvent): void {
        if (value) {
            this.tags.add(value);
        }
        input.nativeElement.value = '';
        let codes = '';
        for (const tag of this.tags) {
            if (codes.length === 0) {
                codes = tag;
            } else {
                codes += `,${tag}`;
            }
        }
        this.codesFormControl.patchValue(codes);
    }
}
