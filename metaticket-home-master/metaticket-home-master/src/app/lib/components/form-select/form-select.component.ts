import { Component, HostListener, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { FormControl, FormsModule } from '@angular/forms';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NgClass, NgForOf, NgIf, NgTemplateOutlet } from '@angular/common';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { InputComponent } from '../input/input.component';
import { ImageComponent } from '../image/image.component';
import { appIcons } from 'src/app/lib/theme';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { SelectOptionItem } from 'src/app/lib/dto';

@Component({
  selector: 'app-form-select',
  standalone: true,
  imports: [
    NzFormModule,
    NgIf,
    NgClass,
    NzSelectModule,
    NgTemplateOutlet,
    InputComponent,
    ImageComponent,
    FormsModule,
    NzTypographyModule,
    NgForOf,
  ],
  templateUrl: './form-select.component.html',
})
export class FormSelectComponent implements OnInit, OnChanges {
  @Input({ required: true }) label!: string;
  @Input({ required: true }) options!: SelectOptionItem[];
  @Input() placeholder = '';
  @Input() fontSize: 'sm' | 'base' = 'base';
  @Input() formControl = new FormControl();
  @Input() idSelectInput: string = 'select-input';
  public readonly appIcons = appIcons;
  showSelect = false;
  value?: string;
  data: SelectOptionItem[] = [];
  errorCode: string | null = null;
  isRequired: boolean = false;

  @HostListener('document:click', ['$event.target'])
  onClick(target: HTMLElement): void {
    // Check if click is on input field
    if (target.id.includes(this.idSelectInput)) {
      this.showSelect = true;
      return;
    }

    // Check if click is on dropdown icon or dropdown content
    const isDropdownIcon = target.closest('app-image');
    const isDropdownContent = target.closest('.absolute');

    if (isDropdownIcon || isDropdownContent) {
      return; // Don't close dropdown
    }

    // Close dropdown if click is outside
    this.showSelect = false;
  }

  /** Lowercased, diacritic-stripped form for accent-insensitive search (e.g. "ha noi" matches "Hà Nội"). */
  private _normalize(value: string): string {
    return value.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd');
  }

  /** Keeps the filtered dropdown list in sync when parent swaps option lists (e.g. wards by province). */
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['options'] && this.options) {
      const typed = this.value?.trim() ? this._normalize(this.value.trim()) : '';
      this.data = typed ? this.options.filter((item) => this._normalize(item.name).includes(typed)) : this.options;
    }
  }

  onSelectItem(item: SelectOptionItem): void {
    this.value = item.name;
    this.formControl.setValue(item.value);
    this.showSelect = false;
  }

  toggleDropdown(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.showSelect = !this.showSelect;
  }

  onChangeInput(value: string): void {
    this.value = value;
    const typed = this._normalize(value.trim());
    this.data = this.options.filter((item) => this._normalize(item.name).includes(typed));

    // Check if the input value exactly matches an option
    const exactMatch = this.options.find((item) => item.name.toLowerCase() === value.trim().toLowerCase());
    if (exactMatch) {
      this.formControl.setValue(exactMatch.value);
    } else {
      // If no exact match, set the raw input value to form control
      this.formControl.setValue(value.trim());
    }
  }

  ngOnInit(): void {
    this.data = this.options;
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

    // Initialize display value from form control
    const initialValue: string = this.formControl.value as string;
    if (initialValue) {
      const matchedOption = this.options.find((item) => item.value === initialValue);
      if (matchedOption) {
        this.value = matchedOption.name;
      } else {
        this.value = initialValue;
      }
    }

    this.formControl.valueChanges.subscribe((newValue: string) => {
      // Update display value when form control changes
      if (newValue) {
        const matchedOption = this.options.find((item) => item.value === newValue);
        if (matchedOption) {
          this.value = matchedOption.name;
        } else {
          this.value = newValue;
        }
      } else {
        this.value = '';
      }

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
