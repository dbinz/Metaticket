import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NgClass, NgForOf, NgIf } from '@angular/common';
import { ImageComponent } from '../image/image.component';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { DropdownMenuItem } from '../../dto';
import { appIcons } from '../../theme';
import { NzButtonComponent } from 'ng-zorro-antd/button';
import { NzWaveDirective } from 'ng-zorro-antd/core/wave';

@Component({
  selector: 'app-dropdown',
  standalone: true,
  imports: [
    NzDropDownModule,
    NzTypographyModule,
    NgForOf,
    ImageComponent,
    NgIf,
    NzIconModule,
    NgClass,
    NzButtonComponent,
    NzWaveDirective,
  ],
  templateUrl: './app-dropdown.component.html',
})
export class AppDropdownComponent {
  @Input({ required: true }) title!: string;
  @Input() isLoading = false;
  @Input() data: DropdownMenuItem[] = [];
  @Output() handlerOnSelected = new EventEmitter<DropdownMenuItem>();
  @Input() iconLeft: string = '';
  @Input() iconRight: string = '';
  @Input() py: 'py-3' | 'py-2' = 'py-3';
  @Input() disabled = false;
  @Input() borderRadius: 'rounded-full' | 'rounded-xl' = 'rounded-full';

  protected readonly appIcons = appIcons;
  protected displayTitle: string = '';

  onSelected(item: DropdownMenuItem): void {
    this.handlerOnSelected?.emit(item);
    this.displayTitle = item.title;
  }
}
