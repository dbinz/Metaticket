import { Component, Input, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NbButtonModule, NbCardModule, NbDialogRef, NbInputModule } from '@nebular/theme';

@Component({
    selector: 'dialog-input-string',
    templateUrl: './dialog.input.string.component.html',
    standalone: true,
    imports: [NbCardModule, FormsModule, NbButtonModule, NbInputModule],
})
export class DialogInputStringComponent implements OnInit {
    @Input() title: string;
    @Input() dataType: 'text' | 'number' = 'text';
    @Input() inputData: string | number = '';
    @Input() validateFn: (value: string | number) => boolean = (value: string | number) => true;

    isInputValid: boolean = true;

    constructor(public ref: NbDialogRef<DialogInputStringComponent>) {}

    ngOnInit() {
        this.checkInputValidity();
    }

    onInputDataChange() {
        this.checkInputValidity();
    }

    private checkInputValidity() {
        this.isInputValid = this.validateFn(this.inputData);
    }

    onOk() {
        this.ref.close(this.inputData);
    }

    onClose() {
        this.ref.close();
    }
}
