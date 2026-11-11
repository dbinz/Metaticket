import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
    NbActionsModule,
    NbButtonModule,
    NbIconModule,
    NbListModule,
    NbRadioModule,
    NbSelectModule,
    NbTabsetModule,
    NbUserModule,
} from '@nebular/theme';

import { DashboardComponent } from './dashboard.component';
import { ThemeModule } from '../../@theme/theme.module';

@NgModule({
    imports: [
        FormsModule,
        ThemeModule,
        NbUserModule,
        NbButtonModule,
        NbTabsetModule,
        NbActionsModule,
        NbRadioModule,
        NbSelectModule,
        NbListModule,
        NbIconModule,
        NbButtonModule,
    ],
    declarations: [DashboardComponent],
})
export class DashboardModule {}
