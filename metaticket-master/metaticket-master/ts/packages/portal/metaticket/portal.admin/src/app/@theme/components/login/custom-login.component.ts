import { NgForOf, NgIf } from '@angular/common';
import { ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NB_AUTH_OPTIONS, NbAuthService, NbLoginComponent } from '@nebular/auth';
import {
    NbAlertModule,
    NbButtonModule,
    NbCardModule,
    NbCheckboxModule,
    NbIconModule,
    NbInputModule,
} from '@nebular/theme';

@Component({
    selector: 'app-custom-login',
    templateUrl: './custom-login.component.html',
    standalone: true,
    imports: [
        NbCardModule,
        FormsModule,
        NbInputModule,
        NbButtonModule,
        NgIf,
        NgForOf,
        NbAlertModule,
        RouterLink,
        NbIconModule,
        NbCheckboxModule,
    ],
})
export class CustomLoginComponent extends NbLoginComponent implements OnInit {
    returnUrl: string = '/';

    constructor(
        private authService: NbAuthService,
        @Inject(NB_AUTH_OPTIONS) options: {},
        cd: ChangeDetectorRef,
        router: Router,
        private route: ActivatedRoute,
    ) {
        super(authService, options, cd, router);
    }

    ngOnInit(): void {
        this.returnUrl = this.route.snapshot.queryParams['next'] || '/';
    }

    override login(): void {
        this.errors = [];
        this.messages = [];
        this.submitted = true;

        this.authService.authenticate(this.strategy, this.user).subscribe({
            next: result => {
                this.submitted = false;
                if (result.isSuccess()) {
                    this.messages = result.getMessages();
                    this.router.navigateByUrl(this.returnUrl);
                } else {
                    this.errors = result.getErrors();
                }
                this.cd.detectChanges();
            },
            error: err => {
                this.submitted = false;
                this.errors = [err];
                this.cd.detectChanges();
            },
        });
    }
}
