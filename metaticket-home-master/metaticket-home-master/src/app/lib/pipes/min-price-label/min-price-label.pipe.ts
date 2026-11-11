import { Pipe, PipeTransform } from '@angular/core';
import { minPriceLabel } from 'src/app/lib/utils';

/** Template-side "Từ …₫" card label; empty string when the show has no price to advertise. */
@Pipe({
  name: 'minPriceLabel',
  standalone: true,
})
export class MinPriceLabelPipe implements PipeTransform {
  transform(minPrice?: number | null): string {
    return minPriceLabel(minPrice);
  }
}
