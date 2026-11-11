import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DataCollectionInputComponent } from './data-collection-input.component';

describe('DataCollectionInputComponent', () => {
  let component: DataCollectionInputComponent;
  let fixture: ComponentFixture<DataCollectionInputComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataCollectionInputComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DataCollectionInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
