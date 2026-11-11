import { Injectable } from '@angular/core';
import { NzNotificationService } from 'ng-zorro-antd/notification';
import { HttpErrorResponse } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  constructor(private _notification: NzNotificationService) {}

  showSuccessNotification(message: string): void {
    this._notification.create('success', 'Thành công', message, {
      nzDuration: 3000,
      nzPlacement: 'topRight',
    });
  }

  showErrorNotification(message: string): void {
    this._notification.create('error', 'Thất bại', message, {
      nzDuration: 3000,
      nzPlacement: 'topRight',
    });
  }

  showHttpErrorNotification(err: HttpErrorResponse): void {
    const error = err.error as string;
    this._notification.create('error', 'Thất bại', error, {
      nzDuration: 3000,
      nzPlacement: 'topRight',
    });
  }

  showWarningNotification(message: string): void {
    this._notification.create('warning', 'Thông báo', message, {
      nzDuration: 3000,
      nzPlacement: 'topRight',
    });
  }

  /**
   * Cảnh báo chặn một thao tác quan trọng (ví dụ khách bấm thanh toán khi đang bật trình chặn quảng
   * cáo). Không tự tắt để khách kịp đọc, và đặt ở giữa trên cho dễ thấy hơn toast thường.
   */
  showBlockingWarning(message: string): void {
    this._notification.create('warning', 'Thông báo', message, {
      nzDuration: 0,
      nzPlacement: 'top',
    });
  }
}
