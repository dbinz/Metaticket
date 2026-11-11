import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BookingBreadcrumbComponent } from './booking-breadcrumb.component';

describe('BookingBreadcrumbComponent', () => {
  let component: BookingBreadcrumbComponent;
  let fixture: ComponentFixture<BookingBreadcrumbComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BookingBreadcrumbComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingBreadcrumbComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
