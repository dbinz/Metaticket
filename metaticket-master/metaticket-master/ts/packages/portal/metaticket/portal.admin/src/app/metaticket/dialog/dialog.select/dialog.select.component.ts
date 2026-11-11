import { Component, Input, OnInit } from '@angular/core';
import { NbButtonModule, NbCardModule, NbDialogRef, NbOptionModule, NbSelectModule } from '@nebular/theme';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

export interface DialogSelectOption {
    value: any;
    title: string;
}

@Component({
    selector: 'dialog-select',
    templateUrl: './dialog.select.component.html',
    standalone: true,
    imports: [CommonModule, FormsModule, NbCardModule, NbSelectModule, NbOptionModule, NbButtonModule],
})
export class DialogSelectComponent implements OnInit {
    @Input() title: string;
    @Input() options: DialogSelectOption[] = [];
    @Input() selectedValue?: any; // Optional: for pre-selecting an option

    protected selectedOptionValue: any;

    constructor(public ref: NbDialogRef<DialogSelectComponent>) {}

    ngOnInit() {
        if (this.selectedValue !== undefined) {
            this.selectedOptionValue = this.selectedValue;
        }
    }

    protected onOk() {
        this.ref.close(this.selectedOptionValue);
    }

    protected onClose() {
        this.ref.close(); // Passing no argument means undefined will be returned
    }
}
