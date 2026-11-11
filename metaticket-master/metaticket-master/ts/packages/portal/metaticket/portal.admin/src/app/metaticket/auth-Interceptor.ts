import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NbAuthService } from '@nebular/auth';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class AuthInterceptor implements HttpInterceptor {
    constructor(
        private authService: NbAuthService,
        private router: Router,
    ) {}

    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return this.authService.getToken().pipe(
            switchMap(token => {
                if (token && token.isValid()) {
                    const newReq = req.clone({
                        setHeaders: {
                            Authorization: `Bearer ${token.getValue()}`,
                        },
                    });
                    return next.handle(newReq);
                }
                return next.handle(req);
            }),
            catchError((err: any) => {
                let status = err.status;
                if (err?.response?.status) {
                    status = err.response.status;
                }
                if (status === 401) {
                    console.log('401 error, redirecting to login');
                    this.authService.logout('email').subscribe(() => {
                        window.location.href = '/auth/login';
                    });
                }
                throw err;
            }),
        );
    }
}
