import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { ImageComponent } from '../image/image.component';
import { appIcons } from 'src/app/lib/theme';
import { Clipboard } from '@angular/cdk/clipboard';
import { OrderService } from 'src/app/lib/services';

@Component({
  selector: 'data-collection-input',
  standalone: true,
  imports: [NzTypographyComponent, ImageComponent],
  templateUrl: './data-collection-input.component.html',
})
export class DataCollectionInputComponent implements OnInit, OnDestroy {
  @Input({ required: true }) label!: string;
  @Input({ required: true }) odcLink!: string;
  @Input() fontSize: 'sm' | 'base' = 'base';
  @Input() isRequired = false;
  @Output() handlerOnValidOdcLink = new EventEmitter<string>();

  protected readonly appIcons = appIcons;
  isValid = false;
  interval?: ReturnType<typeof setInterval>;

  constructor(
    private _clipboard: Clipboard,
    private _orderService: OrderService,
  ) {}

  onCopyClipboard(): void {
    this._clipboard.copy(this.odcLink);
  }

  onOpenOdcLink(): void {
    window.open(this.odcLink);
  }

  ngOnInit(): void {
    this.interval = setInterval(() => {
      this._orderService.validateOdcLink(this.odcLink).subscribe({
        next: (res) => {
          if (res === '"VALID"') {
            clearInterval(this.interval);
            this.isValid = true;
            this.handlerOnValidOdcLink.emit(this.odcLink);
          }
        },
        error: (err) => {
          console.log('validate od link error', err);
        },
      });
    }, 2 * 1000);
  }

  ngOnDestroy(): void {
    clearInterval(this.interval);
  }
}
