import { isPlatformBrowser } from '@angular/common';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { BookingState } from 'src/app/lib/stores';

/**
 * Wrapper localStorage an toàn cho SSR:
 * trên Node.js không có localStorage → mọi thao tác no-op / trả null.
 */
@Injectable({
  providedIn: 'root',
})
export class LocalStorageService {
  private readonly _isBrowser: boolean;

  constructor(@Inject(PLATFORM_ID) platformId: object) {
    this._isBrowser = isPlatformBrowser(platformId);
  }

  saveData(key: string, value: unknown): void {
    if (!this._isBrowser) {
      return;
    }
    localStorage.setItem(key, JSON.stringify(value));
  }

  getData<T>(key: string): T | null {
    if (!this._isBrowser) {
      return null;
    }
    const value = localStorage.getItem(key);
    if (value != null) {
      return JSON.parse(value) as T;
    }
    return null;
  }

  removeData(key: string): void {
    if (!this._isBrowser) {
      return;
    }
    localStorage.removeItem(key);
  }

  saveBookingSession(data: BookingState): void {
    this.saveData(this._bookingKey(data.showId, data.sessionId, data.visitDate), data);
  }

  getBookingSession(showId: string, sessionId: string, visitDate?: string): BookingState | null {
    return this.getData<BookingState>(this._bookingKey(showId, sessionId, visitDate));
  }

  removeBookingSession(showId: string, sessionId: string, visitDate?: string): void {
    this.removeData(this._bookingKey(showId, sessionId, visitDate));
  }

  /**
   * Saved booking states of a CALENDAR session, one per visit date. The booking page does not know the date before
   * restoring, so it scans the `${showId}-${sessionId}-YYYY-MM-DD` keys instead of reading a single key.
   */
  getCalendarBookingSessions(showId: string, sessionId: string): BookingState[] {
    if (!this._isBrowser) {
      return [];
    }
    const prefix = `${showId}-${sessionId}-`;
    // Collect keys first: removing an entry while walking localStorage by index would skip the next key.
    const keys: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith(prefix) && /^\d{4}-\d{2}-\d{2}$/.test(key.slice(prefix.length))) {
        keys.push(key);
      }
    }

    const result: BookingState[] = [];
    for (const key of keys) {
      try {
        const state = this.getData<BookingState>(key);
        if (state) {
          result.push(state);
        }
      } catch (error) {
        // A corrupted entry must not block booking; drop it so it is not retried on every visit.
        console.warn('drop unreadable booking state', key, error);
        this.removeData(key);
      }
    }
    return result;
  }

  /** The visit date is part of the key so two dates of one calendar session never share a held reservation. */
  private _bookingKey(showId: string, sessionId: string, visitDate?: string): string {
    return visitDate ? `${showId}-${sessionId}-${visitDate}` : `${showId}-${sessionId}`;
  }
}
