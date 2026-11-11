import { Query } from '@datorama/akita';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AccessoryCheckoutState, AccessoryCheckoutStore } from './accessory-checkout.store';

@Injectable({ providedIn: 'root' })
export class AccessoryCheckoutQuery extends Query<AccessoryCheckoutState> {
  readonly currentIndex$: Observable<number> = this.select('currentIndex');
  /** Absolute checkout deadline (epoch millis; 0 = none) driving the display countdown. */
  readonly expiresAt$: Observable<number> = this.select('expiresAt');

  constructor(private _store: AccessoryCheckoutStore) {
    super(_store);
  }
}
