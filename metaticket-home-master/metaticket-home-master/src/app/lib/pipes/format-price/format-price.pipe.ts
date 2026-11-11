import { Pipe, PipeTransform } from '@angular/core';
import { formatPriceVnd } from 'src/app/lib/utils';

@Pipe({
  name: 'formatPrice',
  standalone: true,
})
export class FormatPricePipe implements PipeTransform {
  transform(value: number): string {
    return formatPriceVnd(value);
  }
}
