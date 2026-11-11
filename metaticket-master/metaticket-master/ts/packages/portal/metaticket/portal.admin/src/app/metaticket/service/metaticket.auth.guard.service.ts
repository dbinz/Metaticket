import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { NbAuthService } from '@nebular/auth';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

@Injectable({
    providedIn: 'root',
})
export class MetaticketAuthGuardService implements CanActivate {
    constructor(
        private authService: NbAuthService,
        private router: Router,
    ) {}

    /** Requires Nebular auth for all guarded portal routes (including accessory admin). */
    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
        return this.authService.isAuthenticatedOrRefresh().pipe(
            tap(authenticated => {
                if (!authenticated) {
                    this.router.navigate(['/auth/login'], {
                        queryParams: {
                            next: state.url,
                        },
                    });
                }
            }),
        );
    }
}
