import { formatDate } from '@angular/common';

export function formatDateTimeVN(date: Date | number | string, showSeconds: boolean = true): string {
    return formatDate(date, 'HH:mm dd/MM/yyyy', 'en_US');
}
