import { AsyncPipe } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NbButtonModule, NbCardModule, NbInputModule, NbSpinnerModule } from '@nebular/theme';
import { DialogConfirmComponent } from './dialog.confirm/dialog.confirm.component';

@NgModule({
    imports: [NbCardModule, NbButtonModule, NbSpinnerModule, AsyncPipe, FormsModule, NbInputModule],
    declarations: [DialogConfirmComponent],
})
export class DialogModule {}
