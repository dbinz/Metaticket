import { Pipe, PipeTransform } from '@angular/core';
import { SearchSessionResponse } from 'src/app/lib/entities';
import { formatDate, nearestHomeSession, ongoingCalendarLabel } from 'src/app/lib/utils';

@Pipe({
  name: 'nearestSessionDate',
  standalone: true,
})
export class NearestSessionDatePipe implements PipeTransform {
  transform(sessions: SearchSessionResponse[] | null | undefined): string {
    const nearestItem = nearestHomeSession(sessions);

    if (!nearestItem) {
      return ' ';
    }

    // A running calendar session has no meaningful single date ("now" from metasearch); show its range end instead.
    return ongoingCalendarLabel(nearestItem) || formatDate(nearestItem.startTime);
  }
}
