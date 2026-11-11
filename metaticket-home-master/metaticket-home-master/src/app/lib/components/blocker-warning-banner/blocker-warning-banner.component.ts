import { Component, OnInit } from '@angular/core';
import { NgIf } from '@angular/common';
import { NzTypographyComponent } from 'ng-zorro-antd/typography';
import { BlockerDetectionService } from 'src/app/lib/services';

/**
 * Cảnh báo khách đang bật tiện ích chặn quảng cáo / đang ở in-app WebView — hai nguyên nhân chính
 * khiến cửa sổ thanh toán không mở được. Đặt ngay trên breadcrumb của trang thanh toán.
 */
@Component({
  selector: 'blocker-warning-banner',
  standalone: true,
  imports: [NgIf, NzTypographyComponent],
  templateUrl: './blocker-warning-banner.component.html',
})
export class BlockerWarningBannerComponent implements OnInit {
  visible = false;
  isChecking = false;
  isInAppWebView = false;
  private _dismissed = false;

  constructor(private _blockerDetection: BlockerDetectionService) {}

  ngOnInit(): void {
    void this.recheck();
  }

  async recheck(): Promise<void> {
    this.isChecking = true;
    try {
      const blocker = await this._blockerDetection.check();
      this.isInAppWebView = blocker.inAppWebView;
      this.visible = (blocker.adBlocker || blocker.inAppWebView) && !this._dismissed;
    } finally {
      this.isChecking = false;
    }
  }

  onDismiss(): void {
    this._dismissed = true;
    this.visible = false;
  }
}
