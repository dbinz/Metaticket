import { ApplicationConfig, importProvidersFrom } from '@angular/core';
import { provideRouter, withInMemoryScrolling, withNavigationErrorHandler } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptorsFromDi } from '@angular/common/http';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { HammerModule } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';
import { registerLocaleData } from '@angular/common';
import en from '@angular/common/locales/en';
import { en_US, provideNzI18n } from 'ng-zorro-antd/i18n';
import { NzConfig, provideNzConfig } from 'ng-zorro-antd/core/config';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import { EyeInvisibleOutline, EyeOutline } from '@ant-design/icons-angular/icons';
import { NgHcaptchaModule } from 'ng-hcaptcha';
import { routes } from './app.routes';
import { AuthInterceptor } from './lib/interceptors';
import { MaintenanceInterceptor } from './lib/interceptors/maintenance.interceptor';
import { checkMaintenanceOnNavigationError } from './lib/services/maintenance/maintenance.service';

registerLocaleData(en);

const ngZorroConfig: NzConfig = {
  notification: { nzMaxStack: 3 },
};

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(
      routes,
      // Scroll to #fragment (e.g. TOC /news/slug#section) after navigation.
      withInMemoryScrolling({
        anchorScrolling: 'enabled',
        scrollPositionRestoration: 'enabled',
      }),
      withNavigationErrorHandler(checkMaintenanceOnNavigationError),
    ),
    provideHttpClient(withFetch(), withInterceptorsFromDi()),
    provideNzI18n(en_US),
    importProvidersFrom(FormsModule),
    importProvidersFrom(HammerModule),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: MaintenanceInterceptor,
      multi: true,
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },
    provideAnimations(),
    provideNzConfig(ngZorroConfig),
    importProvidersFrom(NgHcaptchaModule.forRoot()),
    { provide: NZ_ICONS, useValue: [EyeOutline, EyeInvisibleOutline] },
  ],
};
