import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PolicyRegulationComponent } from './policy-regulation.component';

describe('PolicyRegulationComponent', () => {
  let component: PolicyRegulationComponent;
  let fixture: ComponentFixture<PolicyRegulationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PolicyRegulationComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PolicyRegulationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
