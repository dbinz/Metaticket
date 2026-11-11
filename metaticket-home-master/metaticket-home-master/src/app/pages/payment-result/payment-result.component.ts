import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { PaymentService } from 'src/app/lib/services';
import { PaymentRedirectResponse } from 'src/app/lib/entities';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import { BookingService } from 'src/app/lib/stores';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-payment-result',
  templateUrl: './payment-result.component.html',
  standalone: true,
  imports: [CommonModule],
})
export class PaymentResultComponent implements OnInit, OnDestroy {
  message: string = 'Đang xử lý kết quả thanh toán, vui lòng chờ...';
  status: 'processing' | 'success' | 'failed' | 'canceled' | 'error' = 'processing';
  countdown: number = 5; // Biến để lưu thời gian đếm ngược

  private destroy$ = new Subject<void>();
  private timerInterval: number | undefined; // Biến để giữ ID của interval

  constructor(
    private paymentService: PaymentService,
    private router: Router,
    private bookingService: BookingService,
  ) {
    console.log('PaymentResultComponent CONSTRUCTOR LOADED!');
  }

  ngOnInit(): void {
    console.log('PaymentResultComponent ngOnInit RUNNING with URL:', this.router.url);

    const fullPath = this.router.url;
    const routePrefix = `/payment-result/`;

    if (fullPath.startsWith(routePrefix)) {
      const data = fullPath.substring(routePrefix.length);
      this.updatePaymentResult(data);
    } else {
      this.status = 'error';
      this.message = 'Không thể xử lý kết quả thanh toán. Vui lòng liên hệ bộ phận hỗ trợ.';
      // Xóa phiên booking khi có lỗi xử lý URL
      this.bookingService.expireBookingSession();
      this.scheduleClose();
    }
  }

  private updatePaymentResult(data: string): void {
    this.paymentService
      .updatePaymentResultFromReturnUrl(data)
      .pipe(
        takeUntil(this.destroy$),
        catchError((error: any) => {
          if (error instanceof HttpErrorResponse) {
            const response: PaymentRedirectResponse = error.error as PaymentRedirectResponse;
            if (response.code === 0) {
              this.status = 'success';
              this.message = response.message || 'Thanh toán thành công!';
              // Xóa phiên booking khi có lỗi
              this.bookingService.expireBookingSession();
            } else if (response.code === 1) {
              this.status = 'failed';
              this.message = response.message || 'Thanh toán thất bại, vui lòng thử lại.';
              // Xóa phiên booking khi có lỗi
              this.bookingService.expireBookingSession();
            } else if (response.code === 3) {
              this.status = 'processing';
              this.message = response.message || 'Đang trong quá trình xử lý kết quả thanh toán.';
            } else {
              // Mặc định là trạng thái đang xử lý nếu có mã code khác
              this.status = 'processing';
              this.message = response.message || 'Đang trong quá trình xử lý kết quả thanh toán.';
              console.error('Lỗi khi cập nhật kết quả thanh toán:', error);
              this.status = 'error';
              this.message =
                'Đã có lỗi xảy ra trong quá trình cập nhật kêt quả thanh toán. Vui lòng liên hệ bộ phận hỗ trợ.';
            }
          } else {
            // Mặc định là trạng thái đang xử lý nếu có mã code khác
            this.status = 'processing';
            this.message = 'Đang trong quá trình xử lý kết quả thanh toán.';
            console.error('Lỗi khi cập nhật kết quả thanh toán:', error);
            this.status = 'error';
            this.message =
              'Đã có lỗi xảy ra trong quá trình cập nhật kêt quả thanh toán. Vui lòng liên hệ bộ phận hỗ trợ.';
          }
          return of(null);
        }),
        finalize(() => {
          // Luôn bắt đầu đếm ngược sau khi API call kết thúc
          this.scheduleClose();
        }),
      )
      .subscribe((response: PaymentRedirectResponse | null) => {
        if (response) {
          if (response.code === 0) {
            this.status = 'success';
            this.message = response.message || 'Thanh toán thành công!';
            // Xóa phiên booking khi thanh toán thành công
            this.bookingService.bookingCompleted();
          } else if (response.code === 1) {
            this.status = 'failed';
            this.message = response.message || 'Thanh toán thất bại, vui lòng thử lại.';
            // Xóa phiên booking khi thanh toán thất bại
            this.bookingService.expireBookingSession();
          } else {
            // Mặc định là trạng thái đang xử lý nếu có mã code khác
            this.status = 'processing';
            this.message = response.message || 'Đang trong quá trình xử lý kết quả thanh toán.';
          }
        }
      });
  }

  private scheduleClose(): void {
    // Bắt đầu đếm ngược mỗi giây
    this.timerInterval = window.setInterval(() => {
      this.countdown--;
      console.log(this.countdown);
      if (this.countdown <= 0) {
        clearInterval(this.timerInterval);
        this.leaveResultPage();
      }
    }, 1000);
  }

  /**
   * Phương thức để người dùng chủ động đóng tab
   */
  public closeTabNow(): void {
    this.leaveResultPage();
  }

  /**
   * Tab do cổng thanh toán mở thì đóng lại; còn khi khách thanh toán ngay trên tab hiện tại
   * (fallback lúc popup bị chặn) thì `window.close()` không có tác dụng, phải điều hướng về trang chủ.
   */
  private leaveResultPage(): void {
    if (window.opener) {
      window.close();
      return;
    }
    void this.router.navigate(['/']);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    // Dọn dẹp interval để tránh rò rỉ bộ nhớ
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
  }
}
