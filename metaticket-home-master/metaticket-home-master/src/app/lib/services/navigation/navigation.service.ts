import { isPlatformBrowser, Location } from '@angular/common';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { NavigationExtras, Router } from '@angular/router';
import { routeName } from 'src/app/lib/constants';

@Injectable({
  providedIn: 'root',
})
export class NavigationService {
  private readonly _isBrowser: boolean;

  constructor(
    private _router: Router,
    private _location: Location,
    @Inject(PLATFORM_ID) platformId: object,
  ) {
    this._isBrowser = isPlatformBrowser(platformId);
  }

  navigateTo(route: string, extras?: NavigationExtras): void {
    this._router.navigate([route], extras).then(() => {
      this._scrollToTop();
    });
  }

  /** Nested paths (e.g. /news/category/show-su-kien) — `navigate([full])` would be one segment. */
  navigateByUrl(url: string): void {
    const path = url.startsWith('/') ? url : `/${url}`;
    this._router.navigateByUrl(path).then(() => {
      this._scrollToTop();
    });
  }

  navigateToHome(): void {
    this.navigateTo(routeName.root);
  }

  navigateToNotFound(): void {
    this.navigateTo(routeName.notFound, { replaceUrl: true });
  }

  goBack(): void {
    this._location.back();
    this._scrollToTop();
  }

  private _scrollToTop(): void {
    if (!this._isBrowser) {
      return;
    }
    setTimeout(() => {
      window.scrollTo(0, 0);
    }, 100);
  }
}
