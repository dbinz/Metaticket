import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PolicyInformationSecurityUserComponent } from './policy-information-security-user.component';

describe('PolicyInformationSecurityUserComponent', () => {
  let component: PolicyInformationSecurityUserComponent;
  let fixture: ComponentFixture<PolicyInformationSecurityUserComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PolicyInformationSecurityUserComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PolicyInformationSecurityUserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
