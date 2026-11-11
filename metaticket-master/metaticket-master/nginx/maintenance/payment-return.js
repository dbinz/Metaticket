// The gateway return page must still deliver its signed callback while Angular is unavailable.
async function forwardPaymentReturn() {
  const prefix = '/payment-result/';
  if (!/^\/payment-result\/(onepay|vnpay|ninepay)\/[^/]+\/[^/]+\/[^/]+$/.test(window.location.pathname)) {
    return;
  }
  const status = document.getElementById('payment-return-status');
  status.hidden = false;
  status.textContent = 'Đang gửi phản hồi thanh toán đến hệ thống…';
  // Preserve the signed query byte-for-byte; never decode/re-encode payment parameters.
  const callback = '/api/payment/return/result/' + window.location.pathname.slice(prefix.length) + window.location.search;
  const controller = new AbortController();
  const timerId = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(callback, { cache: 'no-store', credentials: 'same-origin', signal: controller.signal });
    // service.payment answers failed/pending results with 4xx + a RedirectResponse body; only that body proves delivery.
    const body = await response.json().catch(() => null);
    if (![0, 1, 3].includes(body?.code)) {
      throw new Error('Payment return unavailable');
    }
    status.textContent = 'Đã gửi phản hồi thanh toán đến hệ thống. Vui lòng kiểm tra đơn hàng sau bảo trì.';
  } catch {
    status.textContent = 'Chưa xác nhận được việc xử lý phản hồi thanh toán. Vui lòng giữ trang này và thử tải lại sau.';
  } finally {
    clearTimeout(timerId);
  }
}

void forwardPaymentReturn();
