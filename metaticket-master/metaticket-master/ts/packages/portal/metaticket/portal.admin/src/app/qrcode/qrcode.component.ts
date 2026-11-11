import { Component } from '@angular/core';

@Component({
    selector: 'ngx-qrcode',
    template: `
        <nb-layout center>
            <nb-layout-column class="p-0 overflow-hidden">
                <router-outlet></router-outlet>
            </nb-layout-column>
        </nb-layout>
    `,
})
export class QrcodeComponent {}
