import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { DropdownMenuItem } from '../../dto';
import { NgClass, NgFor, NgIf } from '@angular/common';
import { appIcons } from '../../theme';
import { ImageComponent } from '../image/image.component';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-collapse-menu',
  standalone: true,
  imports: [NzCollapseModule, NgFor, NgIf, NgClass, ImageComponent, RouterLink],
  templateUrl: './collapse-menu.component.html',
  styleUrl: './collapse-menu.component.css',
})
export class CollapseMenuComponent {
  @Input({ required: true }) title!: string;
  @Input() isLoading = false;
  @Input() data: DropdownMenuItem[] = [];
  @Output() handlerOnSelected = new EventEmitter<DropdownMenuItem>();
  @Input() displayTitle: string = '';
  protected active: boolean = false;
  protected appIcons = appIcons;
  expandCollapse(): void {
    this.active = !this.active;
  }
  onSelected(item: DropdownMenuItem): void {
    this.handlerOnSelected.emit(item);
  }
}
