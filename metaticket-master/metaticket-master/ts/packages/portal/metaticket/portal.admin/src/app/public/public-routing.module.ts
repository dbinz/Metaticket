import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { OrderReportComponent } from './order-report/order-report.component';
import { AccessoryPublicReportComponent } from './accessory-report/accessory-public-report.component';
import { PublicComponent } from './public.component';

const routes: Routes = [
    {
        path: '',
        component: PublicComponent,
        children: [
            { path: 'report/:reportKey', component: OrderReportComponent },
            { path: 'accessory/report/:reportKey', component: AccessoryPublicReportComponent },
            { path: '', redirectTo: 'shows', pathMatch: 'full' },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class PublicRoutingModule {}
