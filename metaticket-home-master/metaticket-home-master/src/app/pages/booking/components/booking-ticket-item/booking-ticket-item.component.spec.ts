import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BookingTicketItemComponent } from './booking-ticket-item.component';

describe('BookingTicketItemComponent', () => {
  let component: BookingTicketItemComponent;
  let fixture: ComponentFixture<BookingTicketItemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookingTicketItemComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingTicketItemComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
