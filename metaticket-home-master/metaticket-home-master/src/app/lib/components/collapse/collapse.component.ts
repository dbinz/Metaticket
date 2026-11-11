import { NgClass } from '@angular/common';
import { Component, Input } from '@angular/core';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { ImageComponent } from '../image/image.component';
import { appIcons } from '../../theme';

@Component({
  selector: 'app-collapse',
  standalone: true,
  imports: [NgClass, NzIconModule, ImageComponent],
  templateUrl: './collapse.component.html',
  styleUrls: ['./collapse.component.css'],
})
export class CollapseComponent {
  protected active: boolean = false;
  @Input() question!: string;
  protected readonly appIcons = appIcons;
  expandCollapse(): void {
    this.active = !this.active;
  }
}
