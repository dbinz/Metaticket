import { Component, Input } from '@angular/core';
import { NbDialogRef } from '@nebular/theme';
import { ReplaySubject } from 'rxjs';

@Component({
    templateUrl: './dialog.confirm.component.html',
    selector: 'dialog-confirm',
})
export class DialogConfirmComponent {
    @Input() title: string;
    @Input() content: string;
    @Input() loading$: ReplaySubject<boolean>;
    @Input() onAction$: ReplaySubject<'confirm' | 'close' | undefined>;

    constructor(public ref: NbDialogRef<DialogConfirmComponent>) {}

    cancel() {
        this.onAction$?.next(undefined);
        this.ref.close();
    }

    confirm() {
        this.onAction$?.next('confirm');

        if (this.loading$)
            this.loading$.subscribe(loading => {
                if (!loading) this.cancel();
            });
        else this.ref.close('confirm');
    }
}
