import { Pipe, PipeTransform } from '@angular/core';
import { SearchSessionResponse } from 'src/app/lib/entities';

@Pipe({
  name: 'hasVisibleSessions',
  standalone: true,
})
export class HasVisibleSessionsPipe implements PipeTransform {
  transform(sessions: SearchSessionResponse[] | null | undefined): boolean {
    return (sessions ?? []).some((session) => session.showInHome);
  }
}
