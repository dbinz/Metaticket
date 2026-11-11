import { Injectable } from '@angular/core';

export interface BlockerState {
  /** Extension chặn quảng cáo (uBlock, AdGuard, Brave Shields, Cốc Cốc...) đang hoạt động. */
  adBlocker: boolean;
  /** Trang đang chạy trong WebView của app khác (Facebook, Zalo...), nơi tab mới hoạt động không ổn định. */
  inAppWebView: boolean;
}

/** Class nằm trong EasyList — extension chặn quảng cáo sẽ ẩn phần tử mang các class này. */
const BAIT_CLASS_NAMES = 'adsbox ad-banner ad-placement banner-ads pub_300x250';

/** Thời gian chờ extension kịp áp CSS lên phần tử mồi. Dưới ~100ms thường cho kết quả âm tính giả. */
const BAIT_SETTLE_TIME_IN_MS = 120;

const IN_APP_WEB_VIEW_PATTERN = /FBAN|FBAV|FB_IAB|Instagram|Zalo|Line\/|MicroMessenger|TikTok/i;

/**
 * Phát hiện các tiện ích/ngữ cảnh trình duyệt có thể chặn cửa sổ thanh toán.
 *
 * Cố tình KHÔNG dò popup blocker ở đây: muốn dò thật thì phải gọi `window.open`, mà ngoài user
 * gesture thì lệnh đó luôn bị chặn nên kết quả luôn dương tính giả. Popup blocker được phát hiện
 * chính xác ngay tại thời điểm khách bấm thanh toán, trong `PaymentWindowService`.
 */
@Injectable({
  providedIn: 'root',
})
export class BlockerDetectionService {
  async check(): Promise<BlockerState> {
    return {
      adBlocker: await this._detectAdBlocker(),
      inAppWebView: this._isInAppWebView(),
    };
  }

  /**
   * Chèn một phần tử mồi mang class quảng cáo rồi kiểm tra xem nó có bị ẩn không.
   * Không gọi mạng nên không gửi dữ liệu khách hàng ra ngoài.
   */
  private async _detectAdBlocker(): Promise<boolean> {
    try {
      const bait = document.createElement('div');
      bait.className = BAIT_CLASS_NAMES;
      bait.style.cssText = 'position:absolute;left:-9999px;top:-9999px;width:1px;height:1px;';
      document.body.appendChild(bait);

      await new Promise((resolve) => setTimeout(resolve, BAIT_SETTLE_TIME_IN_MS));

      const style = window.getComputedStyle(bait);
      const isBlocked = bait.offsetHeight === 0 || style.display === 'none' || style.visibility === 'hidden';
      bait.remove();

      return isBlocked;
    } catch (error) {
      // Không chặn được luồng thanh toán chỉ vì việc dò tìm thất bại.
      console.error('ad blocker detection failed', error);
      return false;
    }
  }

  private _isInAppWebView(): boolean {
    return IN_APP_WEB_VIEW_PATTERN.test(navigator.userAgent ?? '');
  }
}
