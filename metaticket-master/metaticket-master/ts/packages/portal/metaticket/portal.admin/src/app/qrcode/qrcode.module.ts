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
import { ThemeModule } from '../@theme/theme.module';
import { QrcodeRoutingModule } from './qrcode-routing.module';
import { QrcodeComponent } from './qrcode.component';
import { QrcodeScannerComponent } from './qrcodescanner/qrcode-scanner.component';
import { PopupOrderInfoComponent } from './popup/order-info/popup.order-info.component';
import { PopupErrorComponent } from './popup/error/popup.error.component';
import { QRCodeGateway } from '../metaticket/gateway/qrcode.gateway';
import { QRCodeService } from '../metaticket/service/metaticket.service.qrcode';

@NgModule({
    imports: [
        NbLayoutModule,
        ThemeModule,
        NbEvaIconsModule,
        QrcodeRoutingModule,
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
    declarations: [QrcodeComponent, QrcodeScannerComponent, PopupOrderInfoComponent, PopupErrorComponent],
    providers: [{ provide: QRCodeGateway }, { provide: QRCodeService }],
})
export class QrcodeModule {}
