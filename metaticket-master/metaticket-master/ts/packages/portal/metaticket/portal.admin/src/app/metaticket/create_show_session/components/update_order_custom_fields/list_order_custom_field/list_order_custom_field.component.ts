import { Component, Input } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';

@Component({
    templateUrl: './list_order_custom_field.component.html',
    styleUrls: ['./list_order_custom_field.component.scss'],
    selector: 'list-order-custom-field',
})
export class ListOrderCustomFieldComponent {
    @Input() index: number;
    @Input() formGroup: FormGroup;

    constructor(private formBuilder: FormBuilder) {}
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

    getConstraintsOptions() {
        return (this.constraintsFormGroup.get('options') as FormArray).controls as FormGroup[];
    }

    getConstraintsOptionNameFormControl(itemForm: FormGroup) {
        return <FormControl>itemForm.get('name');
    }

    getConstraintsOptionValueFormControl(itemForm: FormGroup) {
        return <FormControl>itemForm.get('value');
    }

    addOptionItem() {
        (<FormArray>this.constraintsFormGroup.controls['options']).push(
            this.formBuilder.group({
                name: new FormControl('', Validators.required),
                value: new FormControl('', Validators.required),
            }),
        );
    }

    removeItem(index: number) {
        (this.constraintsFormGroup.get('options') as FormArray).removeAt(index);
    }
}
