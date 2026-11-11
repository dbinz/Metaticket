import { SearchShowResponse } from 'src/app/lib/entities';

/** A show remains active while any home-visible session has not ended. Legacy sessions fall back to startTime. */
export function groupEvents(
  shows: SearchShowResponse[],
  now = Date.now(),
): {
  upcoming: SearchShowResponse[];
  past: SearchShowResponse[];
} {
  const upcoming: { show: SearchShowResponse; time: number }[] = [];
  const past: { show: SearchShowResponse; time: number }[] = [];
  const seen = new Set<string>();
  for (const show of shows) {
    if (seen.has(show.id)) continue;
    seen.add(show.id);
    const sessions = show.sessions?.filter((session) => session.showInHome) ?? [];
    if (show.sessions != null && !sessions.length) continue;
    const dates = sessions.length ? sessions : [{ startTime: show.startTime ?? 0, endTime: show.ongoingUntil ?? 0 }];
    const active = dates.filter((session) => Math.max(session.endTime ?? 0, session.startTime) >= now);
    if (active.length) {
      upcoming.push({ show, time: Math.min(...active.map((session) => Math.max(session.startTime, now))) });
    } else {
      past.push({ show, time: Math.max(...dates.map((session) => Math.max(session.endTime ?? 0, session.startTime))) });
    }
  }
  return {
    upcoming: upcoming.sort((a, b) => a.time - b.time).map((item) => item.show),
    past: past.sort((a, b) => b.time - a.time).map((item) => item.show),
  };
}
