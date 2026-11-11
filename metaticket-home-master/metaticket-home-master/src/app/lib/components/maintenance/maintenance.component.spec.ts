import { TestBed } from '@angular/core/testing';
import { OverlayContainer } from '@angular/cdk/overlay';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MaintenanceComponent } from './maintenance.component';
import { appIcons } from '../../theme';

describe('MaintenanceComponent', () => {
  it('blocks existing ng-zorro overlays and page scrolling, focuses retry, then restores both', async () => {
    await TestBed.configureTestingModule({
      imports: [MaintenanceComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const overlays = TestBed.inject(OverlayContainer).getContainerElement();
    const root = document.documentElement;
    root.style.overflow = 'auto';
    const fixture = TestBed.createComponent(MaintenanceComponent);
    fixture.detectChanges();
    expect(overlays.inert).toBeTrue();
    expect(root.style.overflow).toBe('hidden');
    expect(document.activeElement?.textContent).toContain('Thử lại');
    fixture.destroy();
    expect(overlays.inert).toBeFalse();
    expect(root.style.overflow).toBe('auto');
    root.style.overflow = '';
  });

  it('shows the MetaTicket logo as the brand', async () => {
    await TestBed.configureTestingModule({
      imports: [MaintenanceComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(MaintenanceComponent);
    fixture.detectChanges();
    const logo = (fixture.nativeElement as HTMLElement).querySelector('img');
    expect(logo?.getAttribute('src')).toBe(appIcons.icLogoLightMode);
    expect(logo?.alt).toBe('MetaTicket');
    fixture.destroy();
  });
});
