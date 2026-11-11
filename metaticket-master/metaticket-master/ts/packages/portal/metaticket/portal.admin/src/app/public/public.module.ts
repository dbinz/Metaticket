import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NbEvaIconsModule } from '@nebular/eva-icons';
import {
    NbAutocompleteModule,
    NbButtonModule,
    NbCardModule,
    NbDatepickerModule,
    NbIconModule,
    NbInputModule,
    NbLayoutModule,
    NbOptionModule,
    NbSpinnerModule,
    NbTreeGridModule,
} from '@nebular/theme';
import { AdminService, ShowService, UserService } from '../@core/metaticket/data.service';
import { ThemeModule } from '../@theme/theme.module';
import { ShowGateway } from '../metaticket/gateway/show.gateway';
import { PublicRoutingModule } from './public-routing.module';
import { OrderReportComponent } from './order-report/order-report.component';
import { AccessoryPublicReportComponent } from './accessory-report/accessory-public-report.component';
import { PublicComponent } from './public.component';
import { MetaticketUserService } from '../metaticket/service/metaticket.user.service';
import { MetaticketAdminService } from '../metaticket/service/metaticket.admin.service';
import { MetaticketShowService } from '../metaticket/service/metaticket.show.service';
import { AdminGateway } from '../metaticket/gateway/admin.gateway';

@NgModule({
    imports: [
        NbLayoutModule,
        ThemeModule,
        NbEvaIconsModule,
        PublicRoutingModule,
        NbCardModule,
        NbSpinnerModule,
        NbTreeGridModule,
        NbIconModule,
        NbAutocompleteModule,
        NbInputModule,
        NbOptionModule,
        ReactiveFormsModule,
        FormsModule,
        NbButtonModule,
        NbDatepickerModule,
    ],
    declarations: [OrderReportComponent, AccessoryPublicReportComponent, PublicComponent],
    providers: [
        { provide: UserService, useClass: MetaticketUserService },
        { provide: ShowService, useClass: MetaticketShowService },
        { provide: AdminService, useClass: MetaticketAdminService },
        { provide: ShowGateway },
        { provide: AdminGateway },
    ],
})
export class PublicModule {}
