import { Injectable } from '@angular/core';
import { NbAuthService, NbAuthToken } from '@nebular/auth';
import { BehaviorSubject, of, Subscription, throwError, timer } from 'rxjs';
import { mergeMap, retryWhen, switchMap } from 'rxjs/operators';
import { UserModel } from '../';
import { UserService } from '../../@core/metaticket/data.service';
import axios from 'axios';

@Injectable()
export class MetaticketUserService extends UserService {
    private users$: BehaviorSubject<UserModel> = new BehaviorSubject(undefined);
    private refreshSubscription: Subscription | null = null;

    constructor(private authService: NbAuthService) {
        super();
        this.authService.onTokenChange().subscribe((token: NbAuthToken) => {
            if (this.refreshSubscription) {
                this.refreshSubscription.unsubscribe();
                this.refreshSubscription = null;
            }

            if (token.isValid()) {
                axios.defaults.headers.common['Authorization'] = `Bearer ${token.getValue()}`;
                this.users$.next(token.getPayload());

                const payload = token.getPayload();
                if (payload && payload.exp) {
                    const expires = new Date(payload.exp * 1000);
                    // Refresh trước khi hết hạn
                    const timeout = expires.getTime() - Date.now() - 5 * 60 * 1000;

                    this.refreshSubscription = timer(Math.max(0, timeout))
                        .pipe(
                            switchMap(() =>
                                this.authService.refreshToken('email', {}).pipe(
                                    switchMap(res => {
                                        if (res.isSuccess()) {
                                            return of(res);
                                        }
                                        return throwError('Refresh failed');
                                    }),
                                    retryWhen(errors =>
                                        errors.pipe(
                                            mergeMap((error: any) => {
                                                // Nếu token đã hết hạn thực sự, dừng retry
                                                if (Date.now() > expires.getTime()) {
                                                    return throwError(error);
                                                }
                                                // Retry sau 5 giây
                                                return timer(5000);
                                            }),
                                        ),
                                    ),
                                ),
                            ),
                        )
                        .subscribe({
                            next: res => {
                                // Success handled by onTokenChange
                            },
                            error: err => {
                                console.error('Auto refresh token failed after retries', err);
                            },
                        });
                }
            } else {
                delete axios.defaults.headers.common['Authorization'];
                this.users$.next(undefined);
            }
        });
    }

    getUser$(): BehaviorSubject<UserModel> {
        return this.users$;
    }
}
