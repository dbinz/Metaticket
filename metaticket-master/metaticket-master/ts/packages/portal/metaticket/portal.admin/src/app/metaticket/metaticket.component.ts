import { Component } from '@angular/core';

import { MENU_ITEMS } from './metaticket-menu';

@Component({
    selector: 'ngx-metaticket',
    template: `
        <ngx-one-column-layout>
            <nb-menu [items]="menu"></nb-menu>
            <router-outlet></router-outlet>
        </ngx-one-column-layout>
    `,
})
export class MetaticketComponent {
    menu = MENU_ITEMS;
}
