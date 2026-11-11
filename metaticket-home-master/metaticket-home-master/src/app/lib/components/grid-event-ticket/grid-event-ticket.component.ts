import { NgForOf, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { SearchShowResponse } from 'src/app/lib/entities';
import { NavigationService } from 'src/app/lib/services';
import { formatDate, nearestHomeSession, ongoingCalendarLabel, showDetailPath } from 'src/app/lib/utils';
import { ButtonComponent } from 'src/app/lib/components/button/button.component';
import { ImageComponent } from 'src/app/lib/components/image/image.component';
import { MinPriceLabelPipe, NearestSessionDatePipe } from 'src/app/lib/pipes';

@Component({
  selector: 'grid-event-ticket',
  standalone: true,
  imports: [
    NzTypographyModule,
    ButtonComponent,
    NgIf,
    ImageComponent,
    NgForOf,
    NearestSessionDatePipe,
    MinPriceLabelPipe,
  ],
  templateUrl: './grid-event-ticket.component.html',
  styleUrl: './grid-event-ticket.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GridEventTicketComponent {
  @Input() featured = false;
  @Input() title = '';
  @Input() isLast? = true;
  @Input() textColor = 'text-white';
  @Input() buttonColor: 'primary' | 'white' | 'yellow' | 'transparent' | 'transparent-orange' | 'gray' | 'red' | 'green' | 'transparent-gray' =
    'transparent';
  @Input({ required: true }) data!: SearchShowResponse[];
  @Output() handlerOnViewMore = new EventEmitter<void>();
  constructor(private _navigationService: NavigationService) {}
  onViewMore(): void {
    this.handlerOnViewMore.emit();
  }
  onViewDetail(show: SearchShowResponse): void {
    this._navigationService.navigateTo(showDetailPath(show));
  }
  formatDate = formatDate;

  /**
   * "Đang diễn ra · đến …" when the card's session is a running calendar session. Checked before `startTime` because
   * filter items also carry the show-level startTime (first calendar date), which would read as a past date.
   */
  ongoingLabel(item: SearchShowResponse): string {
    return ongoingCalendarLabel(nearestHomeSession(item.sessions));
  }

  trackByShowId(_index: number, show: SearchShowResponse): string {
    return show.id;
  }

  /** Filter listing still requires a home-visible session; highlight cards omit `sessions`. */
  isHighlightCardVisible(item: SearchShowResponse): boolean {
    const sessions = item.sessions;
    if (sessions == null) {
      return true;
    }
    return sessions.some((session) => session.showInHome);
  }
}
