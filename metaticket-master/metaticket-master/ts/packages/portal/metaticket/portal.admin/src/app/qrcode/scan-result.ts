import { HttpErrorResponse } from '@angular/common/http';
import { TimeoutError } from 'rxjs';

/**
 * How a scan ended, which drives the popup colour: `valid` let the guest in, `used` already checked in, `rejected` the
 * ticket is refused (wrong event, not paid, wrong date...), `system` the server could not answer, scan again later.
 */
export type ScanOutcome = 'valid' | 'used' | 'rejected' | 'system';

export interface ScanFailure {
    outcome: Exclude<ScanOutcome, 'valid'>;
    title: string;
    message: string;
    /** HTTP status shown in small print for support; 0 = no response at all. */
    status: number;
}

const MAX_MESSAGE_LENGTH = 300;

/** Fallback text per status when the body carries nothing readable (empty, HTML page, generic Ktor text). */
const STATUS_MESSAGES: Record<number, string> = {
    400: 'Mã QR không hợp lệ.',
    401: 'Phiên đăng nhập không hợp lệ, vui lòng mở lại link quét vé.',
    403: 'Không có quyền quét mã cho sự kiện này.',
    404: 'Mã QR không tồn tại.',
    409: 'Mã QR đã được quét! Vui lòng quét mã khác.',
    412: 'Vé không đủ điều kiện vào cửa.',
    429: 'Quét quá nhanh, vui lòng thử lại sau vài giây.',
};

/** Turns any failed scan (HTTP error, timeout, network loss) into a short Vietnamese explanation for gate staff. */
export function describeScanFailure(error: unknown): ScanFailure {
    if (error instanceof TimeoutError) {
        return {
            outcome: 'system',
            title: 'Máy chủ không phản hồi',
            message: 'Quá thời gian chờ kết quả. Kiểm tra mạng rồi quét lại vé này.',
            status: 0,
        };
    }
    if (!(error instanceof HttpErrorResponse) || error.status === 0) {
        return {
            outcome: 'system',
            title: 'Mất kết nối',
            message: 'Không kết nối được máy chủ. Kiểm tra mạng rồi quét lại vé này.',
            status: 0,
        };
    }

    const status = error.status;
    if (status >= 500) {
        return {
            outcome: 'system',
            title: status === 500 ? 'Lỗi hệ thống' : 'Máy chủ đang bận',
            message:
                readableBody(error.error) ??
                'Hệ thống đang gặp sự cố, vui lòng quét lại sau ít phút hoặc liên hệ hỗ trợ.',
            status,
        };
    }

    const message = readableBody(error.error) ?? STATUS_MESSAGES[status] ?? `Không quét được vé (mã lỗi ${status}).`;
    // Backends older than this change answer "already scanned" with 412, so the text is checked too.
    if (status === 409 || /đã được quét/i.test(message)) {
        return { outcome: 'used', title: 'Vé đã được quét', message, status };
    }
    return { outcome: 'rejected', title: 'Vé không hợp lệ', message, status };
}

/**
 * The server message when it is meant for people: plain text or a JSON `message`. HTML error pages (nginx 502/504),
 * Ktor's generic "500: InternalServerError" and gRPC code prefixes are dropped or stripped.
 */
function readableBody(body: unknown): string | undefined {
    let text: string | undefined;
    if (typeof body === 'string') text = body;
    else if (body && typeof body === 'object') {
        const candidate =
            (body as { message?: unknown; error?: unknown }).message ?? (body as { error?: unknown }).error;
        if (typeof candidate === 'string') text = candidate;
    }
    if (!text) return undefined;

    text = text.trim();
    if (!text || /<\s*(!doctype|html|head|body|center|h1)\b/i.test(text)) return undefined;
    if (/^\d{3}:\s*\w+$/.test(text)) return undefined; // "500: InternalServerError"
    text = text.replace(/^[A-Z_]{3,}:\s*/, ''); // "FAILED_PRECONDITION: ..." from gRPC
    return text.length > MAX_MESSAGE_LENGTH ? `${text.slice(0, MAX_MESSAGE_LENGTH)}…` : text;
}
