import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeatPlanComponent } from './seat-plan.component';

describe('SeatPlanComponent', () => {
  let component: SeatPlanComponent;
  let fixture: ComponentFixture<SeatPlanComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SeatPlanComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SeatPlanComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
