import { Component, Input } from '@angular/core';
import { CollapseComponent } from 'src/app/lib/components';

@Component({
  selector: 'app-frag',
  standalone: true,
  imports: [CollapseComponent],
  templateUrl: './frag.component.html',
})
export class FragComponent {
  @Input() title!: string;
}
