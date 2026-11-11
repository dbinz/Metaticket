import { SearchShowResponse } from 'src/app/lib/entities';
import { groupEvents } from './event-groups';

describe('groupEvents', () => {
  const now = 1000;
  const show = (
    id: string,
    sessions: { startTime: number; endTime?: number; showInHome?: boolean }[],
  ): SearchShowResponse =>
    ({ id, sessions: sessions.map((session) => ({ showInHome: true, ...session })) }) as SearchShowResponse;

  it('keeps ongoing sessions active through their end time and sorts upcoming by nearest start', () => {
    const future = show('future', [{ startTime: 2000, endTime: 3000 }]);
    const ongoing = show('ongoing', [{ startTime: 100, endTime: 1500 }]);
    const boundary = show('boundary', [{ startTime: 100, endTime: now }]);
    expect(groupEvents([future, ongoing, boundary], now).upcoming.map((item) => item.id)).toEqual([
      'ongoing',
      'boundary',
      'future',
    ]);
    expect(groupEvents([boundary], now + 1).past).toEqual([boundary]);
  });

  it('uses all visible sessions and ignores hidden future sessions', () => {
    const mixed = show('mixed', [{ startTime: 100, endTime: 200 }, { startTime: 2000 }]);
    const past = show('past', [
      { startTime: 100, endTime: 200 },
      { startTime: 2000, showInHome: false },
    ]);
    expect(groupEvents([mixed, past], now)).toEqual({ upcoming: [mixed], past: [past] });
  });

  it('falls back to startTime for legacy sessions and orders past shows newest first', () => {
    const old = show('old', [{ startTime: 100 }]);
    const recent = show('recent', [{ startTime: 500, endTime: 900 }]);
    const future = show('future', [{ startTime: 2000, endTime: 0 }]);
    expect(groupEvents([old, recent, future, recent], now)).toEqual({ upcoming: [future], past: [recent, old] });
  });

  it('does not include shows without visible sessions', () => {
    expect(groupEvents([show('hidden', [{ startTime: 2000, showInHome: false }]), show('empty', [])], now)).toEqual({
      upcoming: [],
      past: [],
    });
  });
});
