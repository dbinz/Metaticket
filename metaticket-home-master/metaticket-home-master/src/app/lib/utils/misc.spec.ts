import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { isReservationGoneError, serverErrorText } from './misc';

describe('serverErrorText', () => {
  const fail = (status: number, error: unknown): HttpErrorResponse => new HttpErrorResponse({ status, error });

  it('reads a plain-text 4xx body', () => {
    expect(serverErrorText(fail(400, '  Mã đã hết hạn \n'))).toBe('Mã đã hết hạn');
  });

  it('reads the message from JSON error bodies returned as text', () => {
    expect(serverErrorText(fail(400, '{"error":"  Mã đã hết hạn  "}'))).toBe('Mã đã hết hạn');
    expect(serverErrorText(fail(401, '{"error":"Phiên đã hết hạn"}'))).toBe('Phiên đã hết hạn');
  });

  it('falls back when a structured error has no displayable message', () => {
    expect(serverErrorText(fail(400, '{"error":{"detail":"internal"}}'))).toBe('');
    expect(serverErrorText(fail(400, '{"error":"<html>Gateway</html>"}'))).toBe('');
    expect(serverErrorText(fail(400, '["internal"]'))).toBe('');
  });

  it('reads a legacy HTTP 200 refusal thrown by the response parser', () => {
    expect(serverErrorText(fail(200, 'Chưa mở bán'))).toBe('Chưa mở bán');
    expect(serverErrorText(fail(200, '<html>Gateway</html>'))).toBe('');
  });

  it('reads the text kept next to a JSON parse error', () => {
    const error = { error: new SyntaxError('Unexpected token'), text: 'Mã đã hết hạn' };

    expect(serverErrorText(fail(400, error))).toBe('Mã đã hết hạn');
  });

  it('has no text for a bare JSON parse error (body already lost)', () => {
    expect(serverErrorText(fail(400, new SyntaxError('Unexpected token')))).toBe('');
  });

  it('ignores JSON bodies, null and non-string text', () => {
    expect(serverErrorText(fail(400, { code: 'X', message: 'not a plain-text body' }))).toBe('');
    expect(serverErrorText(fail(400, null))).toBe('');
    expect(serverErrorText(fail(400, { text: 42 }))).toBe('');
  });

  it('ignores non-4xx responses (gateway text such as "Gateway Timeout")', () => {
    expect(serverErrorText(fail(504, 'Gateway Timeout'))).toBe('');
    expect(serverErrorText(fail(0, 'offline'))).toBe('');
    expect(serverErrorText(fail(399, 'redirect'))).toBe('');
    expect(serverErrorText(fail(500, 'Internal'))).toBe('');
  });

  it('keeps the 4xx edges', () => {
    expect(serverErrorText(fail(400, 'a'))).toBe('a');
    expect(serverErrorText(fail(499, 'b'))).toBe('b');
  });

  it('ignores HTML error pages, in either shape', () => {
    expect(serverErrorText(fail(400, '  <html><body>400 Bad Request</body></html>'))).toBe('');
    expect(serverErrorText(fail(413, { error: new SyntaxError('x'), text: '<html>413</html>' }))).toBe('');
  });

  it('returns the text raw: escaping is the caller job (notification renders innerHTML)', () => {
    expect(serverErrorText(fail(400, 'Mã <b>A</b> & B'))).toBe('Mã <b>A</b> & B');
  });
});

describe('isReservationGoneError', () => {
  const fail = (status: number, contentType = 'text/plain; charset=UTF-8'): HttpErrorResponse =>
    new HttpErrorResponse({ status, headers: new HttpHeaders().set('content-type', contentType) });

  it('is true when ticket/reservation/fetch says the hold is unusable (400 bad id, 403 not ours, 404 gone)', () => {
    expect(isReservationGoneError(fail(400))).toBeTrue();
    expect(isReservationGoneError(fail(403))).toBeTrue();
    expect(isReservationGoneError(fail(404))).toBeTrue();
  });

  it('is false for failures that say nothing about the hold (cut request, rate limit, 5xx)', () => {
    expect(isReservationGoneError(new HttpErrorResponse({ status: 0 }))).toBeFalse();
    expect(isReservationGoneError(fail(401))).toBeFalse();
    expect(isReservationGoneError(fail(429))).toBeFalse();
    expect(isReservationGoneError(fail(500))).toBeFalse();
    expect(isReservationGoneError(fail(502, 'text/html'))).toBeFalse();
    expect(isReservationGoneError(fail(503))).toBeFalse();
  });

  it('is false for an HTML error page of the gateway/CDN even with a 403/404 status', () => {
    expect(isReservationGoneError(fail(403, 'text/html; charset=UTF-8'))).toBeFalse();
    expect(isReservationGoneError(fail(404, 'text/html'))).toBeFalse();
  });

  it('is false for errors that are not HTTP responses (timeout, thrown error)', () => {
    expect(isReservationGoneError(new Error('timeout'))).toBeFalse();
    expect(isReservationGoneError(undefined)).toBeFalse();
  });
});
