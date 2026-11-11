import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ImageComponent } from '../image/image.component';
import { DropdownMenuButton } from 'src/app/lib/dto';
import { appIcons } from 'src/app/lib/theme';
import { NgClass, NgFor } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';

@Component({
  selector: 'app-dropdown-button',
  standalone: true,
  imports: [ImageComponent, NgFor, FormsModule, NzCheckboxModule, NzDropDownModule, NgClass, NzTypographyComponent],
  templateUrl: './dropdown-button.component.html',
})
export class DropdownButtonComponent {
  @Input({ required: true }) title!: string;
  @Input() iconLeft: string = '';
  @Input() iconRight: string = '';
  @Input({ required: true }) data: DropdownMenuButton[] = [];
  @Output() handlerOnSelected = new EventEmitter<DropdownMenuButton[]>();
  protected readonly appIcons = appIcons;
  protected displayTitle: string = '';
  protected choseItem: DropdownMenuButton[] = [];

  onChoseItems(item: DropdownMenuButton): void {
    if (item.value === 'all') {
      if (!item.check) {
        this.choseItem = [];
        this.displayTitle = this.choseItem.map((item) => item.title).join(', ');
        this.data?.forEach((it: DropdownMenuButton, idx: number) => {
          if (idx > 0) {
            it.check = false;
          }
        });
      } else {
        this.data?.forEach((it: DropdownMenuButton, idx: number) => {
          if (idx > 0) {
            it.check = true;
            // this.displayTile += this.displayTile === '' ? `${it?.title}` : `, ${it?.title}`;
            this.choseItem.push(it);
          }
        });
        this.displayTitle = this.choseItem.map((item) => item.title).join(', ');
      }
      this.handlerOnSelected.emit(this.choseItem);
    } else {
      if (!item.check) {
        this.choseItem = this.choseItem.filter((it) => it.value !== item.value);
      } else {
        this.choseItem.push(item);
      }
      this.displayTitle = this.choseItem.map((item) => item.title).join(', ');
      this.handlerOnSelected.emit(this.choseItem);
    }
  }
}
