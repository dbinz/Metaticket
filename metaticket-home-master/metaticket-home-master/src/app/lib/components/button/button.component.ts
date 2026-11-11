import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgClass, NgIf } from '@angular/common';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

@Component({
  selector: 'app-button',
  standalone: true,
  imports: [NzButtonModule, NzTypographyModule, NgIf, NgClass, NzIconModule],
  templateUrl: './button.component.html',
  styleUrl: './button.component.css',
})
export class ButtonComponent {
  @Input({ required: true }) title!: string;
  @Input() color:
    | 'primary'
    | 'white'
    | 'yellow'
    | 'transparent'
    | 'transparent-orange'
    | 'gray'
    | 'red'
    | 'green'
    | 'gray-event'
    | 'transparent-white'
    | 'transparent-gray' = 'primary';
  @Input() fontSize: 'default' | 'sm' | 'lg' = 'default';
  @Input() border: 'none' | 'gray' = 'none';
  @Input() py: 'py-3' | 'py-2' = 'py-3';
  @Input() disabled = false;
  @Input() isLoading = false;
  @Input() borderRadius: 'rounded-full' | 'rounded-xl' = 'rounded-full';
  @Input() isSelected: true | false = false;
  @Input() opacity: string = '';
  @Input() className: string = '';
  @Input() style: any;
  @Output() handlerOnPressed = new EventEmitter<void>();

  // Getter để tính toán class cho button
  getButtonClasses(): string[] {
    const colorMap = {
      primary: 'px-4 tablet:px-6 border-0 bg-blue-4867FC hover:bg-blue-072591',
      white: 'px-4 tablet:px-6 bg-white hover:bg-[#686868] text-black hover:text-white',
      yellow: 'px-12 border-0 bg-yellow-FED80B hover:bg-white',
      transparent: 'px-4 tablet:px-6 bg-transparent hover:bg-white hover:text-black text-white border border-white',
      'transparent-orange': 'px-4 tablet:px-6',
      // eslint-disable-next-line @typescript-eslint/naming-convention
      'transparent-gray': 'px-4 tablet:px-6 bg-transparent border hover:bg-blue-072591 text-black hover:text-white',
      gray: `px-4 tablet:px-6 bg-grey-F4F4F4 ${this.className}`,
      red: 'px-4 py-[5px] bg-[#fdd9d7] border-none',
      green: `px-4 py-[5px] bg-[#CCF4DD] hover:bg-[#00C853] border-none text-[#00A042] hover:text-white ${this.className}`,
      // eslint-disable-next-line @typescript-eslint/naming-convention
      'gray-event': 'px-16 bg-grey-F4F4F4 hover:bg-[#686868] border border-gray-C9C9C9 text-black hover:text-white',
      // eslint-disable-next-line @typescript-eslint/naming-convention
      'transparent-white': 'px-4 tablet:px-6 border bg-white',
    };

    const classes = [
      'w-full',
      'z-1',
      'h-auto',
      'flex',
      'items-center',
      'justify-center',
      this.py,
      this.borderRadius,
      colorMap[this.color],
    ];

    if (this.disabled || this.isLoading) {
      classes.push(this.opacity || 'opacity-60');
    }
    if (this.border === 'gray') {
      classes.push('border', 'border-gray-C9C9C9');
    }
    if (this.isSelected && this.color === 'transparent-gray') {
      classes.push('border-[#4867FC]');
    }

    return classes.filter(Boolean);
  }

  // Getter để tính toán class cho text
  getTextClasses(): string[] {
    const colorMap = {
      primary: 'text-white',
      white: '', // text-black được kế thừa
      yellow: 'text-black font-bold',
      transparent: '', // text-white được kế thừa
      'transparent-orange': '',
      // eslint-disable-next-line @typescript-eslint/naming-convention
      'transparent-gray': '', // text-black được kế thừa
      gray: 'text-black',
      red: 'text-[#C3362B]',
      green: '', // text-color được kế thừa
      // eslint-disable-next-line @typescript-eslint/naming-convention
      'gray-event': '', // text-black được kế thừa
      // eslint-disable-next-line @typescript-eslint/naming-convention
      'transparent-white': 'text-black',
    };

    const sizeMap = {
      default: 'text-base',
      sm: 'text-sm leading-6',
      lg: 'text-lg leading-6',
    };

    return ['font-medium', 'text-stroke', sizeMap[this.fontSize], colorMap[this.color]].filter(Boolean);
  }

  // Getter để tính toán class cho icon loading
  getIconClasses(): string[] {
    const colorMap = {
      primary: 'text-white',
      white: 'text-black',
      yellow: 'text-black',
      // eslint-disable-next-line @typescript-eslint/naming-convention
      'transparent-white': 'text-black',
    };
    return [this.fontSize, colorMap[this.color as keyof typeof colorMap] || 'text-white'].filter(Boolean);
  }

  onClick(): void {
    if (this.disabled || this.isLoading) return;
    this.handlerOnPressed.emit();
  }
}
