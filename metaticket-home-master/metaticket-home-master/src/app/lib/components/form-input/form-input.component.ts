import { Component, Input, OnInit, Optional, Self } from '@angular/core';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzInputDirective } from 'ng-zorro-antd/input';
import { ControlValueAccessor, FormControl, NgControl, ReactiveFormsModule } from '@angular/forms';
import { NgClass, NgIf } from '@angular/common';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NzIconModule } from 'ng-zorro-antd/icon';

@Component({
  selector: 'app-form-input',
  standalone: true,
  imports: [NzFormModule, NzInputDirective, NgIf, NgClass, ReactiveFormsModule, NzTypographyModule, NzIconModule],
  templateUrl: './form-input.component.html',
  styleUrls: ['./form-input.component.scss'],
})
export class FormInputComponent implements ControlValueAccessor, OnInit {
  @Input({ required: true }) label!: string;
  @Input() type: 'text' | 'password' = 'text';
  @Input() placeholder = '';
  @Input() fontSize: 'sm' | 'base' = 'base';
  @Input() formControl = new FormControl();
  @Input() readOnly: boolean = false;

  errorCode: string | null = null;
  isRequired: boolean = false;
  passwordVisible: boolean = false;

  get inputType(): string {
    return this.type === 'password' && this.passwordVisible ? 'text' : this.type;
  }

  togglePasswordVisibility(): void {
    this.passwordVisible = !this.passwordVisible;
  }

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
    this.formControl.valueChanges.subscribe(() => {
      const controlErrors = this.formControl.errors;
      if (controlErrors != null) {
        const errorCodes = Object.keys(controlErrors).map((errorCode) => errorCode);
        if (errorCodes.length > 0) {
          this.errorCode = errorCodes[0];
          return;
        }

        this.errorCode = null;
      }
    });
  }
}
