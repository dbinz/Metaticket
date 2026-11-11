import { Injectable } from '@angular/core';
import { HomeSearchState, HomeSearchStore } from './home-search.store';
import { DropdownMenuItem } from '../../dto';

@Injectable({ providedIn: 'root' })
export class HomeSearchService {
  constructor(private _store: HomeSearchStore) {}

  initial(state: HomeSearchState): void {
    this._store.update(state);
  }

  onSelectCity(citySelected: DropdownMenuItem | null): void {
    this._store.update({
      citySelected: citySelected,
    });
  }

  onSelectEvent(eventSelected: DropdownMenuItem | null): void {
    this._store.update({
      eventSelected: eventSelected,
    });
  }
}
