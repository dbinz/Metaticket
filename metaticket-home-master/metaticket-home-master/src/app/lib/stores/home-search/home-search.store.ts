import { Store, StoreConfig } from '@datorama/akita';
import { Injectable } from '@angular/core';
import { DropdownMenuItem } from '../../dto';

export interface HomeSearchState {
  citySelected: DropdownMenuItem | null | undefined;
  eventSelected: DropdownMenuItem | null | undefined;
}

@Injectable({ providedIn: 'root' })
@StoreConfig({ name: 'home-search' })
export class HomeSearchStore extends Store<HomeSearchState> {
  constructor() {
    const initialState: HomeSearchState = {
      citySelected: null,
      eventSelected: null,
    };

    super(initialState);
  }
}
