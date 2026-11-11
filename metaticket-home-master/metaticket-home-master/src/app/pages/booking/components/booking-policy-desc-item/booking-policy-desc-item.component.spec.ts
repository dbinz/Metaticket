import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BookingPolicyDescItemComponent } from './booking-policy-desc-item.component';

describe('BookingPolicyDescItemComponent', () => {
  let component: BookingPolicyDescItemComponent;
  let fixture: ComponentFixture<BookingPolicyDescItemComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookingPolicyDescItemComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingPolicyDescItemComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
