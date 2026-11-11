import { Directive, ElementRef, OnInit } from '@angular/core';

@Directive({
  selector: '[appFocusInput]',
  standalone: true,
})
export class FocusInputDirective implements OnInit {
  constructor(private el: ElementRef) {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    if (!el.nativeElement['focus']) {
      throw new Error('Element does not accept focus.');
    }
  }
  ngOnInit(): void {
    const input: HTMLInputElement = this.el.nativeElement as HTMLInputElement;
    input.focus();
    input.select();
  }
}
