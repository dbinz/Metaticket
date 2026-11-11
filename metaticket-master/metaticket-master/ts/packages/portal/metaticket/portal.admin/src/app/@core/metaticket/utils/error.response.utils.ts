import { HttpErrorResponse } from '@angular/common/http';

export function getErrorMessageFromResponse(error: HttpErrorResponse): string {
    console.log('response error:', error);
    let errorMessage = 'Unknown error';

    if (typeof error.error === 'string' && error.error) {
        // Ưu tiên 1: Lỗi trả về là một chuỗi không rỗng
        errorMessage = error.error;
    } else if (error.error?.message) {
        // Ưu tiên 2: Lỗi trả về là JSON object có thuộc tính 'message'
        errorMessage = error.error.message;
    } else if (error.message) {
        // Ưu tiên 3: Lỗi chung từ HTTP response hoặc lỗi phía client
        errorMessage = error.message;
    }
    return errorMessage;
}
