/// <reference types="@viclass/ww/typings/mfe.runtime"/>

import { enableProdMode, importProvidersFrom } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { HttpClientModule } from "@angular/common/http"
import { AppComponent } from './app/app.component';
import "@viclass/ww/mfeRT"
import "zone.js"
import {environment} from "./environments/environment"
import { AppStateService } from './app/app.state.service';
import { provideRouter } from '@angular/router';


if (environment.production) {
  enableProdMode();
}
bootstrapApplication(AppComponent, {
  providers: [
      provideRouter([]),
      importProvidersFrom(HttpClientModule),
      AppStateService
  ]
}).catch(err => console.error(err));