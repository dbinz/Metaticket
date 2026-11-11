import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeatplannerUiComponent } from './seatplanner.ui.component';

describe('SeatplannerUiComponent', () => {
  let component: SeatplannerUiComponent;
  let fixture: ComponentFixture<SeatplannerUiComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [SeatplannerUiComponent]
    });
    fixture = TestBed.createComponent(SeatplannerUiComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
