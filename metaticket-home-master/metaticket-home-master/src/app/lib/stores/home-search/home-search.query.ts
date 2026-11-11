import { Query } from '@datorama/akita';
import { Injectable } from '@angular/core';
import { HomeSearchState, HomeSearchStore } from './home-search.store';

@Injectable({ providedIn: 'root' })
export class HomeSearchQuery extends Query<HomeSearchState> {
  constructor(private _store: HomeSearchStore) {
    super(_store);
  }
}
