import { routeName } from 'src/app/lib/constants';

/** An accessory show reference that may carry a canonical slug. */
export interface ProductLinkRef {
  id: string;
  slug?: string;
}

/** Path to an accessory show's detail page (`/san-pham/{slug|id}`), mirroring `showDetailPath`. */
export function productDetailPath(show: ProductLinkRef): string {
  const key = show.slug?.trim() || show.id;
  return `/${routeName.productDetail}/${key}`;
}
