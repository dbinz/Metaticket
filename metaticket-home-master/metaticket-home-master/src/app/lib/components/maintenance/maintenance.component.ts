import { AsyncPipe, DOCUMENT } from '@angular/common';
import { AfterViewInit, Component, DestroyRef, ElementRef, OnDestroy, inject } from '@angular/core';
import { OverlayContainer } from '@angular/cdk/overlay';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { MaintenanceService } from '../../services/maintenance/maintenance.service';
import { appIcons } from '../../theme';

@Component({
  selector: 'app-maintenance',
  standalone: true,
  imports: [AsyncPipe],
  templateUrl: './maintenance.component.html',
})
export class MaintenanceComponent implements AfterViewInit, OnDestroy {
  readonly maintenance = inject(MaintenanceService);
  readonly appIcons = appIcons;
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly overlayContainer = inject(OverlayContainer);
  private readonly document = inject(DOCUMENT);
  private previousOverlayInert = false;
  private previousPageOverflow = '';
  isChecking = false;

  ngAfterViewInit(): void {
    // ng-zorro dialogs live outside app-root and must be blocked as well.
    const container = this.overlayContainer.getContainerElement();
    this.previousOverlayInert = container.inert;
    container.inert = true;
    // The inert page behind must not keep its own scrollbar next to the overlay.
    const root = this.document.documentElement;
    this.previousPageOverflow = root.style.overflow;
    root.style.overflow = 'hidden';
    this.host.nativeElement.querySelector<HTMLButtonElement>('button')?.focus();
  }

  ngOnDestroy(): void {
    this.overlayContainer.getContainerElement().inert = this.previousOverlayInert;
    this.document.documentElement.style.overflow = this.previousPageOverflow;
  }

  /** Reload only after an explicit retry confirms the gate has reopened; never repeat a POST. */
  retry(): void {
    if (this.isChecking) {
      return;
    }
    this.isChecking = true;
    this.maintenance
      .checkStatus()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => (this.isChecking = false)),
      )
      .subscribe((enabled) => {
        if (enabled === false) {
          window.location.reload();
        }
      });
  }
}
