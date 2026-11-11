import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient, withFetch } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { UserService } from '../user-service/user.service';
import { TicketSlotsService } from '../ticket-slots-service/ticket-slots.service';
import { PaymentService } from '../payment/payment.service';
import { ShowService } from '../show/show.service';

import { TicketService } from './ticket.service';

describe('Storefront API responses through FetchBackend', () => {
  let service: TicketService;
  let fetchSpy: jasmine.Spy<typeof fetch>;

  beforeEach(() => {
    // FetchBackend captures fetch at construction: stub it before any service is injected.
    fetchSpy = spyOn(window, 'fetch');
    TestBed.configureTestingModule({ providers: [provideHttpClient(withFetch())] });
    service = TestBed.inject(TicketService);
  });

  function respond(body: string, status = 200): void {
    fetchSpy.and.callFake(() => Promise.resolve(new Response(body, { status })));
  }

  async function expectError(request: Observable<unknown>, status: number, message: string): Promise<void> {
    try {
      await firstValueFrom(request);
      fail('The API refusal must not be treated as success');
    } catch (error) {
      expect(error instanceof HttpErrorResponse).toBeTrue();
      const httpError = error as HttpErrorResponse;
      expect(httpError.status).toBe(status);
      expect(httpError.error).toBe(message);
    }
  }

  it('preserves the plain-text refusal when reserving seats', async () => {
    respond('Không đủ vé', 409);
    await expectError(service.reserveReservation('hold-1'), 409, 'Không đủ vé');
  });

  it('preserves plain-text errors of ticket polling, show fetch and payment status', async () => {
    respond('Không tìm thấy', 404);
    await expectError(service.getAllTicket({ showId: 'show-1', sessionId: 'session-1' }), 404, 'Không tìm thấy');
    await expectError(TestBed.inject(ShowService).getShowDetail('show-1'), 404, 'Không tìm thấy');
    await expectError(TestBed.inject(PaymentService).getPaymentStatus('pay-1'), 404, 'Không tìm thấy');
  });

  it('keeps the status used to detect an expired reservation', async () => {
    respond('Không tìm thấy reservation', 404);
    await expectError(service.getSingleReservation('hold-1'), 404, 'Không tìm thấy reservation');
  });

  it('parses the successful reservation as before', async () => {
    respond('{"reservationId":"hold-1"}', 201);
    expect(
      await firstValueFrom(service.createReservation({ showId: 'show-1', sessionId: 'session-1', details: [] })),
    ).toEqual({ reservationId: 'hold-1' });
  });

  it('rejects a legacy 200 ErrorCommon instead of continuing with an undefined reservation id', async () => {
    respond('{"code":"NOT_OPEN","message":"Chưa mở bán"}');
    await expectError(
      service.createReservation({ showId: 'show-1', sessionId: 'session-1', details: [] }),
      200,
      'Chưa mở bán',
    );
  });

  it('rejects an empty reservation response instead of continuing with an undefined id', async () => {
    respond('');
    await expectError(
      service.createReservation({ showId: 'show-1', sessionId: 'session-1', details: [] }),
      200,
      'Phản hồi từ máy chủ không hợp lệ. Vui lòng thử lại.',
    );
  });

  it('accepts the successful plain-text resend-OTP message', async () => {
    respond('Mã OTP mới đã được gửi tới email.');
    expect(await firstValueFrom(TestBed.inject(UserService).resendOtpRegisterUser())).toBeUndefined();
  });

  it('accepts an empty successful OTP response', async () => {
    respond('');
    expect(
      await firstValueFrom(TestBed.inject(TicketSlotsService).verifyOtpTransferTicketSlot('slot-1', '123456')),
    ).toBeUndefined();
  });

  it('preserves the OTP status and text needed to keep the verification form open', async () => {
    respond('Mã OTP không chính xác. Bạn còn 2 lần thử.', 410);
    await expectError(
      TestBed.inject(UserService).verifyOtpResetPassword('0900000000', '123456'),
      410,
      'Mã OTP không chính xác. Bạn còn 2 lần thử.',
    );
  });

  it('rejects legacy ErrorCommon from a void endpoint', async () => {
    respond('{"code":"OTP_INVALID","message":"OTP không đúng"}');
    await expectError(
      TestBed.inject(TicketSlotsService).verifyOtpTransferTicketSlot('slot-1', '123456'),
      200,
      'OTP không đúng',
    );
  });

  it('keeps legitimate payment URLs as text', async () => {
    respond('https://metaticket.com.vn/payment');
    expect(await firstValueFrom(TestBed.inject(PaymentService).startPayment({} as never))).toBe(
      'https://metaticket.com.vn/payment',
    );
  });

  it('does not confuse a payment-result code with the string code of ErrorCommon', async () => {
    respond('{"code":1,"message":"Thanh toán thất bại"}');
    expect(await firstValueFrom(TestBed.inject(PaymentService).updatePaymentResultFromReturnUrl('result'))).toEqual({
      code: 1,
      message: 'Thanh toán thất bại',
    });
  });

  it('preserves the headers and status of a gateway failure', async () => {
    fetchSpy.and.callFake(() =>
      Promise.resolve(
        new Response('<html>Unavailable</html>', {
          status: 503,
          headers: [['Content-Type', 'text/html']],
        }),
      ),
    );
    try {
      await firstValueFrom(service.getSingleReservation('hold-1'));
      fail('Expected gateway error');
    } catch (error) {
      const httpError = error as HttpErrorResponse;
      expect(httpError.status).toBe(503);
      expect(httpError.headers.get('content-type')).toBe('text/html');
      expect(httpError.error).toBe('<html>Unavailable</html>');
    }
  });

  it('keeps legacy HTTP 200 text refusals out of the reservation success flow', async () => {
    respond('Chưa mở bán');
    await expectError(
      service.createReservation({ showId: 'show-1', sessionId: 'session-1', details: [] }),
      200,
      'Chưa mở bán',
    );
  });
});
