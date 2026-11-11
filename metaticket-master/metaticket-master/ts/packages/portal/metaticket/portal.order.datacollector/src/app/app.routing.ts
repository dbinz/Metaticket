import { RouterFeatures, Routes, withDebugTracing } from "@angular/router";
import { CategoriesComponent } from "./categories/categories.component";
import { HomepageComponent } from "./homepage/homepage.component";
import { IndividualTicketsComponent } from "./individual-tickets/individual-tickets.component";
import { PageNotfoundComponent } from "./page.notfound/page.notfound.component";

export const routes: Routes = [
    { path: ":id",  component: HomepageComponent,
        children: [
            { path: "categories", component: CategoriesComponent },
            { path: "individual-tickets", component: IndividualTicketsComponent }
        ]
    },
    { path: "",  component: PageNotfoundComponent},
    // { path: "",  redirectTo: "page-notfound", pathMatch: "full"},
    // { path: "**",  redirectTo: "page-notfound", pathMatch: "full"},

];

export const routeConfig: RouterFeatures[] = [
    // withDebugTracing()
]
