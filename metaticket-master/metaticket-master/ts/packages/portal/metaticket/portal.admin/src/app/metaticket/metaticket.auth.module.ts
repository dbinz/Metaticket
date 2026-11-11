import { CommonModule } from '@angular/common';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { ModuleWithProviders, NgModule, Optional, SkipSelf } from '@angular/core';
import {
    NbAuthJWTInterceptor,
    NbAuthJWTToken,
    NbAuthModule,
    NbAuthService,
    NbPasswordAuthStrategy,
} from '@nebular/auth';
import { AuthInterceptor } from './auth-Interceptor';
import { throwIfAlreadyLoaded } from '../@core/module-import-guard';
import { environment, publicApi } from '../../environments/environment';
import axios from 'axios';

@NgModule({
    imports: [CommonModule],
    exports: [NbAuthModule],
    declarations: [],
})
export class MetaticketAuthModule {
    constructor(@Optional() @SkipSelf() parentModule: MetaticketAuthModule, authService: NbAuthService) {
        throwIfAlreadyLoaded(parentModule, 'MetaticketAuthModule');

        axios.interceptors.response.use(
            response => response,
            error => {
                if (error.response && error.response.status === 401) {
                    console.log('Axios 401 error, redirecting to login');
                    authService.logout('email').subscribe(() => {
                        window.location.href = '/auth/login';
                    });
                }
                return Promise.reject(error);
            },
        );
    }

    static forRoot(): ModuleWithProviders<MetaticketAuthModule> {
        return {
            ngModule: MetaticketAuthModule,
            providers: [
                ...METATICKET_AUTH_PROVIDERS,
                {
                    provide: HTTP_INTERCEPTORS,
                    useClass: AuthInterceptor,
                    multi: true,
                },
            ],
        };
    }
}

export interface NbAuthSocialLink {
    link?: string;
    url?: string;
    target?: string;
    title?: string;
    icon?: string;
}

const socialLinks: NbAuthSocialLink[] = [];

export const defaultSettings: any = {
    forms: {
        login: {
            // delay before redirect after a successful login, while success message is shown to the user
            redirectDelay: 500,
            strategy: 'email', // strategy id key.
            rememberMe: true, // whether to show or not the `rememberMe` checkbox
            showMessages: {
                // show/not show success/error messages
                success: true,
                error: true,
            },
            socialLinks: socialLinks, // social links at the bottom of a page
        },
        register: {
            redirectDelay: 500,
            strategy: 'email',
            showMessages: {
                success: true,
                error: true,
            },
            terms: false,
            fullName: undefined,
            socialLinks: socialLinks,
        },
        requestPassword: {
            redirectDelay: 500,
            strategy: 'email',
            showMessages: {
                success: true,
                error: true,
            },
            socialLinks: socialLinks,
        },
        resetPassword: {
            redirectDelay: 500,
            strategy: 'email',
            showMessages: {
                success: true,
                error: true,
            },
            socialLinks: socialLinks,
        },
        logout: {
            redirectDelay: 500,
            strategy: 'email',
        },
        validation: {
            password: {
                required: true,
                minLength: 4,
                maxLength: 50,
            },
            email: {
                required: true,
            },
            fullName: {
                required: false,
                minLength: 4,
                maxLength: 50,
            },
        },
    },
};

export const METATICKET_AUTH_PROVIDERS = [
    NbAuthModule.forRoot({
        strategies: [
            NbPasswordAuthStrategy.setup({
                name: 'email',
                token: {
                    class: NbAuthJWTToken,
                    key: 'token',
                },
                baseEndpoint: `${publicApi('/authentication')}`,
                login: {
                    endpoint: '/login',
                },
                refreshToken: {
                    endpoint: '/refresh-token',
                    method: 'post',
                },
                logout: {
                    endpoint: '/logout',
                    method: 'get',
                    redirect: {
                        success: '/',
                    },
                },
                // register: {
                //     endpoint: '/register',
                //     redirect: {
                //         success: '/',
                //     },
                // },
                validation: {
                    password: {
                        required: true,
                        minLength: 5,
                        maxLength: 30,
                        regexp: null,
                    },
                    email: {
                        required: true,
                        regexp: null,
                    },
                    fullName: {
                        required: true,
                        minLength: 5,
                        maxLength: 50,
                        regexp: null,
                    },
                },
            }),
        ],
        forms: defaultSettings.forms,
    }).providers,
];
