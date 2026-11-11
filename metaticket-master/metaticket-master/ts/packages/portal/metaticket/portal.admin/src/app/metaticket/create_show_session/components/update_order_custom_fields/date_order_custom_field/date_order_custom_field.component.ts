import { Component, Input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

@Component({
    templateUrl: './date_order_custom_field.component.html',
    styleUrls: ['./date_order_custom_field.component.scss'],
    selector: 'date-order-custom-field',
})
export class DateOrderCustomFieldComponent {
    @Input() index: number;
    @Input() formGroup: FormGroup;

    get idFormControl() {
        return <FormControl>this.formGroup.get('id');
    }

    get displayNameFormControl() {
        return <FormControl>this.formGroup.get('displayName');
    }

    get noteFormControl() {
        return <FormControl>this.formGroup.get('note');
    }

    get constraintsFormGroup() {
        return <FormGroup>this.formGroup.get('constraints');
    }

    get requireConstraintFormControl() {
        return <FormControl>this.constraintsFormGroup.get('require');
    }
}
