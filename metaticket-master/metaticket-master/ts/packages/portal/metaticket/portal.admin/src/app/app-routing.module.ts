import { NgModule } from '@angular/core';
import { ExtraOptions, RouterModule, Routes } from '@angular/router';
import {
    NbAuthComponent,
    NbLogoutComponent,
    NbRegisterComponent,
    NbRequestPasswordComponent,
    NbResetPasswordComponent,
} from '@nebular/auth';
import { CustomLoginComponent } from './@theme/components/login/custom-login.component';

export const routes: Routes = [
    {
        path: 'portal',
        loadChildren: () => import('./metaticket/metaticket.module').then(m => m.MetaticketModule),
    },
    {
        path: 'auth',
        component: NbAuthComponent,
        children: [
            { path: '', component: CustomLoginComponent },
            { path: 'login', component: CustomLoginComponent },
            { path: 'register', component: NbRegisterComponent },
            { path: 'logout', component: NbLogoutComponent },
            { path: 'request-password', component: NbRequestPasswordComponent },
            { path: 'reset-password', component: NbResetPasswordComponent },
            { path: '', redirectTo: '', pathMatch: 'full' },
        ],
    },
    {
        path: 'public',
        loadChildren: () => import('./public/public.module').then(m => m.PublicModule),
    },
    {
        path: 'qrcode',
        loadChildren: () => import('./qrcode/qrcode.module').then(m => m.QrcodeModule),
    },
    { path: '', redirectTo: 'portal', pathMatch: 'full' },
];

const config: ExtraOptions = {
    useHash: false,
};

@NgModule({
    imports: [RouterModule.forRoot(routes, config)],
    exports: [RouterModule],
})
export class AppRoutingModule {}
