/**
 * @license
 * Copyright Akveo. All Rights Reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
import { registerLocaleData } from '@angular/common';
import localeVi from '@angular/common/locales/vi';
import { HttpClientModule } from '@angular/common/http';
import { LOCALE_ID, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import {
    NbChatModule,
    NbDatepickerModule,
    NbTimepickerModule,
    NbDialogModule,
    NbMenuModule,
    NbSidebarModule,
    NbToastrModule,
    NbWindowModule,
} from '@nebular/theme';
import { CoreModule } from './@core/core.module';
import { ThemeModule } from './@theme/theme.module';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { MetaticketAuthModule } from './metaticket/metaticket.auth.module';
import { MetaticketAuthGuardService } from './metaticket/service/metaticket.auth.guard.service';

// Đăng ký dữ liệu locale ngay tại đây
registerLocaleData(localeVi, 'vi-VN');

@NgModule({
    declarations: [AppComponent],
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        HttpClientModule,
        AppRoutingModule,
        NbSidebarModule.forRoot(),
        NbMenuModule.forRoot(),
        NbDatepickerModule.forRoot(),
        NbTimepickerModule.forRoot(),
        NbDialogModule.forRoot(),
        NbWindowModule.forRoot(),
        NbToastrModule.forRoot(),
        NbChatModule.forRoot({
            messageGoogleMapKey: 'AIzaSyA_wNuCzia92MAmdLRzmqitRGvCF7wCZPY',
        }),
        CoreModule.forRoot(),
        ThemeModule.forRoot(),
        MetaticketAuthModule.forRoot(),
        NbDialogModule.forRoot(),
    ],
    bootstrap: [AppComponent],
    providers: [MetaticketAuthGuardService, { provide: LOCALE_ID, useValue: 'vi-VN' }],
})
export class AppModule {}
