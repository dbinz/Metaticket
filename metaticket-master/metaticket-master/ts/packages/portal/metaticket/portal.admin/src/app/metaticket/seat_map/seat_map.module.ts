import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';
import { NbButtonModule, NbCardModule, NbIconModule, NbInputModule, NbSpinnerModule } from '@nebular/theme';
import { EditorUILoaderModule } from '@viclass/editorui.loader';
import { Angular2SmartTableModule } from 'angular2-smart-table';
import { SeatMapListComponent } from './list/seat_map_list.component';
import { SeatMapDrawComponent } from './draw/seat_map_draw.component';
import { MetaticketAuthGuardService } from '../service/metaticket.auth.guard.service';

const routes: Routes = [
    {
        path: 'list',
        component: SeatMapListComponent,
        canActivate: [MetaticketAuthGuardService],
    },
    {
        path: 'draw',
        component: SeatMapDrawComponent,
        canActivate: [MetaticketAuthGuardService],
    },
    {
        path: 'draw/:id',
        component: SeatMapDrawComponent,
        canActivate: [MetaticketAuthGuardService],
    },
    {
        path: '',
        redirectTo: 'list',
        pathMatch: 'full',
    },
];

@NgModule({
    imports: [
        CommonModule,
        RouterModule.forChild(routes),
        NbCardModule,
        NbButtonModule,
        NbIconModule,
        NbInputModule,
        NbSpinnerModule,
        Angular2SmartTableModule,
        EditorUILoaderModule,
        FormsModule,
        ReactiveFormsModule,
        SeatMapDrawComponent,
    ],
    declarations: [SeatMapListComponent],
    providers: [SeatMapDrawComponent],
})
export class SeatMapModule {}
