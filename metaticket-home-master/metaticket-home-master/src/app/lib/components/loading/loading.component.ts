import { Component, Input, OnInit } from '@angular/core';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzIconModule } from 'ng-zorro-antd/icon';

@Component({
  selector: 'app-loading',
  standalone: true,
  imports: [NzSpinModule, NzIconModule],
  templateUrl: './loading.component.html',
})
export class LoadingComponent implements OnInit {
  @Input() isSpinning = true;
  @Input() size: 'default' | 'sm' | 'lg' | 'xl' = 'default';

  public fontSize = '';

  private _getFontSize(): void {
    this.fontSize = {
      default: 'text-base',
      sm: 'text-sm leading-6',
      lg: 'text-lg leading-6',
      xl: 'text-4xl',
    }[this.size];
  }

  ngOnInit(): void {
    this._getFontSize();
  }
}
