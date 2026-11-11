import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { NgClass, NgForOf, NgIf } from '@angular/common';
import { ImageComponent } from '../image/image.component';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { DropdownMenuItem } from '../../dto';
import { appIcons } from '../../theme';

@Component({
  selector: 'app-single-dropdown',
  standalone: true,
  imports: [NzDropDownModule, NzTypographyModule, NgForOf, ImageComponent, NgIf, NzIconModule, NgClass],
  templateUrl: './single-dropdown.component.html',
})
export class SingleDropdownComponent {
  @Input({ required: true }) title!: string;
  @Input() isLoading = false;
  @Input() data: DropdownMenuItem[] = [];
  @Output() handlerOnSelected = new EventEmitter<DropdownMenuItem>();
  @Input() iconLeft: string = '';
  @Input() iconRight: string = '';

  protected readonly appIcons = appIcons;
  protected displayTitle: string = '';

  onSelected(item: DropdownMenuItem): void {
    this.handlerOnSelected?.emit(item);
    this.displayTitle = item.title;
  }
}
