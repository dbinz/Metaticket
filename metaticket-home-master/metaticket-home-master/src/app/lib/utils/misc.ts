import { HttpErrorResponse } from '@angular/common/http';

// eslint-disable-next-line @typescript-eslint/naming-convention
export function truncateToNearestXMinutes(ms: number, xMinutes: number): number {
  const msPerMinute = 60000;
  return ms - (ms % (xMinutes * msPerMinute));
}

const HTML_ESCAPES = new Map<string, string>([
  ['&', '&amp;'],
  ['<', '&lt;'],
  ['>', '&gt;'],
  ['"', '&quot;'],
  ["'", '&#39;'],
]);

/**
 * Makes server text safe to hand to ng-zorro notifications, which render their content with `innerHTML`: the text
 * then shows literally instead of being parsed as markup.
 */
export function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (char) => HTML_ESCAPES.get(char) ?? char);
}

/**
 * Customer-facing text of a refused request: a plain-text 4xx body or the string `error` field of a JSON body.
 * Returns '' when there is no displayable message. Includes HTTP 2xx rejected by the API response parser (legacy errors).
 * The text is raw: escape it before handing it to a notification.
 * Accepts the body as a string (`responseType: 'text'`) or as `{ error, text }` (JSON parse failure that kept it).
 */
export function serverErrorText(err: HttpErrorResponse): string {
  if (!((err.status >= 400 && err.status < 500) || (err.status >= 200 && err.status < 300))) {
    return '';
  }
  const body: unknown = err.error;
  let rawText = '';
  if (typeof body === 'string') {
    rawText = body;
  } else if (body !== null && typeof body === 'object' && 'text' in body && typeof body.text === 'string') {
    rawText = body.text;
  }
  let text = rawText.trim();
  if (text.startsWith('{') || text.startsWith('[')) {
    try {
      const parsed: unknown = JSON.parse(text);
      text =
        parsed !== null && typeof parsed === 'object' && 'error' in parsed && typeof parsed.error === 'string'
          ? parsed.error.trim()
          : '';
    } catch {
      // Plain-text messages can also start with a bracket.
    }
  }
  return text.startsWith('<') ? '' : text;
}

const RESERVATION_GONE_STATUSES = [400, 403, 404];

/**
 * True when `ticket/reservation/fetch` itself answered that the hold is unusable: bad id (400), another account's
 * (403) or removed (404). Anything else (network drop, request cut by a reload, 429/5xx, or an HTML error page of the
 * gateway/CDN in front of the service) says nothing about the hold, so a saved booking must be kept: dropping it sends
 * the customer back to pick tickets while their own hold still locks the seats until it expires.
 */
export function isReservationGoneError(err: unknown): boolean {
  if (!(err instanceof HttpErrorResponse) || !RESERVATION_GONE_STATUSES.includes(err.status)) {
    return false;
  }
  return !(err.headers.get('content-type') ?? '').includes('text/html');
}
