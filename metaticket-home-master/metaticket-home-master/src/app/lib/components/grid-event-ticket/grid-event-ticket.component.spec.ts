import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GridEventTicketComponent } from './grid-event-ticket.component';

describe('GridEventTicketComponent', () => {
  let component: GridEventTicketComponent;
  let fixture: ComponentFixture<GridEventTicketComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GridEventTicketComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(GridEventTicketComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
