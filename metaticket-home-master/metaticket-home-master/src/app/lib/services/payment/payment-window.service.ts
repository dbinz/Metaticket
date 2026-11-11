import { Injectable } from '@angular/core';

/** Trang chờ hiển thị trong tab mới trong lúc đợi backend trả URL cổng thanh toán. */
const PLACEHOLDER_HTML = `<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><title>Đang chuyển tới cổng thanh toán</title></head>
<body style="font-family:system-ui,-apple-system,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0">
<p style="font-size:16px;color:#333">Đang chuyển tới cổng thanh toán, vui lòng chờ...</p>
</body></html>`;

/**
 * Mở cửa sổ cổng thanh toán sao cho không bị popup blocker chặn.
 *
 * Trình duyệt chỉ cho phép `window.open` khi lệnh chạy đồng bộ trong call stack của một user gesture
 * (transient user activation). Gọi sau một request HTTP là đã mất activation, nên `openPlaceholder`
 * phải được gọi NGAY đầu click handler, trước khi gọi API, rồi mới điều hướng tab đó bằng
 * `navigateTo` khi có URL.
 */
@Injectable({
  providedIn: 'root',
})
export class PaymentWindowService {
  /**
   * Mở sẵn một tab trống. Phải gọi đồng bộ trong click handler.
   * Trả về `null` khi trình duyệt chặn — khi đó KHÔNG được gọi `payment/start`, vì backend sẽ đẩy
   * đơn sang `PaymentProcessing` và giữ ghế trong khi khách không có cách nào thanh toán.
   */
  openPlaceholder(): Window | null {
    const paymentWindow = window.open('', '_blank');
    if (!paymentWindow) {
      return null;
    }

    try {
      paymentWindow.document.write(PLACEHOLDER_HTML);
      paymentWindow.document.close();
    } catch (error) {
      // Một số WebView chặn ghi vào document của tab mới; tab vẫn dùng để điều hướng được.
      console.error('cannot write payment placeholder', error);
    }

    return paymentWindow;
  }

  /** Điều hướng tab đã mở sẵn tới cổng thanh toán. Trả `false` nếu tab không còn dùng được. */
  navigateTo(paymentWindow: Window | null, url: string): boolean {
    if (!this.isUsable(paymentWindow)) {
      return false;
    }

    paymentWindow!.location.href = url;
    paymentWindow!.focus();
    return true;
  }

  /** Điều hướng chính tab hiện tại — không bao giờ bị popup blocker chặn. */
  redirectSameTab(url: string): void {
    window.location.href = url;
  }

  isUsable(paymentWindow: Window | null): boolean {
    return !!paymentWindow && !paymentWindow.closed;
  }

  close(paymentWindow: Window | null): void {
    if (this.isUsable(paymentWindow)) {
      paymentWindow!.close();
    }
  }
}
