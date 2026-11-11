import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const validators = {
  required(message: string): ValidatorFn {
    return (control: AbstractControl): { [key: string]: string } | null => {
      const isValid = (control.value as string)?.trim()?.length > 0;
      return isValid ? null : { ['required']: message };
    };
  },

  regExp(key: string, pattern: RegExp, message: string): ValidatorFn {
    return (control: AbstractControl): { [key: string]: string } | null => {
      const isValid = pattern.test(control.value as string);
      return isValid ? null : { [key]: message };
    };
  },

  match(key: string, field: string, message: string): ValidatorFn {
    return (control: AbstractControl<string | null>): ValidationErrors | null => {
      const password: string | null = control.parent?.get(field)?.value as string;
      const confirmPassword: string | null = control?.value;

      return password === confirmPassword ? null : { [key]: message };
    };
  },
};
