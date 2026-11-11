import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { ButtonComponent } from 'src/app/lib/components/button/button.component';
import { FormatPricePipe } from 'src/app/lib/pipes';
import { AccessoryPendingOrderPromptService } from 'src/app/lib/services/accessory/accessory-pending-order-prompt.service';

/**
 * Blocking modal for an unpaid accessory hold discovered on product pages.
 * Hosts call {@link AccessoryPendingOrderPromptService.checkOnProductPage}.
 */
@Component({
  selector: 'app-accessory-pending-order-modal',
  standalone: true,
  imports: [CommonModule, NzTypographyComponent, ButtonComponent, FormatPricePipe],
  templateUrl: './accessory-pending-order-modal.component.html',
})
export class AccessoryPendingOrderModalComponent {
  readonly pending$ = this._prompt.pending$;
  readonly loadingAction$ = this._prompt.loadingAction$;

  constructor(private _prompt: AccessoryPendingOrderPromptService) {}

  /** Continue to payment for the unpaid hold. */
  onContinue(): void {
    this._prompt.continuePayment();
  }

  /** Cancel the unpaid hold on the server. */
  onCancel(): void {
    this._prompt.cancelPending();
  }
}
