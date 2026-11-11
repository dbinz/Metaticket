import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { QrcodeComponent } from './qrcode.component';
import { QrcodeScannerComponent } from './qrcodescanner/qrcode-scanner.component';

const routes: Routes = [
    {
        path: '',
        component: QrcodeComponent,
        children: [
            { path: 'scanner/:sessionId', component: QrcodeScannerComponent },
            { path: '', redirectTo: 'shows', pathMatch: 'full' },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class QrcodeRoutingModule {}
