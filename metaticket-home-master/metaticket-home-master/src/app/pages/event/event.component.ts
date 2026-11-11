import { Component } from '@angular/core';
import { EventHeaderComponent } from './event-header/event-header.component';
import { EventContentComponent } from './event-content/event-content.component';
import { LayoutComponent } from 'src/app/lib/components';

@Component({
  selector: 'app-event',
  standalone: true,
  imports: [LayoutComponent, EventHeaderComponent, EventContentComponent],
  templateUrl: './event.component.html',
})
export class EventComponent {}
