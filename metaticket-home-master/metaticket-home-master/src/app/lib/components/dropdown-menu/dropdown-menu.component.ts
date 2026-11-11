import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { appIcons } from 'src/app/lib/theme';
import { NgClass, NgForOf, NgIf } from '@angular/common';
import { DropdownMenuItem } from 'src/app/lib/dto';
import { ImageComponent } from '../image/image.component';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-dropdown-menu',
  standalone: true,
  imports: [NzDropDownModule, NzTypographyModule, NgForOf, ImageComponent, NgIf, NzIconModule, NgClass, RouterLink],
  templateUrl: './dropdown-menu.component.html',
})
export class DropdownMenuComponent {
  @Input({ required: true }) title!: string;
  @Input() isLoading = false;
  @Input() iconRight: string = '';
  @Input() data: DropdownMenuItem[] = [];
  @Output() handlerOnSelected = new EventEmitter<DropdownMenuItem>();
  @Input() displayTitle: string = '';

  protected readonly appIcons = appIcons;

  onSelected(item: DropdownMenuItem): void {
    this.handlerOnSelected?.emit(item);
  }
}
