import { TestBed } from '@angular/core/testing';

import { SeatplannerUiService } from './seatplanner.ui.service';

describe('SeatplannerUiService', () => {
  let service: SeatplannerUiService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SeatplannerUiService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
