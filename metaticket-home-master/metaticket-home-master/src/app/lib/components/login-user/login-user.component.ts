import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { routeName } from '../../constants';
import { ButtonComponent } from '../button/button.component';
import { FormInputComponent } from '../form-input/form-input.component';

@Component({
  selector: 'app-login-user',
  templateUrl: './login-user.component.html',
  standalone: true,
  imports: [NzTypographyModule, ButtonComponent, RouterLink, NzFormModule, ReactiveFormsModule, FormInputComponent],
})
export class LoginUserComponent implements OnInit {
  @Output() typeChange = new EventEmitter<'login' | 'register' | 'otp'>();
  protected readonly routeName = routeName;
  public formGroup!: FormGroup<{
    phoneNumber: FormControl<string | null>;
    password: FormControl<string | null>;
  }>;
  constructor(private _fb: FormBuilder) {}
  changeValue(newValue: 'login' | 'register' | 'otp'): void {
    this.typeChange.emit(newValue);
  }
  // public type: 'login' | 'register' = 'login';

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
    });
  }

  ngOnInit(): void {
    this._initialForm();
  }

  public login(): void {
    if (this.formGroup.valid) {
      console.log('submit', this.formGroup.value);
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
