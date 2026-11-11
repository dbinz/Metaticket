import { Query } from '@datorama/akita';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Cart, CartLine, AccessoryCartStore } from './accessory-cart.store';

@Injectable({ providedIn: 'root' })
export class AccessoryCartQuery extends Query<Cart> {
  readonly lines$: Observable<CartLine[]> = this.select('lines');
  readonly accessoryShowId$: Observable<string> = this.select('accessoryShowId');

  /** Total number of units across all lines (for a cart badge). */
  readonly totalQuantity$: Observable<number> = this.lines$.pipe(
    map((lines) => lines.reduce((sum, line) => sum + line.quantity, 0)),
  );

  constructor(private _store: AccessoryCartStore) {
    super(_store);
  }
}
