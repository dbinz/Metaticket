import { Component, Input } from '@angular/core';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-basic-popup',
  templateUrl: './popup.basic.component.html',
  standalone: true,
  imports: [NgIf],
})
export class PopupBasicComponent {
  isOpen = false;
  private resolveFunction: ((value: boolean) => void) | null = null;

  @Input() title: string = 'Thành công';
  @Input() content: string = '';

  open(content?: string): Promise<boolean> {
    if (content) this.content = content;
    this.isOpen = true;

    return new Promise<boolean>((resolve) => {
      this.resolveFunction = resolve;
    });
  }

  protected close(): void {
    this.isOpen = false;
    if (this.resolveFunction) {
      this.resolveFunction(false); // Trả về `false` nếu user đóng popup
      this.resolveFunction = null;
    }
  }

  protected confirm(): void {
    this.isOpen = false;
    if (this.resolveFunction) {
      this.resolveFunction(true); // Trả về `true` nếu user bấm "Xác nhận"
      this.resolveFunction = null;
    }
  }
}
