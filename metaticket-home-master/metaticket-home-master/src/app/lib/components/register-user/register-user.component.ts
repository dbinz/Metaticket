import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { ButtonComponent } from '../button/button.component';
import { RouterLink } from '@angular/router';
import { NzFormModule } from 'ng-zorro-antd/form';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { FormInputComponent } from '../form-input/form-input.component';
import { routeName } from '../../constants';
import { FormInputCheckBoxComponent } from '../form-input-checkbox/form-input-checkbox.component';

@Component({
  selector: 'app-register-user',
  templateUrl: './register-user.component.html',
  standalone: true,
  imports: [
    NzTypographyModule,
    ButtonComponent,
    RouterLink,
    NzFormModule,
    ReactiveFormsModule,
    FormInputComponent,
    FormInputCheckBoxComponent,
  ],
})
export class RegisterUserComponent implements OnInit {
  protected readonly routeName = routeName;
  @Output() typeChange = new EventEmitter<'login' | 'register' | 'otp'>();
  public formGroup!: FormGroup<{
    phoneNumber: FormControl<string | null>;
    password: FormControl<string | null>;
    confirmPassword: FormControl<string | null>;
    checked: FormControl<boolean | null>;
  }>;
  constructor(private _fb: FormBuilder) {}
  private _initialForm(): void {
    this.formGroup = this._fb.group({
      phoneNumber: [
        '',
        // Validators.compose([
        //   // eslint-disable-next-line @typescript-eslint/unbound-method
        //   Validators.required,
        //   Validators.pattern(validationRegex.phoneNumber),
        // ]),
      ],
      password: [
        '',
        // Validators.compose([
        //   // eslint-disable-next-line @typescript-eslint/unbound-method
        //   Validators.required,
        //   Validators.minLength(6),
        // ]),
      ],
      confirmPassword: [
        '',
        // Validators.compose([
        //   // eslint-disable-next-line @typescript-eslint/unbound-method
        //   Validators.required,
        //   Validators.minLength(6),
        // ]),
      ],
      checked: [false],
    });
  }

  ngOnInit(): void {
    this._initialForm();
  }
  changeValue(newValue: 'login' | 'register' | 'otp'): void {
    this.typeChange.emit(newValue);
  }
  public register(): void {
    if (this.formGroup.valid) {
      console.log('submit', this.formGroup.value);
      this.changeValue('otp');
      return;
    }

    Object.values(this.formGroup.controls).forEach((control) => {
      if (control.invalid) {
        control.markAsDirty();
        control.updateValueAndValidity({ onlySelf: true });
      }
    });
  }
}
