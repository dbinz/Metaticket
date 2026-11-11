import { Query } from '@datorama/akita';
import { Injectable } from '@angular/core';
import { BookingState, BookingStore } from './booking.store';

@Injectable({ providedIn: 'root' })
export class BookingQuery extends Query<BookingState> {
  constructor(private _store: BookingStore) {
    super(_store);
  }
}
