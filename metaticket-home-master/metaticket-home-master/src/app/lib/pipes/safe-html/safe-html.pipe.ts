import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Pipe sanitize HTML từ CMS/API trước khi bind qua [innerHTML].
 * Chỉ dùng với nội dung từ nguồn tin cậy (CMS nội bộ).
 */
@Pipe({
  name: 'safeHtml',
  standalone: true,
})
export class SafeHtmlPipe implements PipeTransform {
  private readonly _sanitizer = inject(DomSanitizer);

  transform(value: string | null | undefined): SafeHtml {
    if (!value) {
      return '';
    }
    return this._sanitizer.bypassSecurityTrustHtml(value);
  }
}
