import { Component, Input } from '@angular/core';
import { NzImageModule } from 'ng-zorro-antd/image';

@Component({
  selector: 'app-image',
  standalone: true,
  imports: [NzImageModule],
  templateUrl: './image.component.html',
})
export class ImageComponent {
  @Input({ required: true }) src!: string;
  @Input() sx?: string;
  @Input() disablePreview = true;
}
