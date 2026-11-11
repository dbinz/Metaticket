import { Component, EventEmitter, Input, OnInit, Output, TemplateRef } from '@angular/core';
import { NzInputDirective, NzInputGroupComponent, NzInputGroupWhitSuffixOrPrefixDirective } from 'ng-zorro-antd/input';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-input',
  standalone: true,
  imports: [
    NzInputDirective,
    NzInputGroupComponent,
    NzInputGroupWhitSuffixOrPrefixDirective,
    ReactiveFormsModule,
    NgIf,
    FormsModule,
  ],
  templateUrl: './input.component.html',
})
export class InputComponent implements OnInit {
  @Input() id = '';
  @Input() placeholder = '';
  @Input() value = '';
  @Input() fontSize: 'sm' | 'default' | 'lg' = 'default';
  @Input() border: 'none' | 'default' = 'default';
  @Input() type: 'text' | 'password' = 'text';
  @Input() suffixIcon?: TemplateRef<void>;
  @Output() handlerOnChanged = new EventEmitter<string>();

  public textSize = '';
  public inputBorder = '';

  private _getTextSize(): void {
    this.textSize = {
      default: 'text-base font-medium',
      sm: 'text-sm leading-6 font-medium',
      lg: 'text-lg leading-6 font-medium',
    }[this.fontSize];
  }

  private _getInputBorder(): void {
    this.inputBorder = {
      default: 'border border-gray-C9C9C9',
      none: 'border-0',
    }[this.border];
  }

  onInputChange(event?: Event): void {
    // Handle browser autocomplete by getting value from event target if available
    if (event && event.target) {
      this.value = (event.target as HTMLInputElement).value;
    }
    this.handlerOnChanged.emit(this.value);
  }

  ngOnInit(): void {
    this._getTextSize();
    this._getInputBorder();
  }
}
