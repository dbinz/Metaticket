import { Component, Input } from '@angular/core';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-popup-error',
  templateUrl: './popup.error.component.html',
  standalone: true,
  imports: [NgIf],
})
export class PopupErrorComponent {
  isOpen = false;

  @Input() title: string = 'Thông báo hệ thống';
  @Input() message: string = '';
  private resolveFunction: ((value: void) => void) | null = null;

  open(message: string): Promise<void> {
    if (message) this.message = message;
    this.isOpen = true;

    return new Promise<void>((resolve) => {
      this.resolveFunction = resolve;
    });
  }

  close(): void {
    this.isOpen = false;
    if (this.resolveFunction) {
      this.resolveFunction();
      this.resolveFunction = null;
    }
  }
}
