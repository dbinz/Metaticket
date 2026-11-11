import { enableProdMode, importProvidersFrom } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { HttpClientModule } from "@angular/common/http";
import { provideRouter, withDebugTracing } from "@angular/router";
import "zone.js";
import { AppComponent } from "./app/app.component";
import { environment } from "./environments/environment";
import { AppStateService } from "./app/app.state.service";
import { routeConfig, routes } from "./app/app.routing";
import { OdcGateway } from "./app/odc.gateway";
import {provideAnimations} from "@angular/platform-browser/animations"
import { MAT_DATE_LOCALE, MatNativeDateModule } from "@angular/material/core";


if (environment.production) {
  enableProdMode();
}
bootstrapApplication(AppComponent, {
  providers: [
      provideRouter(routes, ...routeConfig),
      provideAnimations(),
      importProvidersFrom(MatNativeDateModule),
      importProvidersFrom(HttpClientModule),
      AppStateService,
      OdcGateway,
      { provide: MAT_DATE_LOCALE, useValue: 'en-GB' }
  ]
}).catch(err => console.error(err));
