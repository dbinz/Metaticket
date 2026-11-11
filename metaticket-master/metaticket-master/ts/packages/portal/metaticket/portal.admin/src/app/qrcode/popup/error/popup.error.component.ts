import { Component, Input } from '@angular/core';
import { NbDialogRef } from '@nebular/theme';
import { ScanOutcome } from '../../scan-result';

@Component({
    templateUrl: './popup.error.component.html',
    styleUrls: ['./popup.error.component.scss'],
    selector: 'popup-error',
})
export class PopupErrorComponent {
    @Input() outcome: Exclude<ScanOutcome, 'valid'> = 'rejected';
    @Input() title = 'Vé không hợp lệ';
    @Input() message: string;
    /** HTTP status for support staff; 0 or unset hides it. */
    @Input() status?: number;

    constructor(public ref: NbDialogRef<PopupErrorComponent>) {}

    get icon(): string {
        switch (this.outcome) {
            case 'used':
                return 'eva-alert-triangle-outline';
            case 'system':
                return 'eva-wifi-off-outline';
            default:
                return 'eva-close-circle-outline';
        }
    }
}
