import { Component, Input } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

@Component({
    templateUrl: './number_order_custom_field.component.html',
    styleUrls: ['./number_order_custom_field.component.scss'],
    selector: 'number-order-custom-field',
})
export class NumberOrderCustomFieldComponent {
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

    get regexConstraintFormControl() {
        return <FormControl>this.constraintsFormGroup.get('regex');
    }
}
