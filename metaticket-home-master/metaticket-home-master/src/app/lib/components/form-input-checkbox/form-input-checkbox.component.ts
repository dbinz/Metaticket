import { Component, Input, OnInit, Optional, Self } from '@angular/core';
import { NzFormControlComponent, NzFormItemComponent, NzFormLabelComponent } from 'ng-zorro-antd/form';
import { NzInputDirective } from 'ng-zorro-antd/input';
import { ControlValueAccessor, FormControl, NgControl, ReactiveFormsModule } from '@angular/forms';
import { NgIf } from '@angular/common';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

@Component({
  selector: 'app-form-input-checkbox',
  standalone: true,
  imports: [
    NzFormControlComponent,
    NzFormItemComponent,
    NzFormLabelComponent,
    NzInputDirective,
    NgIf,
    ReactiveFormsModule,
    NzTypographyModule,
  ],
  templateUrl: './form-input-checkbox.component.html',
})
export class FormInputCheckBoxComponent implements ControlValueAccessor, OnInit {
  @Input() formControl = new FormControl();
  @Input() label!: string;

  public isValid = true;

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
    this.formControl.statusChanges.subscribe((): void => {
      this.isValid = this.formControl.valid;
    });
  }
}
