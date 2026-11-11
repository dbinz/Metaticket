import { Component, Input, OnInit, Optional, Self } from '@angular/core';
import { ControlValueAccessor, FormControl, NgControl, ReactiveFormsModule } from '@angular/forms';
import { NzColDirective, NzRowDirective } from 'ng-zorro-antd/grid';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NgClass, NgIf } from '@angular/common';

@Component({
  selector: 'app-form-checkbox',
  standalone: true,
  imports: [NzColDirective, NzCheckboxModule, NzFormModule, NzRowDirective, NgIf, NgClass, ReactiveFormsModule],
  templateUrl: './form-checkbox.component.html',
})
export class FormCheckboxComponent implements ControlValueAccessor, OnInit {
  @Input({ required: true }) label!: string;
  @Input() fontSize: 'sm' | 'base' = 'base';
  @Input() formControl = new FormControl();

  isRequired: boolean = false;

  constructor(@Optional() @Self() public ngControl: NgControl) {
    if (this.ngControl != null) {
      this.ngControl.valueAccessor = this;
    }
  }

  writeValue(): void {}
  registerOnChange(): void {}
  registerOnTouched(): void {}
  setDisabledState?(): void {}

  ngOnInit(): void {
    // Check if the form control has required validator
    const validators = this.formControl.validator;
    if (validators) {
      // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
      const validationResult = validators({ value: null } as any);
      // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
      this.isRequired = validationResult && validationResult['required'];
    } else {
      this.isRequired = false;
    }
    this.formControl.setValue(false);
  }
}
